import gc
import os
import threading
import time
from datetime import datetime
from pathlib import Path

from flask import Flask, Response, jsonify, request, send_file
from PIL import Image

from tapir_bridge import status as tapir_status
from window_capture import capture_archicad

APP_DIR = Path(__file__).resolve().parent
DATA_DIR = APP_DIR / "data"
DATA_DIR.mkdir(exist_ok=True)
CAPTURE = DATA_DIR / "current.png"
RESULT = DATA_DIR / "result.png"
RESULTS_DIR = DATA_DIR / "results"
RESULTS_DIR.mkdir(exist_ok=True)

HOST = "127.0.0.1"
PORT = 8100
MODEL_ID = os.environ.get("SD15_MODEL_ID", "stable-diffusion-v1-5/stable-diffusion-v1-5")
CONTROLNET_ID = os.environ.get("CONTROLNET_ID", "lllyasviel/control_v11p_sd15_canny")

app = Flask(__name__)
pipe = None
pipe_lock = threading.Lock()
state_lock = threading.Lock()
state = {"active": False, "progress": 0, "message": "Готово", "device": "", "model_ready": False}


def set_state(**kwargs):
    with state_lock:
        state.update(kwargs)


def get_state():
    with state_lock:
        return dict(state)


def clear_mem():
    gc.collect()
    try:
        import torch
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
    except Exception:
        pass


def load_pipe():
    global pipe
    if pipe is not None:
        return pipe
    with pipe_lock:
        if pipe is not None:
            return pipe
        import torch
        from diffusers import ControlNetModel, StableDiffusionControlNetImg2ImgPipeline, DPMSolverMultistepScheduler

        device = "cuda" if torch.cuda.is_available() else "cpu"
        dtype = torch.float16 if device == "cuda" else torch.float32
        set_state(message="Загрузка локальной модели…", progress=2, device=device)
        controlnet = ControlNetModel.from_pretrained(CONTROLNET_ID, torch_dtype=dtype)
        p = StableDiffusionControlNetImg2ImgPipeline.from_pretrained(
            MODEL_ID,
            controlnet=controlnet,
            torch_dtype=dtype,
            safety_checker=None,
            requires_safety_checker=False,
        )
        p.scheduler = DPMSolverMultistepScheduler.from_config(p.scheduler.config)
        for fn in ("enable_attention_slicing", "enable_vae_slicing", "enable_vae_tiling"):
            try:
                getattr(p, fn)()
            except Exception:
                pass
        if device == "cuda":
            try:
                p.enable_model_cpu_offload(gpu_id=0)
            except Exception:
                p = p.to("cuda")
        else:
            p = p.to("cpu")
        pipe = p
        clear_mem()
        set_state(message=f"Модель готова ({device})", progress=100, device=device, model_ready=True)
        return pipe


def make_canny(img, low=100, high=200):
    import cv2
    import numpy as np
    arr = np.array(img.convert("RGB"))
    edges = cv2.Canny(arr, low, high)
    edges = np.stack([edges, edges, edges], axis=2)
    return Image.fromarray(edges)


def fit_size(w, h):
    max_pixels = 1024 * 768
    if w * h > max_pixels:
        scale = (max_pixels / (w * h)) ** 0.5
        w, h = int(w * scale), int(h * scale)
    w = max(512, (w // 64) * 64)
    h = max(512, (h // 64) * 64)
    return w, h


@app.get("/api/status")
def api_status():
    s = tapir_status()
    return jsonify(ok=True, bridge=get_state(), connection=s)


@app.post("/api/capture")
def api_capture():
    data = request.get_json(silent=True) or {}
    viewport_only = bool(data.get("viewport_only", True))
    try:
        info = capture_archicad(CAPTURE, viewport_only=viewport_only)
        return jsonify(ok=True, info=info, src="/capture.png?ts=" + str(int(time.time())))
    except Exception as exc:
        return jsonify(ok=False, error=str(exc)), 500


@app.get("/capture.png")
def capture_png():
    if not CAPTURE.exists():
        return Response(status=404)
    return send_file(CAPTURE, mimetype="image/png", max_age=0)


@app.get("/result.png")
def result_png():
    if not RESULT.exists():
        return Response(status=404)
    return send_file(RESULT, mimetype="image/png", max_age=0)


@app.post("/api/open-results")
def open_results():
    if os.name == "nt":
        os.startfile(str(RESULTS_DIR))
        return jsonify(ok=True)
    return jsonify(ok=False, error="Windows only"), 400


@app.post("/api/render")
def api_render():
    if not CAPTURE.exists():
        return jsonify(ok=False, error="Сначала нажмите «Снять текущий вид Archicad»"), 400

    data = request.get_json(force=True) or {}
    prompt = data.get("prompt") or "photorealistic architectural visualization, realistic materials, natural light"
    negative = data.get("negative_prompt") or "low quality, blurry, distorted geometry, warped windows, text, watermark"
    width, height = fit_size(int(data.get("width", 1024)), int(data.get("height", 768)))
    steps = max(10, min(50, int(data.get("steps", 28))))
    cfg = max(1.0, min(15.0, float(data.get("cfg_scale", 7.0))))
    strength = max(0.15, min(0.85, float(data.get("strength", 0.48))))
    control = max(0.1, min(1.5, float(data.get("control", 0.95))))

    init = Image.open(CAPTURE).convert("RGB").resize((width, height), Image.LANCZOS)
    canny = make_canny(init)
    set_state(active=True, progress=1, message="Подготовка…")

    try:
        p = load_pipe()

        def modern_cb(_pipe, step, _timestep, callback_kwargs):
            pct = min(98, max(2, int((step + 1) / steps * 96)))
            set_state(active=True, progress=pct, message=f"Генерация • шаг {step + 1}/{steps}")
            return callback_kwargs

        common = dict(
            prompt=prompt,
            negative_prompt=negative,
            image=init,
            control_image=canny,
            strength=strength,
            guidance_scale=cfg,
            num_inference_steps=steps,
            controlnet_conditioning_scale=control,
        )
        try:
            out = p(**common, callback_on_step_end=modern_cb).images[0]
        except TypeError:
            def legacy_cb(step, _timestep, _latents):
                pct = min(98, max(2, int((step + 1) / steps * 96)))
                set_state(active=True, progress=pct, message=f"Генерация • шаг {step + 1}/{steps}")
            out = p(**common, callback=legacy_cb, callback_steps=1).images[0]

        out.save(RESULT)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        saved = RESULTS_DIR / f"archicad_ai_{stamp}.png"
        out.save(saved)
        set_state(active=False, progress=100, message="Готово", model_ready=True)
        clear_mem()
        return jsonify(ok=True, result="/result.png?ts=" + str(int(time.time())), saved=str(saved))
    except Exception as exc:
        clear_mem()
        msg = str(exc)
        if "out of memory" in msg.lower():
            msg += " | Попробуйте 768×768 или 896×640 и 20–24 шага."
        set_state(active=False, progress=0, message="Ошибка: " + msg)
        return jsonify(ok=False, error=msg), 500


UI = r'''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tapir AI Visualizer</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#101211;color:#eef0ee;font:14px/1.45 Inter,Segoe UI,system-ui,sans-serif}.app{display:grid;grid-template-columns:370px 1fr;min-height:100vh}.side{background:#1b1e1c;border-right:1px solid #343834;padding:18px;overflow:auto}.main{padding:18px;min-width:0}.brand{font-size:22px;font-weight:800;margin:0 0 4px}.sub{color:#9da59e;margin-bottom:16px}.card{background:#242825;border:1px solid #394039;border-radius:14px;padding:14px;margin-bottom:14px}.row{display:flex;gap:8px;align-items:center}.dot{width:9px;height:9px;border-radius:50%;background:#777}.dot.ok{background:#75b579}.dot.bad{background:#d96d6d}.status{font-size:13px;color:#b9c0ba}.btn{width:100%;border:0;border-radius:10px;padding:11px 12px;background:#738d75;color:#fff;font-weight:750;cursor:pointer}.btn.secondary{background:#373d38}.btn:disabled{opacity:.45;cursor:not-allowed}label{display:block;margin:10px 0 5px;color:#b9c0ba}textarea,input,select{width:100%;background:#111412;color:#f4f4f4;border:1px solid #4b544c;border-radius:9px;padding:9px}.twocol{display:grid;grid-template-columns:1fr 1fr;gap:8px}.images{display:grid;grid-template-columns:1fr 1fr;gap:14px}.pane{background:#1a1d1b;border:1px solid #343934;border-radius:14px;padding:12px;min-width:0}.pane h3{font-size:14px;margin:0 0 9px;color:#bfc6c0}.pane img{width:100%;max-height:75vh;object-fit:contain;background:#090a09;border-radius:9px;display:block}.bar{height:10px;background:#111;border-radius:99px;overflow:hidden;margin:10px 0 6px}.fill{height:100%;width:0;background:#7e9c80;transition:.25s}.presets{display:grid;grid-template-columns:1fr 1fr;gap:6px}.preset{border:1px solid #4b544c;background:#2f3430;color:#ddd;border-radius:8px;padding:8px;cursor:pointer}.tiny{font-size:12px;color:#8f9790}@media(max-width:1000px){.app{grid-template-columns:1fr}.images{grid-template-columns:1fr}.side{border-right:0;border-bottom:1px solid #343834}}
</style></head><body>
<div class="app"><aside class="side"><div class="brand">Tapir AI Visualizer</div><div class="sub">ArchiCAD 25 → Tapir → локальный ControlNet</div>
<div class="card"><div class="row"><span id="acDot" class="dot"></span><div id="acStatus" class="status">Проверка Archicad…</div></div><div class="row" style="margin-top:7px"><span id="tapirDot" class="dot"></span><div id="tapirStatus" class="status">Проверка Tapir…</div></div><button class="btn secondary" style="margin-top:10px" onclick="refreshStatus()">Проверить соединение</button></div>
<div class="card"><label><input id="viewport" type="checkbox" checked style="width:auto;margin-right:7px"> Автоматически искать область вида</label><button class="btn" onclick="captureView()">Снять текущий вид Archicad</button><div class="tiny" style="margin-top:7px">Перед захватом открой нужный 3D-вид. Программа сама временно выведет Archicad на передний план.</div></div>
<div class="card"><div class="presets"><button class="preset" onclick="preset('facade')">Фасад</button><button class="preset" onclick="preset('dusk')">Фасад • закат</button><button class="preset" onclick="preset('interior')">Интерьер</button><button class="preset" onclick="preset('landscape')">Генплан</button></div><label>Prompt</label><textarea id="prompt" rows="5">photorealistic architectural visualization, contemporary premium architecture, realistic facade materials, natural daylight, professional architectural photography, detailed landscaping</textarea><label>Negative prompt</label><textarea id="neg" rows="3">low quality, blurry, distorted geometry, warped windows, extra floors, text, watermark, sketch, CAD UI, toolbar</textarea><div class="twocol"><div><label>Размер</label><select id="size"><option>1024x768</option><option>896x640</option><option>768x768</option></select></div><div><label>Шаги</label><input id="steps" type="number" min="10" max="50" value="28"></div></div><div class="twocol"><div><label>CFG</label><input id="cfg" type="number" min="1" max="15" step="0.5" value="7"></div><div><label>Strength</label><input id="strength" type="number" min="0.15" max="0.85" step="0.02" value="0.48"></div></div><label>ControlNet: <span id="controlVal">0.95</span></label><input id="control" type="range" min="0.2" max="1.4" step="0.05" value="0.95" oninput="controlVal.textContent=this.value"><button id="renderBtn" class="btn" style="margin-top:12px" onclick="renderAI()">Сгенерировать</button><div class="bar"><div id="fill" class="fill"></div></div><div id="genStatus" class="tiny">Готово</div><button class="btn secondary" style="margin-top:10px" onclick="openResults()">Открыть папку результатов</button></div>
</aside><main class="main"><div class="images"><div class="pane"><h3>Текущий вид Archicad</h3><img id="src" alt="Нажмите «Снять текущий вид Archicad»"></div><div class="pane"><h3>AI результат</h3><img id="res" alt="Результат появится здесь"></div></div></main></div>
<script>
const presets={facade:'photorealistic architectural visualization, contemporary premium architecture, realistic facade materials, natural daylight, professional architectural photography, detailed landscaping, preserve exact building geometry',dusk:'photorealistic architectural visualization at blue hour, warm interior lights, premium contemporary facade materials, cinematic but realistic lighting, professional architectural photography, preserve exact building geometry',interior:'photorealistic premium interior visualization, realistic materials, soft natural light, architectural photography, clean modern details, preserve exact room geometry',landscape:'photorealistic landscape architecture visualization, realistic trees and planting, natural materials, paths and site furniture, aerial architectural photography, preserve exact site geometry'};
function preset(k){prompt.value=presets[k]}
async function refreshStatus(){try{let j=await(await fetch('/api/status')).json();let c=j.connection;acDot.className='dot '+(c.archicad?'ok':'bad');tapirDot.className='dot '+(c.tapir?'ok':'bad');acStatus.textContent=c.archicad?('Archicad подключён'+(c.product?.version?' • '+c.product.version:'')):'Archicad не подключён';tapirStatus.textContent=c.tapir?('Tapir подключён'+(c.tapir_version?.version?' • '+c.tapir_version.version:'')):'Tapir не подключён';let b=j.bridge;fill.style.width=(b.progress||0)+'%';genStatus.textContent=b.message||'';renderBtn.disabled=!!b.active}catch(e){acDot.className='dot bad';tapirDot.className='dot bad';acStatus.textContent='Сервер не отвечает'}}
async function captureView(){genStatus.textContent='Захват Archicad…';let r=await fetch('/api/capture',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({viewport_only:viewport.checked})});let j=await r.json();if(j.ok){src.src=j.src;genStatus.textContent='Вид захвачен'}else{genStatus.textContent='Ошибка: '+j.error}}
async function renderAI(){let [w,h]=size.value.split('x').map(Number);renderBtn.disabled=true;let r=await fetch('/api/render',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({prompt:prompt.value,negative_prompt:neg.value,width:w,height:h,steps:+steps.value,cfg_scale:+cfg.value,strength:+strength.value,control:+control.value})});let j=await r.json();renderBtn.disabled=false;if(j.ok){res.src=j.result}else{genStatus.textContent='Ошибка: '+j.error}}
async function openResults(){await fetch('/api/open-results',{method:'POST'})}
refreshStatus();setInterval(refreshStatus,900);
</script></body></html>'''


@app.get("/")
@app.get("/ui")
def ui():
    return Response(UI, mimetype="text/html")


if __name__ == "__main__":
    print(f"Tapir AI Visualizer: http://{HOST}:{PORT}")
    app.run(host=HOST, port=PORT, threaded=True)

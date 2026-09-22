import os, threading, time, gc
from pathlib import Path
from flask import Flask, request, jsonify, send_file, Response
from PIL import Image

APP_DIR = Path(__file__).resolve().parent
DATA_DIR = APP_DIR / 'data'
DATA_DIR.mkdir(exist_ok=True)
CAPTURE = DATA_DIR / 'current.png'
RESULT = DATA_DIR / 'result.png'
HOST = '127.0.0.1'
PORT = 8099
MODEL_ID = os.environ.get('SD15_MODEL_ID', 'stable-diffusion-v1-5/stable-diffusion-v1-5')
CONTROLNET_ID = os.environ.get('CONTROLNET_ID', 'lllyasviel/control_v11p_sd15_canny')

app = Flask(__name__)
pipe = None
pipe_lock = threading.Lock()
state_lock = threading.Lock()
state = {'active':False,'progress':0,'message':'Готово','ready':False,'device':''}

def set_state(**kw):
    with state_lock: state.update(kw)
def get_state():
    with state_lock: return dict(state)
def clear_mem():
    gc.collect()
    try:
        import torch
        if torch.cuda.is_available(): torch.cuda.empty_cache()
    except Exception: pass

def load_pipe():
    global pipe
    if pipe is not None: return pipe
    with pipe_lock:
        if pipe is not None: return pipe
        import torch
        from diffusers import ControlNetModel, StableDiffusionControlNetImg2ImgPipeline, DPMSolverMultistepScheduler
        device = 'cuda' if torch.cuda.is_available() else 'cpu'
        dtype = torch.float16 if device == 'cuda' else torch.float32
        set_state(message='Загрузка ControlNet и локальной модели…', progress=2, device=device)
        controlnet = ControlNetModel.from_pretrained(CONTROLNET_ID, torch_dtype=dtype)
        p = StableDiffusionControlNetImg2ImgPipeline.from_pretrained(MODEL_ID, controlnet=controlnet, torch_dtype=dtype, safety_checker=None, requires_safety_checker=False)
        p.scheduler = DPMSolverMultistepScheduler.from_config(p.scheduler.config)
        try: p.enable_attention_slicing('max')
        except Exception: pass
        try: p.enable_vae_slicing()
        except Exception: pass
        try: p.enable_vae_tiling()
        except Exception: pass
        if device == 'cuda':
            try: p.enable_model_cpu_offload(gpu_id=0)
            except Exception: p = p.to('cuda')
        else: p = p.to('cpu')
        pipe = p; clear_mem(); set_state(message=f'Модель готова ({device})',progress=100,ready=True,device=device)
        return pipe

def make_canny(img, low=100, high=200):
    import cv2, numpy as np
    arr=np.array(img.convert('RGB')); edges=cv2.Canny(arr,low,high); edges=np.stack([edges,edges,edges],axis=2)
    return Image.fromarray(edges)
def fit_size(w,h):
    max_pixels=1024*768
    if w*h>max_pixels:
        s=(max_pixels/(w*h))**0.5; w=int(w*s); h=int(h*s)
    return max(512,(w//64)*64), max(512,(h//64)*64)

@app.get('/health')
def health(): return jsonify(ok=True,service='Archicad 25 AI Visualizer',**get_state())
@app.post('/capture')
def capture():
    if not request.data: return jsonify(ok=False,error='empty body'),400
    CAPTURE.write_bytes(request.data); return jsonify(ok=True,size=len(request.data))
@app.get('/capture.png')
def capture_png(): return send_file(CAPTURE,mimetype='image/png') if CAPTURE.exists() else Response(status=404)
@app.get('/result.png')
def result_png(): return send_file(RESULT,mimetype='image/png') if RESULT.exists() else Response(status=404)
@app.get('/api/status')
def status(): return jsonify(ok=True,**get_state())

@app.post('/api/render')
def render():
    if not CAPTURE.exists(): return jsonify(ok=False,error='Сначала отправьте вид из Archicad'),400
    data=request.get_json(force=True) or {}
    prompt=data.get('prompt') or 'photorealistic architectural visualization, realistic materials, natural lighting, high detail'
    negative=data.get('negative_prompt') or 'low quality, blurry, distorted geometry, text, watermark'
    width,height=fit_size(int(data.get('width',1024)),int(data.get('height',768)))
    steps=max(10,min(50,int(data.get('steps',28)))); cfg=float(data.get('cfg_scale',7.0)); strength=float(data.get('strength',0.48)); control=float(data.get('control',0.9))
    init=Image.open(CAPTURE).convert('RGB').resize((width,height),Image.LANCZOS); canny=make_canny(init)
    set_state(active=True,progress=1,message='Подготовка…')
    try:
        p=load_pipe()
        def cb(step,timestep,latents): set_state(active=True,progress=min(98,max(2,int((step+1)/steps*96))),message=f'Генерация • шаг {step+1}/{steps}')
        kwargs=dict(prompt=prompt,negative_prompt=negative,image=init,control_image=canny,strength=max(.1,min(.85,strength)),guidance_scale=cfg,num_inference_steps=steps,controlnet_conditioning_scale=max(.1,min(1.5,control)),callback=cb,callback_steps=1)
        out=p(**kwargs).images[0]; out.save(RESULT); set_state(active=False,progress=100,message='Готово',ready=True); clear_mem()
        return jsonify(ok=True,result='/result.png?ts='+str(int(time.time())))
    except Exception as e:
        clear_mem(); set_state(active=False,progress=0,message='Ошибка: '+str(e)); return jsonify(ok=False,error=str(e)),500

UI = r'''<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Archicad AI Visualizer</title><style>body{margin:0;background:#171817;color:#eee;font:14px system-ui}.wrap{display:grid;grid-template-columns:360px 1fr;min-height:100vh}.panel{padding:18px;background:#232523;border-right:1px solid #444}.main{padding:18px}.card{background:#2d302d;border:1px solid #444;border-radius:14px;padding:14px;margin-bottom:14px}label{display:block;color:#bbb;margin:10px 0 5px}textarea,input,select{width:100%;box-sizing:border-box;background:#171817;color:#fff;border:1px solid #555;border-radius:8px;padding:9px}button{width:100%;padding:11px;border:0;border-radius:9px;background:#708b72;color:#fff;font-weight:700;cursor:pointer;margin-top:10px}.imgs{display:grid;grid-template-columns:1fr 1fr;gap:14px}.imgs img{width:100%;background:#111;border-radius:12px;border:1px solid #444}.bar{height:12px;background:#111;border-radius:10px;overflow:hidden}.fill{height:100%;width:0;background:#829c82;transition:.2s}.small{color:#aaa;font-size:12px}@media(max-width:900px){.wrap{grid-template-columns:1fr}.imgs{grid-template-columns:1fr}}</style></head><body><div class="wrap"><div class="panel"><h2>Archicad AI Visualizer</h2><div class="small">Local SD 1.5 + ControlNet Canny</div><div class="card"><label>Prompt</label><textarea id="prompt" rows="5">photorealistic architectural visualization, premium architecture, realistic materials, soft natural light, detailed landscaping, high quality</textarea><label>Negative</label><textarea id="neg" rows="3">low quality, blurry, distorted geometry, warped windows, text, watermark, sketch, CAD drawing</textarea><label>Размер</label><select id="size"><option value="1024x768">1024×768</option><option value="768x768">768×768</option><option value="896x640">896×640</option></select><label>Шаги</label><input id="steps" type="number" value="28" min="10" max="50"><label>CFG</label><input id="cfg" type="number" value="7" step="0.5"><label>Переработка</label><input id="strength" type="range" min="0.2" max="0.8" step="0.02" value="0.48"><label>ControlNet</label><input id="control" type="range" min="0.2" max="1.4" step="0.05" value="0.9"><button onclick="renderAI()">Сгенерировать</button></div><div class="card"><div class="bar"><div class="fill" id="fill"></div></div><div id="status" class="small" style="margin-top:7px">Готово</div></div></div><div class="main"><div class="imgs"><div><h3>Текущий вид Archicad</h3><img id="src" src="/capture.png"></div><div><h3>AI результат</h3><img id="res"></div></div></div></div><script>async function renderAI(){let [w,h]=size.value.split('x').map(Number);status.textContent='Запуск…';let r=await fetch('/api/render',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({prompt:prompt.value,negative_prompt:neg.value,width:w,height:h,steps:+steps.value,cfg_scale:+cfg.value,strength:+strength.value,control:+control.value})});let j=await r.json();if(j.ok){res.src=j.result}else status.textContent='Ошибка: '+j.error}async function poll(){try{let j=await (await fetch('/api/status')).json();fill.style.width=(j.progress||0)+'%';status.textContent=j.message||''}catch(e){}setTimeout(poll,800)}poll();setInterval(()=>{src.src='/capture.png?ts='+Date.now()},5000)</script></body></html>'''
@app.get('/ui')
def ui(): return Response(UI,mimetype='text/html')
if __name__=='__main__':
    print(f'Archicad AI Visualizer: http://{HOST}:{PORT}/ui'); app.run(host=HOST,port=PORT,threaded=True)

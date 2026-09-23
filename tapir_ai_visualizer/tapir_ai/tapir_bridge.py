import json
import urllib.request
import urllib.error

ARCHICAD_HOST = "http://127.0.0.1"
ARCHICAD_PORT = 19723


def _post(payload, timeout=5):
    req = urllib.request.Request(f"{ARCHICAD_HOST}:{ARCHICAD_PORT}")
    req.add_header("Content-Type", "application/json")
    data = json.dumps(payload).encode("utf-8")
    with urllib.request.urlopen(req, data, timeout=timeout) as resp:
        body = json.loads(resp.read())
    if body.get("error"):
        raise RuntimeError(body["error"])
    if not body.get("succeeded", False):
        raise RuntimeError(body.get("error") or "Archicad command failed")
    return body.get("result")


def run_command(command, parameters=None):
    payload = {"command": command}
    if parameters is not None:
        payload["parameters"] = parameters
    return _post(payload)


def run_tapir(command, parameters=None):
    result = run_command("API.ExecuteAddOnCommand", {
        "addOnCommandId": {
            "commandNamespace": "TapirCommand",
            "commandName": command,
        },
        "addOnCommandParameters": parameters or {},
    })
    if result is None:
        return None
    response = result.get("addOnCommandResponse")
    if isinstance(response, dict) and response.get("error"):
        raise RuntimeError(response["error"])
    return response


def status():
    out = {
        "archicad": False,
        "tapir": False,
        "product": None,
        "tapir_version": None,
        "message": "Archicad не найден",
    }
    try:
        product = run_command("API.GetProductInfo")
        out["archicad"] = True
        out["product"] = product
        out["message"] = "Archicad подключён"
    except Exception as exc:
        out["message"] = f"Archicad API недоступен: {exc}"
        return out
    try:
        version = run_tapir("GetAddOnVersion")
        out["tapir"] = True
        out["tapir_version"] = version
        out["message"] = "Archicad + Tapir подключены"
    except Exception as exc:
        out["message"] = f"Archicad подключён, но Tapir не отвечает: {exc}"
    return out

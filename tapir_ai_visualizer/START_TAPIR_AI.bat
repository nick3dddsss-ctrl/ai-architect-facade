@echo off
setlocal
cd /d "%~dp0tapir_ai"
where py >nul 2>nul
if errorlevel 1 (
  set PY=python
) else (
  set PY=py -3.11
)
if not exist .venv\Scripts\python.exe (
  echo [1/3] Creating Python environment...
  %PY% -m venv .venv
  call .venv\Scripts\activate.bat
  python -m pip install --upgrade pip
  echo [2/3] Installing PyTorch CUDA...
  pip install torch torchvision --index-url https://download.pytorch.org/whl/cu126
  echo [3/3] Installing AI Visualizer...
  pip install -r requirements.txt
) else (
  call .venv\Scripts\activate.bat
)
set PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True
start "" http://127.0.0.1:8100
python app.py
pause

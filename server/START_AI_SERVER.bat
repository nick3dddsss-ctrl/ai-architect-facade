@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe (
  echo Creating Python environment...
  py -3.11 -m venv .venv 2>nul
  if errorlevel 1 python -m venv .venv
  call .venv\Scripts\activate.bat
  python -m pip install --upgrade pip
  echo Installing PyTorch CUDA...
  pip install torch torchvision --index-url https://download.pytorch.org/whl/cu126
  pip install -r requirements.txt
) else (
  call .venv\Scripts\activate.bat
)
set PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True
start "" http://127.0.0.1:8099/ui
python archicad_ai_server.py
pause

@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "ROOT=F:\TapirAIVisualizer"
set "APP=%ROOT%\tapir_ai"
set "VENV=%ROOT%\.venv"
set "TEMP=%ROOT%\temp"
set "TMP=%ROOT%\temp"
set "PIP_CACHE_DIR=%ROOT%\cache\pip"
set "PYTHONPYCACHEPREFIX=%ROOT%\cache\pycache"
set "HF_HOME=%ROOT%\models\huggingface"
set "HUGGINGFACE_HUB_CACHE=%ROOT%\models\huggingface\hub"
set "TRANSFORMERS_CACHE=%ROOT%\models\huggingface\transformers"
set "TORCH_HOME=%ROOT%\models\torch"
set "XDG_CACHE_HOME=%ROOT%\cache"
set "PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True"
set "HF_HUB_DISABLE_SYMLINKS_WARNING=1"

if not exist "F:\" (
  echo [ОШИБКА] Диск F: не найден.
  pause
  exit /b 1
)

if not exist "%APP%\app.py" (
  echo [ОШИБКА] Программа не установлена в %ROOT%
  echo Сначала запустите INSTALL_TO_F.bat из скачанного архива.
  pause
  exit /b 1
)

mkdir "%TEMP%" 2>nul
mkdir "%PIP_CACHE_DIR%" 2>nul
mkdir "%HF_HOME%" 2>nul
mkdir "%HUGGINGFACE_HUB_CACHE%" 2>nul
mkdir "%TRANSFORMERS_CACHE%" 2>nul
mkdir "%TORCH_HOME%" 2>nul
mkdir "%PYTHONPYCACHEPREFIX%" 2>nul

cd /d "%APP%"

where py >nul 2>nul
if errorlevel 1 (
  set "PY=python"
) else (
  set "PY=py -3.11"
)

if not exist "%VENV%\Scripts\python.exe" (
  echo.
  echo [1/3] Создание Python-среды на диске F:...
  %PY% -m venv "%VENV%"
  if errorlevel 1 (
    echo [ОШИБКА] Не удалось создать Python-среду.
    echo Проверьте, что установлен Python 3.11.
    pause
    exit /b 1
  )
  call "%VENV%\Scripts\activate.bat"

  echo [2/3] Установка PyTorch CUDA на диск F:...
  python -m pip install --upgrade pip --cache-dir "%PIP_CACHE_DIR%"
  python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu126 --cache-dir "%PIP_CACHE_DIR%"
  if errorlevel 1 (
    echo [ОШИБКА] Не удалось установить PyTorch.
    pause
    exit /b 1
  )

  echo [3/3] Установка AI Visualizer на диск F:...
  python -m pip install -r requirements.txt --cache-dir "%PIP_CACHE_DIR%"
  if errorlevel 1 (
    echo [ОШИБКА] Не удалось установить зависимости.
    pause
    exit /b 1
  )
) else (
  call "%VENV%\Scripts\activate.bat"
)

echo.
echo Рабочая папка: %ROOT%
echo Python: %VENV%
echo Модели: %HF_HOME%
echo Кэш: %ROOT%\cache
echo Временные файлы: %TEMP%
echo.
echo Первый запуск модели скачает несколько гигабайт именно на F:.
echo.

start "" http://127.0.0.1:8100
python app.py
pause

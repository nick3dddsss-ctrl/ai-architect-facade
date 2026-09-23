@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "ROOT=F:\TapirAIVisualizer"
set "TAPIR_DIR=%ROOT%\Tapir"
set "TAPIR_APX=%TAPIR_DIR%\TapirAddOn_AC25_Win.apx"

if not exist "F:\" (
  echo [ОШИБКА] Диск F: не найден.
  if /I not "%~1"=="/silent" pause
  exit /b 1
)

mkdir "%TAPIR_DIR%" 2>nul
mkdir "%ROOT%\temp" 2>nul

if exist "%TAPIR_APX%" (
  echo Tapir уже находится на диске F:
  echo %TAPIR_APX%
  if /I not "%~1"=="/silent" (
    explorer "%TAPIR_DIR%"
    pause
  )
  exit /b 0
)

echo Скачивание Tapir Add-On для Archicad 25...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/ENZYME-APD/tapir-archicad-automation/releases/latest/download/TapirAddOn_AC25_Win.apx' -OutFile '%TAPIR_APX%'"

if errorlevel 1 (
  echo [ОШИБКА] Не удалось скачать Tapir.
  if /I not "%~1"=="/silent" pause
  exit /b 1
)

if not exist "%TAPIR_APX%" (
  echo [ОШИБКА] Файл Tapir не найден после загрузки.
  if /I not "%~1"=="/silent" pause
  exit /b 1
)

echo Tapir сохранён на диске F:
echo %TAPIR_APX%
if /I not "%~1"=="/silent" (
  explorer "%TAPIR_DIR%"
  pause
)
exit /b 0

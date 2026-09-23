@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "ROOT=F:\TapirAIVisualizer"

if not exist "F:\" (
  echo [ОШИБКА] Диск F: не найден.
  echo Подключите диск F: и запустите файл снова.
  pause
  exit /b 1
)

echo.
echo ==============================================
echo   Tapir AI Visualizer - установка на диск F:
echo ==============================================
echo.
echo Папка установки:
echo %ROOT%
echo.

mkdir "%ROOT%" 2>nul
mkdir "%ROOT%\Tapir" 2>nul
mkdir "%ROOT%\models\huggingface" 2>nul
mkdir "%ROOT%\models\torch" 2>nul
mkdir "%ROOT%\cache\pip" 2>nul
mkdir "%ROOT%\cache\pycache" 2>nul
mkdir "%ROOT%\temp" 2>nul

echo [1/3] Копирование программы на F:...
robocopy "%~dp0tapir_ai" "%ROOT%\tapir_ai" /E /R:1 /W:1 /NFL /NDL /NJH /NJS >nul
if errorlevel 8 (
  echo [ОШИБКА] Не удалось скопировать файлы программы.
  pause
  exit /b 1
)

copy /Y "%~dp0START_TAPIR_AI.bat" "%ROOT%\START_TAPIR_AI.bat" >nul
copy /Y "%~dp0DOWNLOAD_TAPIR_AC25.bat" "%ROOT%\DOWNLOAD_TAPIR_AC25.bat" >nul
copy /Y "%~dp0README_RU.md" "%ROOT%\README_RU.md" >nul

echo [2/3] Скачивание Tapir для Archicad 25 на F:...
call "%ROOT%\DOWNLOAD_TAPIR_AC25.bat" /silent
if errorlevel 1 (
  echo [ОШИБКА] Tapir не скачался.
  pause
  exit /b 1
)

echo [3/3] Готово.
echo.
echo Все рабочие файлы, Python-среда, модели, кэш и результаты будут на F:.
echo.
echo Добавьте в Archicad этот файл:
echo %ROOT%\Tapir\TapirAddOn_AC25_Win.apx
echo.
echo Затем запустите:
echo %ROOT%\START_TAPIR_AI.bat
echo.
explorer "%ROOT%"
pause

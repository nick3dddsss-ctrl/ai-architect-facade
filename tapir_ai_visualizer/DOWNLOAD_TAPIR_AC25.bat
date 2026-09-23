@echo off
setlocal
cd /d "%~dp0"
if not exist Tapir mkdir Tapir
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/ENZYME-APD/tapir-archicad-automation/releases/latest/download/TapirAddOn_AC25_Win.apx' -OutFile 'Tapir\TapirAddOn_AC25_Win.apx'"
echo.
echo Tapir downloaded to:
echo %CD%\Tapir\TapirAddOn_AC25_Win.apx
explorer "%CD%\Tapir"
pause

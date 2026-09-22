$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$work = Join-Path $root "_build_template"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
git clone --depth 1 --recurse-submodules https://github.com/GRAPHISOFT/archicad-addon-cmake.git $work
Copy-Item "$root\addon_overlay\Src\*" "$work\Src\" -Force
Copy-Item "$root\addon_overlay\RINT\AddOn.grc" "$work\RINT\AddOn.grc" -Force
Copy-Item "$root\addon_overlay\RFIX\AddOnFix.grc" "$work\RFIX\AddOnFix.grc" -Force
Copy-Item "$root\addon_overlay\config.json" "$work\config.json" -Force
$dev = Join-Path $root "_devkit25"
if (!(Test-Path "$dev\Support")) { python "$root\tools\download_and_unzip.py" "https://github.com/GRAPHISOFT/archicad-api-devkit/releases/download/25.3002/API.Development.Kit.WIN.25.3002.zip" $dev }
cmake -B "$work\Build" -G "Visual Studio 17 2022" -A x64 -T v142 -DAC_VERSION=25 -DAC_API_DEVKIT_DIR="$dev\Support" $work
cmake --build "$work\Build" --config RelWithDebInfo
$apx = Get-ChildItem "$work\Build\RelWithDebInfo\*.apx" | Select-Object -First 1
if (!$apx) { throw "APX not found" }
Copy-Item $apx.FullName "$root\AIVisualizer_AC25.apx" -Force
Write-Host "DONE: $root\AIVisualizer_AC25.apx"

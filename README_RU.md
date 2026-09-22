# Archicad 25 AI Visualizer — Full v1

**ArchiCAD 25 Add-On → PNG текущего окна → локальный AI server → Stable Diffusion 1.5 + ControlNet Canny → результат.**

## Что делает
- меню AI Visualizer внутри Archicad;
- экспорт текущего 3D-вида/плана в PNG;
- передача только на `127.0.0.1`;
- локальный Stable Diffusion 1.5;
- локальный ControlNet Canny для сохранения геометрии;
- шкала прогресса;
- UI с Prompt, Strength, CFG, ControlNet и разрешением.

## После получения .apx
1. Запустить `server/START_AI_SERVER.bat`.
2. В Archicad: Options → Add-On Manager → подключить `.apx`.
3. Открыть нужный 3D-вид.
4. AI Visualizer → AI Visualizer...
5. В открывшемся окне нажать «Сгенерировать».

## RTX 3050 8GB
1024×768, 24–30 steps, Strength 0.42–0.55, ControlNet 0.8–1.0.

## Сборка
- `BUILD_PLUGIN_WINDOWS.bat` — локальная автоматическая сборка.
- `.github/workflows/build_ac25.yml` — автоматическая сборка GitHub Actions.

Официальный AC25 DevKit берётся из релиза `25.3002`, сборка идёт toolset `v142`.

## MDID
В `addon_overlay/RFIX/AddOnFix.grc` сейчас тестовые ID `1 / 1`. Для нормальной загрузки в лицензированный Archicad как собственного Add-On нужны Developer ID и Local ID Graphisoft. После их получения заменяются только два числа и плагин пересобирается.

## Следующий этап
Semantic/Depth карты по типам элементов Archicad, палитра прямо внутри Archicad и вставка результата обратно в проект.

---
CI: GitHub Actions настроен на автоматическую сборку Windows `.apx` при каждом push в `main`.

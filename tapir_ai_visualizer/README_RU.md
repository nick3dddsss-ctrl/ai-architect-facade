# Tapir AI Visualizer для Archicad 25 — версия для диска F:

Эта сборка специально переделана так, чтобы **все крупные данные проекта хранились на диске F:**.

Папка установки:

`F:\TapirAIVisualizer`

Туда помещаются:

- Tapir Add-On;
- программа AI Visualizer;
- виртуальная среда Python и все Python-библиотеки;
- PyTorch;
- Stable Diffusion и ControlNet;
- Hugging Face cache;
- pip cache;
- временные файлы;
- результаты генерации.

На диске C: остаются только уже установленный Windows/Python/Archicad и их системные компоненты. Новые многогигабайтные модели и библиотеки эта сборка направляет на F:.

## Установка

1. Распакуй архив куда угодно.
2. Запусти `INSTALL_TO_F.bat`.
3. Будет создана папка `F:\TapirAIVisualizer`.
4. Tapir автоматически загрузится сюда: `F:\TapirAIVisualizer\Tapir\TapirAddOn_AC25_Win.apx`.
5. В Archicad открой **Параметры → Менеджер расширений → Добавить** и выбери этот `.apx`.
6. Запусти `F:\TapirAIVisualizer\START_TAPIR_AI.bat`.

## Куда идут большие файлы

- Python venv: `F:\TapirAIVisualizer\.venv`
- модели Hugging Face: `F:\TapirAIVisualizer\models\huggingface`
- Torch cache: `F:\TapirAIVisualizer\models\torch`
- pip cache: `F:\TapirAIVisualizer\cache\pip`
- Python cache: `F:\TapirAIVisualizer\cache\pycache`
- TEMP/TMP: `F:\TapirAIVisualizer\temp`
- результаты: `F:\TapirAIVisualizer\tapir_ai\data\results`

Желательно иметь на F: не менее **15–20 ГБ свободного места**.

Если старая версия уже успела скачать модели на C:, эта новая версия их автоматически не удаляет. После проверки версии на F: старые кэши можно удалить отдельно.

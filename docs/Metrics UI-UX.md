# Metrics UI/UX.md

Практическая инструкция: что уже посчитано, где это лежит и как этим пользоваться. Обоснование решений (почему именно эти метрики, почему упрощения приняты) — в [docs/specs/0033-ui-metrics-tooling.md](specs/0033-ui-metrics-tooling.md), здесь не повторяется. Статус доработок — [Roadmap 2.md](roadmaps/Roadmap%202.md), Milestone 27 (реализовано) и Milestone 29 (запланировано).

## Что уже считается

| Метрика | Где | Диапазон/природа | Что показывает |
|---|---|---|---|
| WCAG-контраст | `android_gateway/app/src/test/.../tooling/UiMetricsReportTest.kt` | ratio, AA-порог 4.5:1 / 3:1 | Контраст для 6 пар ролей `ColorScheme` (light+dark) |
| Touch-target | тот же файл | эвристика, найдено/не найдено | Интерактивные элементы `ui/**/*.kt` без явного `>=48dp` |
| Colorfulness | `tools/ui-metrics/ui_metrics/colorfulness.py` | Hasler-Süsstrunk, точная формула | Насколько цветной экран |
| Feature congestion | `tools/ui-metrics/ui_metrics/feature_congestion.py` | упрощённая эвристика (не точный Rosenholtz) | Визуальный "шум"/загруженность |
| Saliency | `tools/ui-metrics/ui_metrics/saliency.py` | DeepGaze, карта 0-1 → std карты | Куда, по модели, смотрит пользователь в первую очередь |
| Symmetry / Balance | `tools/ui-metrics/ui_metrics/symmetry.py` | упрощённая эвристика (не точный APB) | Зеркальная симметрия / смещение визуального веса от центра |

Все Stage B/C-метрики (colorfulness/feature_congestion/saliency/symmetry/balance) — **экстраполяция**: валидированы на графическом дизайне/естественных изображениях, не на chat-интерфейсах. Числовых порогов приёмки нет и намеренно не будет (см. спека 0033, Допущения 7/12) — интерпретировать относительно (лучше/хуже среди своих экранов), не абсолютно.

## Как пользоваться

### 1. Обновить/сгенерировать снапшоты (если менялся UI)

```
cd android_gateway
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```
Генерирует PNG в `android_gateway/app/src/test/snapshots/` для `ConversationsScreen`/`ThreadScreen`/`SettingsScreen`, обе темы. Без флага `-Proborazzi.test.record` — обычный прогон **сравнивает** с уже закоммиченными baseline и падает при визуальной регрессии.

Отчёт по контрасту/touch-target печатается в консоль при обычном `./gradlew :app:testDebugUnitTest` (тест `UiMetricsReportTest`, не падает сборку — только печатает).

### 2. Python-метрики (colorfulness/feature_congestion/saliency/symmetry/balance)

Первый раз — окружение:
```
cd tools/ui-metrics
python -m venv .venv
.venv\Scripts\activate      # Windows; source .venv/bin/activate на Unix
pip install -r requirements.txt
```
`requirements.txt` тянет PyTorch + DeepGaze (~600МБ весов, качаются при первом реальном вызове saliency) — установка первого раза может занять несколько минут.

Прогон на реальных скриншотах:
```
python -m ui_metrics ../../android_gateway/app/src/test/snapshots
```
Печатает таблицу — по одной строке на PNG, 5 числовых колонок. Можно указать один файл вместо директории.

### 3. Тесты самого инструментария

```
# Kotlin-сторона (WCAG-формула)
cd android_gateway && ./gradlew :app:testDebugUnitTest --tests "*ContrastRatioTest"

# Python-сторона
cd tools/ui-metrics && pytest tests/
```
Это тесты **обвязки** (не падает на тривиальных синтетических входах, корректно читает PNG и т.п.), не тесты "правильности" внешних моделей/формул — у DeepGaze/feature-congestion-эвристики нет ground truth в этом проекте, проверить их точность нечем.

## Известные ограничения (коротко — детали в спеке 0033)

- `feature_congestion`/`symmetry`/`balance` — задокументированные упрощения published-алгоритмов (Rosenholtz / APB), не точное воспроизведение.
- Точная реализация APB (Ngo/Teo/Byrne 2003, оперирует geometry Compose semantics-дерева, а не пикселями) — запланирована, см. Roadmap 2.md, Milestone 29. Пока не реализована.
- `saliency`/`symmetry`/`balance` валидированы не на chat UI — использовать как relative-сигнал между своими же экранами, не как абсолютную оценку "хорошо/плохо".
- Touch-target отчёт — эвристика (regex + окно ±3 строки), возможны и ложные срабатывания, и пропуски.

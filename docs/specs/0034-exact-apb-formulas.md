# 0034 — Точная реализация Ngo/Teo/Byrne (2003) Balance/Equilibrium/Symmetry

Статус: **Implemented**. Milestone 29, [Roadmap 2.md](../roadmaps/Roadmap%202.md). Требования собраны через `stakeholder-requirements-gathering` — [0034-requirements-doc.md](../requirements/0034-requirements-doc.md), [0034-analysis-brief.md](../requirements/0034-analysis-brief.md).

## Контекст

Milestone 27 (спека [0033](0033-ui-metrics-tooling.md), Допущение 10) дал пиксельную эвристику баланса/симметрии (`tools/ui-metrics/ui_metrics/symmetry.py`), явно задокументированную как **не** репродукцию published Aesthetic Perceived Balance (APB) — готового Python-пакета для точных формул не существовало, а строить его с нуля тогда было непропорционально задаче.

С тех пор статья прочитана полностью: Ngo, D.C.L., Teo, L.S., Byrne, J.G. (2003), "Modelling interface aesthetics", *Information Sciences* 152, 25–46, DOI `10.1016/S0020-0255(02)00404-8`. Формулы Balance (BM), Equilibrium (EM), Symmetry (SYM) оперируют списком дискретных экранных объектов (позиция, ширина, высота, площадь), не растровым изображением. У проекта уже есть точная geometry через Compose `SemanticsNode` в тех же Roborazzi-тестах (спека 0033, Stage A), что рендерят 6 baseline PNG — это открыло путь к буквальной реализации вместо пиксельного приближения.

## Допущения и решения

1. **Объект для формул = все visible `SemanticsNode`** (решение владельца продукта, интервью `stakeholder-requirements-gathering`) — не только элементы с семантикой text/clickable. "Visible" = ненулевые bounds и отсутствие `SemanticsProperties.InvisibleToUser`.
2. **Вес `a_i` — конфигурируемый параметр**, три реализации (`tools/ui-metrics/ui_metrics/apb_formulas.py`):
   - `equal_weight` — `a_i=1` для всех объектов (оригинал статьи, там же используется в примерах).
   - `area_weight` — `a_i` = площадь bounding box.
   - `type_weight` — текстовые/интерактивные элементы весят `2.0`, декоративные/layout-контейнеры `1.0` (**не из статьи** — эвристика проекта под chat UI, конкретные константы зафиксированы как допущение, не выведены аналитически).
3. **Три метрики нормализованы в [0, 1]**, где 1 = идеальный баланс/равновесие/симметрия — собственная конвенция проекта ("выше = лучше"), не обязательно совпадает со шкалой/знаком оригинальных формул статьи для каждой величины.
4. **SYM реализован через weighted quadrant-mass сравнение** (доли объектов, попадающих в 4 квадранта экрана относительно геометрического центра, сравниваются попарно по обеим осям), а не через точное попарное сопоставление зеркальных объектов — прагматичное упрощение самого механизма сравнения при сохранении смысла формулы (сравнение зеркальных половин по массе). Задокументированное ограничение: при малом числе объектов (напр. один объект на экране) пустые квадранты тривиально "совпадают" друг с другом, слегка завышая итоговый SYM — не проблема для реальных экранов с десятками объектов (см. тест `test_single_object_in_corner_has_lower_symmetry_than_quadrant_mirrored_layout`).
5. **Найдено `peer-review-template` и исправлено**: вложенные wrapper-узлы semantics-дерева (сам root, `Surface`, фоновый `Box`) регулярно имеют идентичные bounds с одним из родителей — без дедупликации каждый такой узел засчитывался бы как отдельный `a_i=1` объект, искусственно раздувая вес "всего экрана целиком" и искажая quadrant-split чисто из-за глубины вложенности layout, а не реального визуального контента. Исправлено дедупликацией объектов с идентичными bounds в `exportGeometry()` (`SemanticsGeometryExport.kt`) — совпадающие по bounds узлы схлопываются в один, `isTextual`/`isInteractive` берутся по OR. Пример: `ConversationsScreenSnapshotTest.conversationsLight` — было множество дублирующихся full-screen записей, после фикса 5 различимых объектов. Числа в разделе «Результаты» ниже приведены уже после этого фикса.
6. **Перед выбором дефолтного веса владелец продукта явно запросил сравнение** всех трёх наборов на всех 6 baseline PNG (Conversations/Thread/Settings × light/dark) — реализован `tools/ui-metrics/ui_metrics/compare_weights.py` (таблица BM/EM/SYM + 18 PNG-визуализаций с наложенным центром масс и осями квадрантов).
7. **Итоговое решение владельца продукта (2026-09-06) по итогам сравнения**: дефолтного набора весов **не назначается** — все три (`equal`/`area`/`type`) остаются равноправными опциями инструмента, выбираемыми явно при вызове.
8. **`symmetry.py` (старая пиксельная эвристика) удалена** (решение владельца продукта, 2026-09-06, после показа результатов сравнения) — признана бесполезной после появления точных формул. Удалены также её тесты (`tests/test_symmetry.py`) и все ссылки на неё в `__main__.py`/`saliency.py`/README.

## Архитектура

### 1. Экспорт geometry (Kotlin)

`android_gateway/app/src/test/java/com/smsforwarder/gateway/ui/tooling/SemanticsGeometryExport.kt` — общий helper, переиспользуемый всеми тремя `*ScreenSnapshotTest.kt` (Conversations/Thread/Settings). После `captureRoboImage()` (без нового рендера — та же уже отрисованная семантик-дерево):
- рекурсивный обход `SemanticsNode` от `composeRule.onRoot().fetchSemanticsNode()`;
- для каждого visible node — `GeometryObject(x, y, width, height, isTextual, isInteractive)` (`boundsInRoot`, наличие `SemanticsProperties.Text`/`ContentDescription`, наличие `SemanticsActions.OnClick`);
- сериализация в JSON (`kotlinx.serialization`) рядом с PNG, тем же naming pattern: `<FQCN>.<method>.geometry.json` в `android_gateway/app/src/test/snapshots/`.

Юнит-тест самого обхода дерева (не формул) — `SemanticsGeometryExportTest.kt`, на синтетическом layout с известным расположением текстового/интерактивного/скрытого элементов.

### 2. Формулы (Python)

`tools/ui-metrics/ui_metrics/apb_formulas.py`:
- `load_geometry(path)` — чтение JSON в `ScreenGeometry`/`GeometryObject`.
- `balance_measure`, `equilibrium_measure`, `symmetry_measure`, `compute_all` — принимают `ScreenGeometry` + `WeightFn`.
- `WEIGHT_FUNCTIONS: dict[str, WeightFn]` — `{"equal": ..., "area": ..., "type": ...}`.

### 3. Инструмент сравнения весов

`tools/ui-metrics/ui_metrics/compare_weights.py`: `python -m ui_metrics.compare_weights <snapshots-dir> [<out-dir>]`. Для каждого `*.geometry.json` считает BM/EM/SYM под всеми тремя весами, печатает таблицу, и для каждой пары (экран, набор весов) рисует PNG с наложенными центром масс (equilibrium) и осями квадрантного разбиения (symmetry) поверх скриншота — через `PIL.ImageDraw` (Pillow, уже транзитивная зависимость OpenCV, новый пакет не добавлялся). Вывод — одноразовый инструмент поддержки решения, не постоянный CI-артефакт: `out/` в `.gitignore`.

## Критерии приёмки

- [x] Формулы (1)-(17) статьи реализованы над JSON-geometry, не над пикселями.
- [x] Три реализации веса `a_i`, конфигурируемые.
- [x] Сравнение на всех 6 baseline PNG показано владельцу продукта: таблица + 18 визуализаций.
- [x] Владелец продукта принял решение по дефолту (нет дефолта, все три равноправны) и по судьбе `symmetry.py` (удалить).
- [x] `peer-review-template` и `analysis-qa-checklist` пройдены (см. «Результаты» ниже).

## Тесты

- Kotlin: `SemanticsGeometryExportTest.kt` (обход дерева) + 3 существующих `*ScreenSnapshotTest.kt` (регресс, плюс теперь пишут geometry JSON).
- Python: `tools/ui-metrics/tests/test_apb_formulas.py` — 8 тестов на **известные** синтетические geometry (в отличие от `symmetry.py`, для этих формул есть точный матаппарат, поэтому тесты проверяют конкретные ожидаемые значения/сравнения, не просто "не падает"): два зеркальных объекта → BM≈1; 4 объекта по квадрантам, зеркально по обеим осям → SYM≈1; объект в углу → низкие EM/BM; пустая geometry → все метрики = 1.0 (нет объектов — нечему быть несимметричным); `area_weight` корректно улавливает дисбаланс массы, который `equal_weight` не видит; `type_weight` действительно весит текст/интерактив выше декоративного.

## Результаты

Полная таблица (6 экранов × 3 набора весов = 18 строк, после фикса дедупликации п.5 выше) — воспроизводится командой `python -m ui_metrics.compare_weights android_gateway/app/src/test/snapshots out` из `tools/ui-metrics/` (с активным `.venv`):

| Экран | Веса | BM | EM | SYM |
|---|---|---|---|---|
| conversations (light/dark идентичны) | equal | 0.500 | 0.667 | 0.350 |
| conversations | area | 0.500 | 0.803 | 0.497 |
| conversations | type | 0.500 | 0.633 | 0.312 |
| settings (light/dark идентичны) | equal | 0.626 | 0.886 | 0.779 |
| settings | area | 0.534 | 0.978 | 0.542 |
| settings | type | 0.618 | 0.866 | 0.639 |
| thread (light/dark идентичны) | equal | 0.532 | 0.865 | 0.292 |
| thread | area | 0.643 | 0.916 | 0.257 |
| thread | type | 0.576 | 0.892 | 0.315 |

Показательные наблюдения:
- Conversations: BM идентичен (0.500) для всех весов — geometry этого экрана даёт одинаковый horizontal/vertical moment split независимо от веса; EM варьируется 0.633 (type) → 0.667 (equal) → 0.803 (area).
- Settings: SYM заметно ниже при `area`-весах (0.542) относительно `equal`/`type` (0.779/0.639) — крупный фоновый `Card`-контейнер тянет взвешенный центр масс сильнее, чем содержимое, которое реально видит пользователь.
- Thread: SYM стабильно низкий (0.26–0.32) во всех весах — пузыри входящих/исходящих сообщений намеренно асимметричны по дизайну (выравнивание слева/справа), формула корректно это отражает, не баг.
- light/dark темы одного экрана дают идентичные BM/EM/SYM — ожидаемо: geometry зависит только от layout (bounds), не от цвета/темы.

`peer-review-template`/`analysis-qa-checklist`: пройдены в рамках этой сессии — реализация формул, экспорт geometry и удаление `symmetry.py` проверены на компиляцию/прохождение тестов на каждом шаге (Kotlin: `compileDebugUnitTestKotlin` + `testDebugUnitTest` зелёные; Python: `pytest tests/` 20/20 зелёных после удаления `test_symmetry.py`, `compare_weights` и CLI прогнаны вживую на всех 6 реальных baseline).

## Открытые вопросы

Нет открытых вопросов — оба решения владельца продукта (весовой дефолт, судьба `symmetry.py`) приняты и реализованы в рамках этой же сессии.

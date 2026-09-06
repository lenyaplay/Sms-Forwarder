# Metrics UI/UX.md

Практическая инструкция: что уже посчитано, где это лежит и как этим пользоваться. Обоснование решений (почему именно эти метрики, почему упрощения приняты) — в [docs/specs/0033-ui-metrics-tooling.md](specs/0033-ui-metrics-tooling.md)/[0034-exact-apb-formulas.md](specs/0034-exact-apb-formulas.md), здесь не повторяется. Статус доработок — [Roadmap Archived 2.md](roadmaps/Roadmap%20Archived%202.md), Milestone 27 и 29 (оба реализованы), текущая работа — [Roadmap 3.md](roadmaps/Roadmap%203.md).

## Что уже считается

| Метрика | Где | Диапазон/природа | Что показывает |
|---|---|---|---|
| WCAG-контраст | `android_gateway/app/src/test/.../tooling/UiMetricsReportTest.kt` | ratio, AA-порог 4.5:1 / 3:1 | Контраст для 6 пар ролей `ColorScheme` (light+dark) |
| Touch-target | тот же файл | эвристика, найдено/не найдено | Интерактивные элементы `ui/**/*.kt` без явного `>=48dp` |
| Colorfulness | `tools/ui-metrics/ui_metrics/colorfulness.py` | Hasler-Süsstrunk, точная формула | Насколько цветной экран |
| Feature congestion | `tools/ui-metrics/ui_metrics/feature_congestion.py` | упрощённая эвристика (не точный Rosenholtz) | Визуальный "шум"/загруженность |
| Saliency | `tools/ui-metrics/ui_metrics/saliency.py` | DeepGaze, карта 0-1 → std карты | Куда, по модели, смотрит пользователь в первую очередь |
| Balance (BM) | `tools/ui-metrics/ui_metrics/apb_formulas.py` | Ngo/Teo/Byrne (2003), формулы (1)-(4), [0,1] | Баланс "оптического веса" (площади объектов) слева/справа и сверху/снизу от центра экрана |
| Equilibrium (EM) | тот же файл | формулы (5)-(7), [0,1] | Насколько взвешенный по площади центр масс контента совпадает с геометрическим центром экрана |
| Symmetry (SYM) | тот же файл | формулы (8)-(17), [0,1] | Зеркальная симметрия расположения/размеров объектов по вертикали/горизонтали/диагонали |

Colorfulness/feature_congestion/saliency — **экстраполяция**: валидированы на графическом дизайне/естественных изображениях, не на chat-интерфейсах, числовых порогов приёмки нет и намеренно не будет (см. спека 0033, Допущения 7/12) — интерпретировать относительно (лучше/хуже среди своих экранов), не абсолютно. BM/EM/SYM — точная реализация формул статьи (не экстраполяция и не упрощение, но с двумя задокументированными техническими допущениями, см. спеку 0034: нормализация "primed"-величин SYM и обработка объектов, центр которых лежит точно на оси разбиения экрана).

## Текущее состояние метрик — снимок перед началом работы над дизайном (2026-09-06, обновлено после расширения покрытия до всех 7 экранов)

Значения ниже посчитаны на всех 14 baseline-скриншотах (`android_gateway/app/src/test/snapshots/`, все 7 экранов приложения × light/dark: Conversations/Thread/Settings/Delivery/DeliveryLog/FilterRules/FilterRuleEdit) непосредственно перед тем, как владелец продукта приступает к улучшению дизайна — фиксируются как точка отсчёта для сравнения "было/стало" после изменений. Первая версия этого снимка (2026-09-06, раньше в этот же день) покрывала только 3 экрана — обновлено после добавления снапшот-тестов для оставшихся четырёх и фикса обрезки контента (см. ниже).

**WCAG-контраст** (`UiMetricsReportTest`, обе темы): **12 из 12 пар ролей `ColorScheme` проходят и AA-normal (4.5:1), и AA-large (3:1)** — ни одного FAIL. Коэффициенты варьируются от 5.48 (dark, surfaceVariant/onSurfaceVariant) до 16.23 (light, surface/onSurface и background/onBackground). Не зависит от того, сколько экранов покрыто снапшотами — считается по самой цветовой схеме, не по конкретным экранам.

**Touch-target** (та же команда, эвристика): **69 отмеченных вхождений** интерактивных элементов без явного `>=48dp` рядом в коде — распределены по всем экранам (`FilterRulesScreen.kt` и `ThreadScreen.kt`/`SettingsScreen.kt`/`DeliveryScreen.kt` дают больше всего срабатываний). Это репорт-инструмент по эвристике (regex + окно ±3 строки) — не значит, что 69 реальных элементов физически меньше 48dp (Material3-компоненты часто уже соответствуют по умолчанию без явного модификатора), но указывает, где стоит перепроверить вручную в первую очередь при работе над touch-target'ами.

**Colorfulness / Feature congestion / Saliency** (`python -m ui_metrics <snapshots-dir>`):

| Экран | Colorfulness | Feature congestion | Saliency |
|---|---|---|---|
| conversationsDark | 12.109 | 0.764 | 0.221 |
| conversationsLight | 12.641 | 0.730 | 0.227 |
| deliveryDark | 10.101 | 1.211 | 0.127 |
| deliveryLight | 13.438 | 1.185 | 0.149 |
| deliveryLogDark | 4.199 | 0.640 | 0.206 |
| deliveryLogLight | 7.946 | 0.561 | 0.198 |
| filterRuleEditDark | 10.774 | 0.705 | 0.152 |
| filterRuleEditLight | 12.789 | 0.695 | 0.190 |
| filterRulesDark | 31.242 | 1.597 | 0.147 |
| filterRulesLight | 17.535 | 1.243 | 0.168 |
| settingsDark | 8.320 | 1.246 | 0.193 |
| settingsLight | 10.237 | 1.153 | 0.180 |
| threadDark | 20.686 | 0.940 | 0.235 |
| threadLight | 10.113 | 0.781 | 0.251 |

Заметно: `filterRulesDark` — теперь самый "цветной" экран во всей выборке (31.242, заметно выше даже `threadDark`) и одновременно с самой высокой feature congestion (1.597) — включённые фиолетовые `Switch`/выделенный `SegmentedButton` на тёмном фоне дают и цвет, и визуальную сложность одновременно. `deliveryLogDark` — самый низкий и по colorfulness (4.199), и по congestion (0.640) — простой текстовый список без акцентных цветов и интерактивных элементов. `settingsDark`/`settingsLight` остаются с высокой congestion (1.153-1.246) при низкой colorfulness — тот же структурный (не цветовой) визуальный шум, что и в первом снимке.

**Balance / Equilibrium / Symmetry** (`python -m ui_metrics.apb_report <snapshots-dir>`, точные формулы статьи — geometry не зависит от темы, light/dark дают идентичные числа):

| Экран | BM | EM | SYM |
|---|---|---|---|
| conversations | 0.500 | 0.976 | 0.632 |
| delivery | 0.280¹ | 0.998 | 0.548 |
| deliveryLog | 0.213¹ | 0.997 | 0.469 |
| filterRuleEdit | 0.015¹ | 0.994 | 0.495 |
| filterRules | 0.678 | 0.999 | 0.468 |
| settings | 0.490 | 0.998 | 0.595 |
| thread | 0.514 | 0.994 | 0.470 |

¹ **Важная оговорка, не путать с реальным дизайн-дисбалансом**: `delivery`/`deliveryLog`/`filterRuleEdit` рендерятся на виртуальном Robolectric-экране, специально увеличенном по высоте (`@Config(qualifiers = "...h1200dp"/"...h800dp")`), чтобы контент не обрезался при захвате скриншота (см. `DeliveryScreenSnapshotTest`/`FilterRuleEditScreenSnapshotTest`) — но BM/EM/SYM считают геометрию **всего виртуального экрана целиком**, включая пустое пространство под контентом. Низкий BM здесь — артефакт лишней пустоты снизу, а не реальный визуальный перекос дизайна. `filterRules`/`conversations`/`settings`/`thread` используют экран без увеличения высоты (контент и так помещается) — их BM сравним между собой напрямую, `delivery`/`deliveryLog`/`filterRuleEdit` — нет, пока не будет отдельного решения, как считать geometry только по занятой контентом области, а не по всему виртуальному фрейму.

EM у всех 7 экранов высокий (0.976-0.999) — не показательно для трёх "растянутых" экранов по той же причине (центр контента возле верха при пустом низе всё ещё оказывается близко к общему центру фрейма чисто геометрически). SYM у `thread`/`deliveryLog`/`filterRules` ниже остальных (0.468-0.470) — для `thread` это ожидаемо (асимметрия входящих/исходящих сообщений по дизайну), для `deliveryLog`/`filterRules` пока не интерпретировано отдельно (возможно, тоже требует раздельного рассмотрения от эффекта пустого пространства).

## Как пользоваться

### 1. Обновить/сгенерировать снапшоты (если менялся UI)

```
cd android_gateway
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```
Генерирует PNG в `android_gateway/app/src/test/snapshots/` для `ConversationsScreen`/`ThreadScreen`/`SettingsScreen`, обе темы. Без флага `-Proborazzi.test.record` — обычный прогон **сравнивает** с уже закоммиченными baseline и падает при визуальной регрессии.

Отчёт по контрасту/touch-target печатается в консоль при обычном `./gradlew :app:testDebugUnitTest` (тест `UiMetricsReportTest`, не падает сборку — только печатает).

### 2. Python-метрики (colorfulness/feature_congestion/saliency)

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
Печатает таблицу — по одной строке на PNG, 3 числовых колонки (colorfulness/feature_congestion/saliency). Можно указать один файл вместо директории.

### 3. Balance / Equilibrium / Symmetry (BM/EM/SYM)

Требует geometry JSON рядом с PNG — пишутся автоматически тем же прогоном, что и снапшоты (шаг 1 выше), отдельного шага не нужно.

```
cd tools/ui-metrics
python -m ui_metrics.apb_report ../../android_gateway/app/src/test/snapshots [out-dir]
```
Печатает таблицу BM/EM/SYM (одна строка на экран) и сохраняет по одной визуализации на экран в `out-dir` (по умолчанию `./out/`, не коммитится) — area-взвешенный центр масс и оси квадрантного разбиения, наложенные на скриншот.

### 4. Тесты самого инструментария

```
# Kotlin-сторона (WCAG-формула)
cd android_gateway && ./gradlew :app:testDebugUnitTest --tests "*ContrastRatioTest"

# Python-сторона
cd tools/ui-metrics && pytest tests/
```
Это тесты **обвязки** для colorfulness/feature_congestion/saliency (не падает на тривиальных синтетических входах, корректно читает PNG и т.п.), не тесты "правильности" самих моделей/эвристик — у DeepGaze/feature-congestion-эвристики нет ground truth в этом проекте. Для BM/EM/SYM (`test_apb_formulas.py`) — другого рода тесты: сверены против **опубликованных в самой статье** чисел (Table 1/Table 2, Ngo/Teo/Byrne 2003), не просто синтетических ожиданий — см. спеку 0034.

## Известные ограничения (коротко — детали в спеках 0033/0034)

- `feature_congestion` — задокументированное упрощение published-алгоритма (Rosenholtz), не точное воспроизведение.
- `saliency` валидирован не на chat UI (DeepGaze обучен на графическом дизайне/естественных изображениях) — использовать как relative-сигнал между своими же экранами, не как абсолютную оценку "хорошо/плохо".
- BM/EM/SYM — точная реализация формул статьи, но с двумя явно задокументированными допущениями там, где сам текст статьи неполон: нормализация "primed"-величин в SYM (собственная интерпретация проекта) и обработка объектов, чей центр лежит точно на оси разбиения экрана (см. спеку 0034, известное расхождение с published-примером на таком layout'е).
- Touch-target отчёт — эвристика (regex + окно ±3 строки), возможны и ложные срабатывания, и пропуски.
- BM/EM/SYM для экранов, снятых на искусственно увеличенном виртуальном Robolectric-экране (`delivery`/`deliveryLog`/`filterRuleEdit` — см. таблицу выше), не сравнимы напрямую с остальными: пустое пространство под контентом входит в geometry как часть "экрана", искажая BM в первую очередь.

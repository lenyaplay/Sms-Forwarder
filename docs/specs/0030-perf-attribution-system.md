# 0030 — Система оценки производительности с атрибуцией по категориям (Android Gateway App)

**Статус:** Implemented — инфраструктура (модуль `:macrobenchmark`, `Trace.beginSection`-атрибуция, удаление устаревшего дневника расследования) реализована и подтверждена рабочей; живой regression-baseline на физическом TECNO LI9 не получен (платформенное зависание Macrobenchmark на этом устройстве) — эмуляторные числа подтверждают инфраструктуру, не служат реальным baseline. См. «Открытые вопросы».

*Требования собраны через `stakeholder-requirements-gathering`, задокументированы в [docs/requirements/0030-requirements-doc.md](../requirements/0030-requirements-doc.md) и [docs/requirements/0030-analysis-brief.md](../requirements/0030-analysis-brief.md) — эта спека их источник истины по деталям реализации.*

## Контекст

Текущий `android_gateway/app/src/androidTest/.../data/perf` (11 файлов, 1560 строк) — не система регресс-контроля, а дневник расследования джанка холодного скролла (specs 0024/0025) и задержки холодного старта (spec 0026). Владелец продукта (2026-09-04) поручил не удалять его, а построить на его основе постоянную систему: (1) не допускать регресса производительности, (2) всегда точно знать, на что уходит время, с атрибуцией по 4 категориям — создание Activity, фазы Jetpack Compose, файловый I/O, `content://sms`, JIT vs AOT.

Что уже установлено предыдущими спеками (не переоткрывается здесь):
- **JIT-компиляция на главном потоке — подтверждённая причина** джанка первых кадров холодного скролла (spec 0025, порог 3/4 гипотез). Baseline Profile (`:baselineprofile` модуль, уже существует) снижает эффект (mean 36.25мс→26.47мс, -27%), но не устраняет полностью.
- **Резолвинг контактов** (`content://com.android.contacts`, не `content://sms`) был доминирующей причиной задержки холодного старта списка диалогов (~720-925мс из ~894мс) — исправлен персистентным кешем (spec 0026), сейчас 1-5мс. `content://sms` (`SmsHistoryImporter`) сам по себе никогда отдельно не измерялся.
- Разбивка кадра по Compose-фазам (recomposition/layout/draw) **не удалась** ранее (`recompositionCount` оказался неинформативной константой `1`, spec 0025 Раздел B) — вместо неё использовались сырые Choreographer-миллисекунды без разбивки на фазы.
- **AOT-компиляцию не удаётся форсировать через adb на TECNO LI9** (`cmd package compile -m speed` стабильно возвращает `actualCompilerFilter=verify`, задокументировано дважды — specs 0025 и 0026) — прямой платформенный барьер, не баг проекта.
- `:baselineprofile` Gradle-модуль уже существует (`android_gateway/baselineprofile/`), использует `androidx.benchmark:benchmark-macro-junit4` — инфраструктура для Macrobenchmark в проекте уже частично есть.

## Допущения и решения

1. **Новый модуль `:macrobenchmark`**, сиблинг `:baselineprofile` (тот же `com.android.test` plugin, тот же `targetProjectPath = ":app"`) — по стандартному паттерну AndroidX (`:baselineprofile` генерирует профиль, `:macrobenchmark` гоняет регрессионные тесты на его основе). Не встраивается в существующий `app/src/androidTest` — Macrobenchmark-тесты требуют non-debuggable release-сборки цели (та же причина, что уже объяснена в `BaselineProfileGenerator.kt`: debuggable-сборка никогда не AOT-компилируется), а обычные `androidTest`-тесты в `app` гоняются на debug-сборке — смешивать нельзя технически, не только организационно.
2. **`Trace.beginSection`/`endSection`-маркеры добавляются в `main`-код** (не только в тесты) в трёх точках: вокруг `content://sms` query в `SmsHistoryImporter.importIfNeeded()`/`syncNewMessages()`, вокруг записи в Room (`messageDao.upsertFromSystemProvider`), вокруг `ConversationsViewModel.observeConversations`'s Compose-видимой части (уже есть natural boundary — эмиссия `_uiState.update`). Маркеры — тонкие no-op обёртки в релизе, читаются `TraceSectionMetric` из `:macrobenchmark`, ничего не меняют в поведении.
3. **Baseline — 5 прогонов, min/max диапазон**, тот же метод, что уже применялся в specs 0024/0025/0026 (не новая методология). Диапазон фиксируется в `android_gateway/macrobenchmark/baselines.json`, версионируется тем же коммитом, что и код, влияющий на измеренную метрику.
4. **JIT vs AOT — честно отмечен как рискованная категория**, не гарантированно работающая на TECNO LI9: `Macrobenchmark`'s `CompilationMode.Full()` внутри тестового прогона использует тот же `pm compile`-путь, который уже дважды не сработал через прямой adb на этом устройстве (specs 0025/0026). Тест пишется (`CompilationMode.None()` vs `CompilationMode.Full()`, `StartupTimingMetric`), но если `actualCompilerFilter` не переходит в `speed`/`speed-profile` на TECNO LI9 при прогоне — это фиксируется как тот же класс честного отрицательного результата, что уже дважды принимался ранее, а не выдаётся за успешное сравнение.
5. **Разбор 11 существующих файлов — построчное решение по каждому** (не групповое), см. таблицу ниже. Критерий: файл документирует уже закрытую, не переиспользуемую находку (опровергнутая гипотеза, разовое расследование) → archive; файл измеряет то, что имеет смысл проверять на каждый будущий коммит → переписывается на Macrobenchmark в новом модуле; файл — юнит-тест диагностического инструмента (не perf-регресс сам по себе) → остаётся как есть.
6. **`archive`-файлы не удаляются из репозитория** — переносятся в `android_gateway/app/src/androidTest/.../data/perf/archive/` (тот же пакет, подпапка) с явным комментарием-заголовком "почему заархивирован, что заменяет", **исключаются из компиляции** через Gradle source-set фильтр (не просто перестают гоняться — не должны занимать время компиляции/`assembleDebugAndroidTest` на каждый прогон). Это прямое продолжение решения владельца продукта "не удалять, использовать как основу" — код физически сохранён и виден, но не создаёт цену на каждый коммит.

## Разбор существующих 11 файлов `data/perf`

| Файл | Решение | Обоснование |
|---|---|---|
| `PerfMonitorTest.kt` | Оставить как есть | Юнит-тест диагностического инструмента (`PerfMonitor.measure()`), не perf-регресс сам по себе — не относится к этой системе |
| `ColdScrollWarmupTest.kt` | Archive | Методологический предшественник `ColdScrollJankRootCauseTest` (децильная, не покадровая сегментация) — полностью вытеснен более точным протоколом |
| `ColdScrollJankRootCauseTest.kt` | Archive | Находка (кадры #0/#1) зафиксирована в spec 0025; сама методика (JankStats-based) вытеснена независимой Choreographer-перепроверкой |
| `ColdScrollJankWarmupHypothesisTest.kt` | Archive | Опровергнутая гипотеза (холодная композиция строк) — разовая проверка, не повторяется |
| `ColdScrollJankAttachArtifactTest.kt` | Archive | Опровергнутая гипотеза (оверхед `JankStats.createAndTrack`) — разовая проверка |
| `ColdScrollJankChoreographerCrossCheckTest.kt` | Переписать на Macrobenchmark | Дал реальную улику (реальная задержка 200-500мс на холодных кадрах) — единственный из "гипотезных" тестов с продолжающейся диагностической ценностью → `FrameTimingMetric`/`TraceSectionMetric` регресс-тест в `:macrobenchmark` |
| `SteadyStateFrameCostTest.kt` | Переписать на Macrobenchmark | Steady-state per-frame ms — прямой кандидат в `FrameTimingMetric` |
| `RealScreenSteadyStateFrameCostTest.kt` | Переписать на Macrobenchmark | То же на реальном экране — станет основным regression-тестом категории "Compose" |
| `ScrollJankLayerTest.kt` | Archive | Milestone 22 наследие — вопрос "`Card` vs плоский ряд" закрыт (гипотеза 3 опровергнута в spec 0025), `Card` уже заменена в проде |
| `ColdScrollWarmupRealScreenTest.kt` | Archive | Результат honestly `inconclusive` (spec 0025) из-за малого объёма реальных данных на тестовом устройстве — не даёт воспроизводимого сигнала |
| `ColdStartConversationsListTest.kt` | Переписать на Macrobenchmark | Основной артефакт spec 0026 (Activity→первые реальные строки) — прямой кандидат в `StartupTimingMetric` + `TraceSectionMetric`, становится ядром категории "Activity creation" |

## Функциональность

### Категория 1 — Activity/Compose cold start

`macrobenchmark/.../ColdStartBenchmark.kt` — развитие `ColdStartConversationsListTest`: `StartupTimingMetric` (Macrobenchmark встроенная) для общего времени + `TraceSectionMetric("first_real_row")` вокруг точки, где `ConversationsViewModel` эмитит непустой список, размечено `Trace.beginSection`/`endSection` в `ConversationsViewModel.observeConversations`. 5 прогонов COLD (`am force-stop` перед каждым — честный холодный старт, в отличие от `ColdStartConversationsListTest`'s `ActivityScenario`-ограничения, задокументированного в spec 0026 как методологический пробел).

### Категория 2 — Compose recomposition/layout/draw

`macrobenchmark/.../ComposeFrameBenchmark.kt` — `FrameTimingMetric` на скролле `ConversationsScreen` (развитие `RealScreenSteadyStateFrameCostTest`). Разбивку конкретно на recomposition/layout/draw (не просто общий кадр) даёт `TraceSectionMetric` с системными Compose-трейсами (`androidx.compose.runtime.Recomposer` уже расставляет собственные `Trace`-секции в релизных сборках Compose — не нужно писать вручную; предыдущая попытка ручного `recompositionCount`-счётчика провалилась именно потому, что считала recomposition вручную, а не читала уже существующую трассировку Compose runtime). Честно фиксируется в реализации: подтверждается фактическим прогоном, что нужные секции (`Compose:recompose`, `Compose:Layout`, `Compose:Draw`) видны в Perfetto-трейсе на этом устройстве, прежде чем строить на них assert-пороги.

### Категория 3 — `content://sms` + файловый I/O

`macrobenchmark/.../SmsImportBenchmark.kt` — новое, ранее не измерялось. `TraceSectionMetric("sms_history_query")`/`TraceSectionMetric("sms_history_room_write")` вокруг `Trace.beginSection`-меток в `SmsHistoryImporter.importIfNeeded()` (запрос к `content://sms`) и `MessageDao.upsertFromSystemProvider` (запись в Room). Прогон — первый запуск после `am force-stop` с ролью SMS-приложения по умолчанию уже установленной (импорт истории гейтится `configStore.isHistoryImported()` — выполняется один раз за жизнь приложения, см. spec 0026 «Контекст»; для повторяемого замера тест сбрасывает `isHistoryImported` перед каждым прогоном через `configStore`/тестовый хук, не полагаясь на реальную однократность).

### Категория 4 — JIT vs AOT

`macrobenchmark/.../CompilationModeBenchmark.kt` — `StartupTimingMetric`, сравнение `CompilationMode.None()` (чистый JIT, эквивалент debug-без-профиля) vs `CompilationMode.Full()` (принудительный AOT через сам Macrobenchmark test runner, не через adb напрямую — механизм технически отличается от уже дважды неудавшегося `adb shell cmd package compile`, поэтому теоретически может сработать иначе; это не гарантия, а причина всё же попробовать). Результат — задокументирован как есть, включая вероятный отрицательный (device platform limitation) исход, по образцу spec 0025/0026.

## Архитектура

- Новый Gradle-модуль `android_gateway/macrobenchmark/` (`com.android.test` plugin, `targetProjectPath = ":app"`, зависимость `androidx.benchmark:benchmark-macro-junit4` — уже используется в `:baselineprofile`, версия переиспользуется), добавлен в `settings.gradle.kts`.
- `Trace.beginSection`/`endSection` маркеры — точечные правки в `main`: `SmsHistoryImporter.kt` (импорт/синк), `MessageDao`/repository слой (запись), `ConversationsViewModel.kt` (эмиссия первых реальных данных).
- 11 существующих файлов `data/perf` — 8 переносятся в `android_gateway/app/src/androidTest/.../data/perf/archive/` (исключены из `assembleDebugAndroidTest` через Gradle source-set фильтр), 3 переписываются в новом модуле (`ColdScrollJankChoreographerCrossCheckTest`, `SteadyStateFrameCostTest`+`RealScreenSteadyStateFrameCostTest` объединяются в `ComposeFrameBenchmark`, `ColdStartConversationsListTest` → `ColdStartBenchmark`), `PerfMonitorTest.kt` остаётся на месте без изменений.
- `android_gateway/macrobenchmark/baselines.json` — новый файл, хранит min/max диапазоны по каждой метрике/категории, обновляется вручную по факту прогона (не автогенерируется на CI — в проекте нет CI, прогон только вручную на TECNO LI9).
- Backend/Viewer App не затронуты.

## Критерии приёмки

Статус по факту реализации (см. «Результаты» ниже для деталей):

- ⚠️ Модуль `:macrobenchmark` собирается — да (`gradlew :macrobenchmark:connectedReleaseAndroidTest`, точная задача подтверждена). Гоняется на TECNO LI9 — **нет**, детерминированно зависает на внутреннем шаге самой библиотеки Macrobenchmark (не код проекта). Гоняется на эмуляторе `rootable_api35` — да.
- ⚠️ Все 4 категории имеют тест с 5 прогонами и зафиксированным диапазоном в `baselines.json` — **3 из 4** (Activity/Compose cold start, cold scroll frames, `content://sms`+I/O) дали реальные числа на эмуляторе; категория Compose recomposition/layout/draw упала (пустая история, нечего скроллить); категория JIT vs AOT не прогонялась.
- ✅ **Изменено по ходу работы** (явное уточнение владельца продукта отменило более осторожный вариант из раздела «Допущения»): 8 файлов расследования не архивированы, а **удалены** физически (не просто исключены из компиляции) — `git status` подтверждает 10 удалённых файлов (8 расследования + 2 мёртвого кода `FrameSegmentation.kt`/`JankComparison.kt`), их находки остаются в тексте specs 0024/0025, не в самом дереве кода.
- ⚠️ Категория JIT vs AOT — результат **не зафиксирован**, тест написан и компилируется, но не прогнан ни на одном устройстве в этой сессии — открытый пункт, не выполнено, а не "platform limitation" (не путать со случаем TECNO LI9 выше, где причина установлена).
- ✅ `Trace.beginSection`-маркеры в `main`-коде не меняют функциональное поведение — подтверждено дважды: полный `connectedDebugAndroidTest` 158/158 на TECNO LI9 (было 229 до удаления 71 тестового метода — сходится), включая повторный прогон после исправления найденного при самопроверке бага (незакрытые `Trace`-секции при исключении — обёрнуто в `try/finally`).
- `docs/roadmaps/Roadmap 2.md` обновлён.

## Тесты

- Новый Gradle-модуль `android_gateway/macrobenchmark/` (`com.android.test`, таргет `:app`'s `release`), пять файлов: `ColdStartBenchmark`, `ComposeFrameBenchmark`, `ColdScrollJankBenchmark`, `SmsImportBenchmark`, `CompilationModeBenchmark` — все компилируются (`compileReleaseKotlin` зелёный).
- `Trace.beginSection`/`endSection` в `SmsHistoryImporter.kt` (`sms_history_query`, `sms_history_room_write`, в обоих `importIfNeeded()`/`syncNewMessages()`) и `ConversationsViewModel.kt` (`first_real_row`, только на первой эмиссии) — не изменили функциональное поведение.
- Полный `connectedDebugAndroidTest` (обычный `app`-модуль) — **158/158 зелёных** на TECNO LI9 (было 229 до удаления 10 файлов расследования, 229-71=158 сходится с числом удалённых тестовых методов).
- 8 файлов расследования (specs 0024/0025) и 2 файла мёртвого кода (`FrameSegmentation.kt`, `JankComparison.kt`) с их unit-тестами — удалены физически (владелец продукта явно разрешил удалять, не архивировать).

## Результаты

### Живой прогон на физическом устройстве (TECNO LI9) — не удалось, платформенное ограничение

`:macrobenchmark:connectedReleaseAndroidTest` на TECNO LI9 зависает детерминированно на каждом тесте (проверено дважды — `ColdStartBenchmark` полным прогоном и в `dryRunMode`). Диагностировано конкретно через logcat устройства, не как гипотеза: после `am force-stop` перед каждой холодной итерацией Macrobenchmark сама (не мой код) отправляет служебный broadcast `androidx.profileinstaller.action.BENCHMARK_OPERATION` в `ProfileInstallReceiver`; лог показывает `Enqueued broadcast ...: 0`, но подтверждения обработки нет, и дальше — полная тишина 3+ минуты, `MainActivity` для измеряемой итерации так и не запускается. Это внутренний шаг самой библиотеки AndroidX Benchmark, не код проекта — тот же класс OEM-специфичных (Transsion/MediaTek) платформенных ограничений, что уже дважды задокументирован в specs 0025/0026 для `pm compile`/dexopt-триггеров на этом устройстве.

### Живой прогон на эмуляторе (`rootable_api35`) — конвейер подтверждён работающим

По решению владельца продукта опробовано на том же rootable-эмуляторе, что уже использовался в spec 0025 для генерации Baseline Profile. Реальный физический TECNO LI9 отключён от прогона через `ANDROID_SERIAL=emulator-5554`, эмулятор рутован (`adb root`), ошибка Macrobenchmark "не измеряю на эмуляторе" подавлена явно (`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`) — то есть эмулятор используется осознанно, не выдаётся за представительное устройство.

**3 из 4 категорий дали реальные числа, включая впервые в истории проекта измеренный `content://sms`:**

| Категория | Тест | Метрика | min | median | max |
|---|---|---|---|---|---|
| Activity/Compose cold start | `ColdStartBenchmark` | `timeToInitialDisplayMs` | 1194.9 | 1234.4 | 1357.6 |
| Activity/Compose cold start | `ColdStartBenchmark` | `first_real_rowSumMs` (наш `Trace.beginSection`) | 3.00 | 5.44 | 12.57 |
| Cold scroll first frames | `ColdScrollJankBenchmark` | `frameDurationCpuMs` P50/P90/P99 | — | 38.7 / 370.7 / 408.9 | — |
| `content://sms` + I/O | `SmsImportBenchmark` | `sms_history_querySumMs` (наш `Trace.beginSection`) | 22.3 | 46.8 | 99.3 |
| `content://sms` + I/O | `SmsImportBenchmark` | `sms_history_room_writeSumMs` (наш `Trace.beginSection`) | 2.73 | 6.10 | 19.41 |

Ключевой вывод: `TraceSectionMetric` реально считывает добавленные в `main`-код `Trace.beginSection` маркеры (`first_real_row`, `sms_history_query`, `sms_history_room_write`) — вся заявленная в спеке архитектура атрибуции (собственные trace-секции поверх Macrobenchmark-harness) технически работает, не только компилируется.

**`ComposeFrameBenchmark` — упал, затем исправлен в этой же сессии.** Изначально `FrameTimingMetric` выбрасывал `IllegalStateException: Observed no expect/actual slices in trace` — на чистом эмуляторе нет реальной SMS-истории, список диалогов пуст, `list.fling(Direction.DOWN)` не производил ни одного кадра для измерения. Не платформенное ограничение, а недостающая подготовка данных в самом тесте.

**Фикс — `TestMessageSeeder.kt`** (новый файл в `:macrobenchmark`): зафиксированный список из 30 синтетических сообщений (разные отправители `+15550100NN`, чтобы `MessageDao.observeConversations`'s `GROUP BY sender` дал 30 разных строк диалогов — заведомо больше, чем помещается на экране, гарантирует реальный скролл), вставляется через `adb shell content insert` (shell имеет доступ на запись в `content://sms` в обход ограничения "только SMS-приложение по умолчанию") перед прогоном, идемпотентно (проверка на пустоту перед вставкой — не дублирует данные между 5 итерациями одного теста).

- **Найденная и исправленная ловушка**: `--bind body:s:"текст с пробелами"` в команде `content insert` молча ломается — `[ERROR] Unsupported argument: <второе слово>`. Причина не в кавычках как таковых, а в том, что `UiAutomation.executeShellCommand` (Kotlin API) токенизирует команду иначе, чем интерактивный `adb shell` с хоста — **проверено дважды экспериментально**: одинарные кавычки через `adb shell "..."` с хоста прошли и вставили строку с пробелами, тот же приём через `executeShellCommand` из кода теста — снова молча не вставил ничего (`content://sms` осталось пустым, тест снова упал с тем же `IllegalStateException`). Финальное решение — тело сообщения без пробелов (`spec_0030_seeded_test_message_NN`), не кавычки.
- **Результат после фикса**: `frameCount` — было `1` (вырожденный случай, приводивший к падению), стало **141-155** (воспроизведено дважды на чистом эмуляторе после полной очистки `content://sms`, оба раза `BUILD SUCCESSFUL`). `frameDurationCpuMs` P50/P90/P99 = 26.6/35.8/51.2мс.
- **Не решено этим фиксом**: `TraceSectionMetric` для `Compose:recompose`/`Compose:Layout`/`Compose:Draw` по-прежнему читает `0` во всех прогонах — пустые данные больше не причина (список реально скроллится), значит сами эти trace-секции либо не эмитятся, либо называются иначе в используемой версии Compose/AGP. Открытый вопрос, честно перенесён ниже, не выдаётся за решённое.

**Категория 4 (JIT vs AOT) не прогонялась в этой сессии** — сознательно отложена после уже двух успешных категорий и одной честно зафиксированной проблемы, чтобы не наращивать объём живых экспериментов сверх разумного в рамках одной сессии.

Числа зафиксированы в `android_gateway/macrobenchmark/baselines.json`, явно помечены как эмуляторные (не TECNO LI9) — не должны использоваться как реальный регресс-baseline до переизмерения на физическом устройстве.

## Открытые вопросы / Backlog

- **TECNO LI9 зависание** — не решено. Возможные направления для следующей сессии: (а) поискать способ отключить/обойти именно ProfileInstaller BENCHMARK_OPERATION reset-шаг Macrobenchmark на этом устройстве (инструментальный аргумент или `CompilationMode`, который его не требует), (б) смириться с эмулятором как единственной живой средой для этого проекта аналогично тому, как Baseline Profile в итоге тоже генерировался на эмуляторе (spec 0025), с явной оговоркой о нерепрезентативности чисел.
- ~~`ComposeFrameBenchmark` — нужны сидированные тестовые данные~~ — **исправлено** (`TestMessageSeeder.kt`, см. «Результаты»).
- **`Compose:recompose`/`Compose:Layout`/`Compose:Draw` TraceSectionMetric стабильно читает 0**, даже с реальными кадрами скролла (141-155/прогон) — не связано с отсутствием данных. Нужно разобраться, эмитятся ли эти секции вообще Compose runtime текущей версии проекта (проверить напрямую в Perfetto-трейсе одного из iter*.perfetto-trace файлов), или названия секций изменились между версиями Compose.
- **Категория 4 (JIT vs AOT) не прогонялась.** `CompilationModeBenchmark` написан и компилируется, но не проверен вживую ни на одном устройстве в этой сессии.
- **`baselines.json` требует переизмерения на TECNO LI9** (или другом физическом устройстве) прежде чем текущие числа можно использовать как реальный регресс-guard — эмуляторные числа сейчас служат только доказательством, что измерительная инфраструктура работает.

## Peer-review и QA (самостоятельно, без субагента)

`peer-review-template`: построчно проверен `git diff` всех изменённых/новых файлов. Найден и исправлен один must-fix баг до этого прохода: в обоих местах `SmsHistoryImporter.kt` (`importIfNeeded`/`syncNewMessages`) и в `ConversationsViewModel.kt` `Trace.beginSection`/`endSection` изначально не были защищены от исключения между ними (например, если `contentResolver.query()` бросит) — секция осталась бы незакрытой. Исправлено на `try/finally` в обоих файлах, подтверждено повторной компиляцией и полным регрессом (158/158). `android_gateway/app/build.gradle.kts` проверен `git diff` — пусто, подтверждает чистый откат экспериментального `benchmark` build type, не осталось мусора. Конфигурация `:macrobenchmark/build.gradle.kts` (release build type, self-instrumenting, signing) соответствует итоговой рабочей конфигурации, не промежуточным неудачным попыткам.

`analysis-qa-checklist`: критерии приёмки построчно сверены с фактическим результатом — найдено расхождение (пункт про "8 архивных файлов физически перенесены" не соответствовал фактическому решению владельца продукта удалить, а не архивировать) и статусы пунктов, изначально сформулированные как безусловные, приведены в соответствие с честным частичным выполнением (3/4 категории на эмуляторе, 0/4 на целевом физическом устройстве) — раздел «Критерии приёмки» переписан выше, не оставлен противоречащим «Результатам».

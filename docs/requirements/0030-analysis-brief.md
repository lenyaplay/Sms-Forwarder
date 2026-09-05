# Analysis Brief

**Project:** Android Gateway App — новая система оценки производительности (perf-набор) с атрибуцией по категориям
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-09-04
**Delivery date:** не зафиксирована

---

## The Question

> Текущий `data/perf` — дневник расследования одного бага, не система регресс-контроля. Построить новую систему, которая (1) не допускает регресса производительности и (2) всегда точно знает, на что уходит время: Activity creation, Compose (recomposition/layout/draw), файловый I/O, `content://sms`, JIT/AOT.

---

## What "Done" Looks Like

1. `Macrobenchmark` подключён как harness; `StartupTimingMetric`/`FrameTimingMetric` заменяют самодельные `Choreographer`/`JankStats`-измерения там, где это применимо.
2. Собственные `Trace.beginSection` маркеры в продовом коде покрывают: `content://sms` запрос (`SmsHistoryImporter`), запись в Room/файл, Compose recomposition-границы ключевых composables (`ConversationsScreen`, `ThreadScreen`).
3. Отдельный тест на JIT vs AOT через `BaselineProfileRule`/`CompilationMode` сравнение.
4. Baseline — 5 прогонов на каждую метрику → min/max диапазон → выход за диапазон в последующих прогонах = регресс/прогресс.
5. Baseline-диапазоны зафиксированы в файле репозитория, обновляются тем же коммитом, что и код, который на них влияет.
6. Все 11 существующих файлов `data/perf` разобраны построчно: для каждого явное решение — переписать на Macrobenchmark / оставить как есть (закрытый вопрос, не требует повторного прогона на каждый коммит) / архивировать.

---

## Scope

| In scope | Out of scope |
|---|---|
| Новый/расширенный perf-модуль на `Macrobenchmark` | Новые UI-фичи |
| `Trace.beginSection` в `SmsHistoryImporter`, Compose-коде `ConversationsScreen`/`ThreadScreen` | Изменения backend-API |
| `BaselineProfileRule`/`CompilationMode` тест для JIT vs AOT | Ретроактивный анализ старых perf-инцидентов вне specs 0024/0025/0026 |
| Разбор и решение по каждому из 11 существующих файлов `data/perf` | — |
| Файл фиксации baseline min/max диапазонов | — |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Полное содержимое 11 файлов `data/perf` | `android_gateway/app/src/androidTest/.../data/perf/*.kt` | Частично — заголовки/doc-комментарии прочитаны в этой сессии, полное тело файлов и точные сценарии — предстоит перечитать при написании спеки |
| 2 | Спеки 0024/0025/0026 (что именно установлено расследованием, какие числа/пороги уже зафиксированы) | `docs/specs/0024-*.md`, `0025-*.md`, `0026-*.md` | Не прочитаны в этой сессии — обязательны к прочтению перед спекой 0030 |
| 3 | Текущая структура Gradle-модулей проекта (`app`, наличие/отсутствие отдельного `:macrobenchmark`) | `android_gateway/settings.gradle.kts`, `android_gateway/app/build.gradle.kts` | Не прочитаны в этой сессии — обязательны к прочтению перед спекой |
| 4 | `SmsHistoryImporter.kt` — точные границы для `Trace.beginSection` (запрос к провайдеру vs запись в Room) | `data/local/SmsHistoryImporter.kt` | Confirmed — прочитан в этой сессии (см. предыдущий контекст спеки 0029), структура понятна |

---

## Approach (high level)

1. Прочитать specs 0024/0025/0026 полностью — зафиксировать, что уже установлено (причина джанка, числа), чтобы не переоткрывать закрытые вопросы.
2. Прочитать все 11 файлов `data/perf` целиком (не только заголовки) — построчно решить судьбу каждого: (a) заменить на Macrobenchmark-эквивалент с тем же измеряемым сценарием, (b) оставить как есть (документирует уже закрытую находку), (c) архивировать/удалить.
3. Подключить `androidx.benchmark:benchmark-macro-junit4` — решить вопрос модульной структуры (отдельный `:macrobenchmark` Gradle-модуль по стандарту AndroidX, либо адаптация под текущую структуру, если модуль избыточен для проекта такого размера).
4. Добавить `Trace.beginSection`/`endSection` маркеры вокруг: `content://sms` query в `SmsHistoryImporter`, записи в Room/файл, Compose recomposition-точек в `ConversationsScreen`/`ThreadScreen`.
5. Написать Macrobenchmark-тесты по 4 категориям, каждый — 5 прогонов, вычисление min/max диапазона.
6. Подключить `androidx.baselineprofile` plugin (если не подключён) + `BaselineProfileRule` тест для JIT vs AOT сравнения.
7. Зафиксировать baseline-диапазоны в файле (JSON рядом с тестами или `docs/PerfBaselines.md` — решается на этапе спеки, по эргономике: JSON проще парсить самим тестам для сравнения, Markdown проще читать человеку — возможно нужны оба).
8. Автотесты гоняются вручную/по явному запросу (Macrobenchmark на TECNO LI9 не входит в обычный `connectedDebugAndroidTest` регресс — уже так для текущего `data/perf`, сохраняем паттерн).

---

## Output Format

**Deliverable:** Спецификация `docs/specs/0030-*.md`, новый/расширенный perf-код в `android_gateway/`, файл baseline-диапазонов, обновлённый `docs/roadmaps/Roadmap 2.md`.
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- `Macrobenchmark` обычно требует отдельного `com.android.test` Gradle-модуля — для проекта такого размера это может оказаться избыточной инфраструктурой; явное решение принимается в спеке, не по умолчанию.
- `Trace.beginSection` маркеры в продовом коде — это изменение `main`-исходников (не только тестов), требует своей код-ревью-сверки, чтобы не повлиять на поведение (маркеры должны быть no-op вне профилирования).
- Baseline min/max диапазон по 5 прогонам чувствителен к шуму на конкретном устройстве (TECNO LI9) — тот же риск, что уже был учтён (и подтверждён рабочим) в specs 0024/0025.
- 8 файлов расследования могут содержать уже неактуальные (опровергнутые) сценарии — прямое копирование в новую систему без разбора возможно перенесёт устаревшие/misleading тесты.

---

## Not In Scope (explicitly)

- Новые UI-фичи.
- Backend-API.
- Ретроактивный анализ perf-инцидентов вне уже задокументированных в specs 0024/0025/0026.

*Any additions to scope require requestor approval and a revised delivery date.*

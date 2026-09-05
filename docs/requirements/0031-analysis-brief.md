# Analysis Brief

**Project:** Android Gateway App — Milestone 26 остаток: подсветка поиска, распознавание ссылок/телефонов/OTP, множественное выделение сообщений
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-09-04
**Delivery date:** не зафиксирована

---

## The Question

> Доделать 3 оставшихся пункта Milestone 26 одной спекой: подсветка совпадений в поиске, распознавание ссылок/телефонов/OTP-кодов в тексте сообщения с контекстными действиями, Telegram-style множественное выделение сообщений с массовым удалением.

---

## What "Done" Looks Like

1. `SearchResultsList` подсвечивает найденную подстроку внутри текста каждого результата поиска.
2. `MessageBubble` распознаёт ссылки (кликабельны, меню открыть/скопировать), телефонные номера (кликабельны, меню позвонить/написать/скопировать), отдельные 4-6-значные числа (тап копирует немедленно).
3. Long-press по сообщению в `ThreadScreen` включает режим множественного выделения (не открывает "Удалить" напрямую); tap по другим сообщениям в этом режиме добавляет/убирает их из выделения; contextual TopBar показывает счётчик + "Удалить" (массово) + крестик выхода из режима.
4. Полный регресс без perf-пакета зелёный.

---

## Scope

| In scope | Out of scope |
|---|---|
| `ConversationsScreen.kt`/`SearchResultsList` — подсветка | Изменения backend-API |
| `ThreadScreen.kt`/`MessageBubble` — распознавание ссылок/телефонов/OTP, контекстные действия | Контекстная OTP-эвристика (осознанно упрощена) |
| `ThreadScreen.kt`/`ThreadViewModel`/`ThreadUiState` — режим множественного выделения, массовое удаление | Остальные Milestone (27/28) |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Текущий рендер результатов поиска | `ui/conversations/ConversationsScreen.kt` (`SearchResultsList`, строка ~261) | Confirmed — прочитано |
| 2 | Текущий long-press/меню в `MessageBubble` | `ui/thread/ThreadScreen.kt` (`combinedClickable`, строка ~290, `DropdownMenu` "Удалить", строка ~321) | Confirmed — прочитано, подтверждает точное заменяемое поведение |
| 3 | `ThreadUiState`/`ThreadViewModel` — текущая структура состояния, куда добавляется набор выделенных id | `ui/thread/ThreadViewModel.kt` | Не прочитан в этой сессии — обязателен перед спекой |
| 4 | Существующий паттерн `MessageDao`/`MessageRepository` для удаления одного сообщения — для массового удаления | `data/repository/MessageRepository.kt`, `data/local/db/MessageDao.kt` | Не прочитан в этой сессии — обязателен перед спекой (нужно понять, есть ли уже batch-delete или потребуется новый метод) |

---

## Approach (high level)

1. **Подсветка поиска**: чистая функция `buildHighlightedText(text: String, query: String): AnnotatedString`, юнит-тестируемая отдельно от Compose, применяется в `SearchResultsList`.
2. **Распознавание элементов**: чистая функция сегментации текста сообщения на обычные/ссылка/телефон/OTP-сегменты (regex-based), юнит-тестируемая; `MessageBubble` рендерит через `buildAnnotatedString`+кликабельные аннотации, тап на сегмент — контекстное меню (кроме OTP — прямое копирование).
3. **Множественное выделение**: `ThreadUiState` получает `selectedMessageIds: Set<Long>`; `MessageBubble`'s `combinedClickable` — `onLongClick` входит в режим выделения (если ещё не в нём) и выделяет сообщение, `onClick` в режиме выделения переключает выделение конкретного сообщения (вместо no-op, как сейчас); `ThreadScreen`'s `TopAppBar` условно заменяется на contextual TopBar при `selectedMessageIds.isNotEmpty()`.
4. Автотесты: unit на обе чистые функции (highlight, text segmentation), androidTest на режим выделения (long-press → contextual TopBar → tap другого сообщения → массовое удаление → возврат к обычному TopBar), androidTest на клик по ссылке/телефону/OTP (мокнутые Intent/ClipboardManager где нужно).
5. Полный регресс на TECNO LI9 обязателен (общие компоненты `MessageBubble`/`ThreadScreen`, как в предыдущих спеках Milestone 26).

---

## Output Format

**Deliverable:** Спецификация `docs/specs/0031-*.md`, код + тесты в `android_gateway/`, обновлённый `docs/roadmaps/Roadmap 2.md` (Milestone 26).
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- OTP-эвристика (любое отдельное 4-6-значное число) даст ложные срабатывания на время/годы/произвольные числа в тексте — осознанно принято владельцем продукта, не риск для реализации, но стоит явно продемонстрировать на живом примере при верификации.
- Массовое удаление — нужно проверить, есть ли уже batch-операция в `MessageDao`/`MessageRepository`, или добавлять новую (влияет на объём работы).
- Кликабельные сегменты текста внутри уже кликабельного (`combinedClickable`) `MessageBubble` — нужно явно развести жест "тап по сегменту" (открыть меню/скопировать) от "тап по остальной части пузыря" (сейчас no-op, в новой версии — переключение выделения в режиме multi-select) — потенциальная конфликтная зона, требует аккуратной реализации hit-testing внутри `ClickableText`/`AnnotatedString`.

---

## Not In Scope (explicitly)

- Backend-API.
- Контекстная OTP-эвристика.
- Остальные Milestone (27, 28).

*Any additions to scope require requestor approval and a revised delivery date.*

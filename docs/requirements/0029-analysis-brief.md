# Analysis Brief

**Project:** Android Gateway App — SIM-индикатор у сообщения, группировка сообщений по времени, авто-скрытие клавиатуры (Milestone 26, часть 2)
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-09-04
**Delivery date:** не зафиксирована

---

## The Question

> Взять из Milestone 26 backlog первые три оставшихся пункта: SIM-индикатор у сообщения в треде, группировка подряд идущих сообщений одного отправителя по времени (Telegram-style), авто-скрытие клавиатуры при скролле списка сообщений вверх.

---

## What "Done" Looks Like

1. `SmsHistoryImporter` читает `Telephony.Sms.SUBSCRIPTION_ID` и заполняет `simSlot` для новых импортов/синков; уже импортированные строки остаются `simSlot = null` (не ретроактивно).
2. `ThreadScreen`/`MessageBubble` показывает SIM-индикатор рядом с сообщением, если `simSlot != null` и активных SIM ≥ 2 (та же видимость, что у `showSimSelector`).
3. Подряд идущие сообщения одного отправителя/направления с разницей < 5 минут визуально объединены (без повторной шапки/отступа); разрыв ≥ 5 минут или смена отправителя/направления — новая группа.
4. Скролл списка сообщений вверх (в любую сторону от низа) скрывает клавиатуру и снимает фокус с поля ввода.

---

## Scope

| In scope | Out of scope |
|---|---|
| `SmsHistoryImporter.kt` — `HISTORY_PROJECTION` + `Telephony.Sms.SUBSCRIPTION_ID` → `simSlot` (через `SubscriptionManager`/аналог `SimOptionsProvider`) | Ретроактивный повторный импорт истории |
| `ThreadScreen.kt`/`MessageBubble` — SIM-индикатор, группировка сообщений, скрытие клавиатуры при скролле | Подсветка поиска, распознавание ссылок/OTP, Telegram-style выделение сообщений |
| Возможная доработка `MessageDao`/запросов, если группировка требует данных не из текущего маппинга `MessageEntity` | Изменения backend-API |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Текущая схема `MessageEntity` | `data/local/db/MessageEntity.kt` | Confirmed — поле `simSlot: Int?` уже существует, миграция БД не нужна, только заполнение при импорте |
| 2 | Текущий импорт истории | `data/local/SmsHistoryImporter.kt` | Confirmed — `HISTORY_PROJECTION` не содержит `Telephony.Sms.SUBSCRIPTION_ID`, `simSlot` хардкожен в `null` в `toMessageEntity` |
| 3 | Маппинг subscriptionId → slotIndex | `data/local/SimOptionsProvider.kt` (`subscriptionIdForSlot`, обратного метода нет — нужен `SubscriptionManager.getActiveSubscriptionInfo(subId).simSlotIndex` или новый метод в `SimOptionsProvider`) | Confirmed — прямой метод есть, обратный предстоит добавить |
| 4 | Текущая отрисовка сообщений/видимость SIM | `ui/thread/ThreadScreen.kt` (`showSimSelector`, `MessageBubble`) | Confirmed — уже читан и правлен в спеке 0028 |

---

## Approach (high level)

1. `SmsHistoryImporter`: добавить `Telephony.Sms.SUBSCRIPTION_ID` в `HISTORY_PROJECTION`, прочитать колонку в `toMessageEntity`, замапить subscriptionId → slotIndex (новый метод в `SimOptionsProvider`, деградация на `null` если SIM больше не активна/подписка удалена) — применяется и к `importIfNeeded()`, и к `syncNewMessages()` (общая функция).
2. `ThreadScreen`/`MessageBubble`: SIM-индикатор — маленькая иконка/лейбл, видимость управляется тем же условием, что `showSimSelector` (≥2 активных SIM), плюс `message.simSlot != null`.
3. `ThreadUiState`/маппинг сообщений: чистая функция группировки — вход список сообщений (отсортированных по времени), выход — с флагом "первое в группе"/группами; порог 5 минут + совпадение отправителя/направления. Юнит-тестируется отдельно от Compose.
4. Скролл вверх → скрытие клавиатуры: слушать `LazyListState`/scroll delta в `ThreadScreen`, при движении вверх вызывать `LocalSoftwareKeyboardController.current?.hide()` + снятие фокуса.
5. Автотесты: unit-тест на функцию группировки (чистая логика), androidTest на видимость SIM-индикатора (per testTag), androidTest на скрытие клавиатуры/фокуса при скролле; полный регресс на TECNO LI9.

---

## Output Format

**Deliverable:** Спецификация `docs/specs/0029-*.md`, код + тесты в `android_gateway/`, обновлённый `docs/roadmaps/Roadmap 2.md` (Milestone 26).
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- Маппинг subscriptionId → slotIndex для уже неактивной/удалённой SIM может не резолвиться (`SubscriptionManager` не хранит историю) — в этом случае `simSlot` остаётся `null` для той конкретной строки, деградация та же, что и для старых сообщений; не блокирует остальной импорт.
- Группировка сообщений — чистая функция, отделённая от Compose-рендеринга, чтобы не завязывать бизнес-правило на UI-тесты (юнит-тестируема напрямую).
- Скрытие клавиатуры по скроллу — риск ложных срабатываний на мелкий дрожащий скролл; порог/дебounce уточняется на этапе реализации, если live-проверка на TECNO покажет проблему (не зафиксировано как отдельное требование, т.к. явно не поднято владельцем продукта).

---

## Not In Scope (explicitly)

- Ретроактивный импорт `simSlot` для старых сообщений.
- Подсветка совпадений в поиске, распознавание ссылок/телефонов/OTP, Telegram-style множественное выделение — остаются в Milestone 26 backlog.
- Backend-API.

*Any additions to scope require requestor approval and a revised delivery date.*

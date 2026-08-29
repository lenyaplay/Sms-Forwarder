# Analysis Brief

*Производный от [0019-0021-requirements-doc.md](0019-0021-requirements-doc.md) после одобрения владельцем продукта.*

---

**Project:** Android Gateway App — уведомления/навигация, производительность, сброс настроек доставки
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-08-29
**Delivery date:** не зафиксирована

---

## The Question

> Как убрать неверный переход по уведомлению, заметную задержку при открытии длинной переписки/холодном старте, и добавить быстрый сброс настроек доставки без риска задеть правила фильтрации?

---

## What "Done" Looks Like

1. Три спеки написаны ([0019](../specs/0019-gateway-notification-deep-link-and-expanded-text.md), [0020](../specs/0020-gateway-thread-and-startup-performance.md), [0021](../specs/0021-gateway-reset-delivery-settings.md)) с допущениями, критериями приёмки и планом тестов.
2. `docs/Roadmap.md` обновлён — Milestone 17 (было: одна общая заявка) разбит на 3 отдельных Milestone, старый Milestone 17 (delete/contact-name/export) сдвинут на 18 без потери содержимого.
3. Причина медленного холодного старта в спеке 0020 подтверждена чтением реального кода, не гаданием.

---

## Scope

| In scope | Out of scope |
|---|---|
| Deep-link из уведомления в конкретный тред + `BigTextStyle` | Правила фильтрации (не трогает кнопка сброса) |
| Прокрутка треда без анимации через всю историю (открытие + переход из поиска) | Milestone 18 (delete/contact-name/export) |
| Устранение подтверждённой причины медленного старта списка диалогов | Полная пагинация/виртуализация сверх найденной причины |
| Кнопка сброса serverUrl/uploadToken/retry-настроек/паузы | Backend/Viewer App |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | `IncomingSmsNotifier`/`MainActivity`/`NavGraph` deep-link инфраструктура | Прочитан код | Confirmed — инфраструктура уже есть, не подключена к `notifyIncoming` |
| 2 | `ThreadScreen` механизм скролла | Прочитан код | Confirmed — `animateScrollToItem` на каждое изменение размера списка |
| 3 | `ConversationsViewModel`/`ContactNameResolver`/`MessageDao.observeConversations` | Прочитан код | Confirmed — резолвинг контактов без кеша на каждую эмиссию, задокументированное known limitation Milestone 12 |
| 4 | `DeliveryViewModel`/`GatewayConfigStore` текущие поля и дефолты | Прочитан код | Confirmed |

---

## Approach (high level)

1. Спека 0019: `notifyIncoming` получает `sender` в `Intent.putExtra(EXTRA_OPEN_SENDER, sender)`, `BigTextStyle` для текста.
2. Спека 0020: `ThreadScreen` — `scrollToItem` (без анимации) при первом открытии/переходе к найденному сообщению, `animateScrollToItem` оставить только для уже открытого экрана при новом входящем сообщении; `ConversationsViewModel`/`ContactNameResolver` — кеш резолвинга по номеру (не пере-резолвить на каждую эмиссию), индекс Room на `messages(sender, createdAt)` для `observeConversations`.
3. Спека 0021: `GatewayConfigStore.resetDeliverySettings()`, кнопка+подтверждение на `DeliveryScreen`.
4. Каждая — свой цикл: `query-validation` (если есть новый SQL/индекс), реализация, тесты в той же работе, `peer-review-template`, `analysis-qa-checklist`, статус спеки → `Implemented`, запись в `Roadmap.md`.

---

## Output Format

**Deliverable:** 3 файла `docs/specs/00{19,20,21}-*.md` + правки `docs/Roadmap.md`.
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- Спека 0020 (навигация к сообщению из поиска) требует пробросить конкретный `messageId`/позицию через навигацию в `ThreadScreen`, которого сейчас нет (сейчас переход в тред идёт только по `sender`) — новый параметр маршрута, отражено в архитектуре спеки.
- Индекс на `messages(sender, createdAt)` — новая Room-миграция, требует `query-validation` перед применением (как в Milestone 16 для `delivery_log`).

---

## Not In Scope (explicitly)

- Milestone 18 (delete-after-forward, contact-name-in-payload, export/import).
- Правила фильтрации.
- Изменения backend-API.

*Любые дополнения к объёму требуют одобрения владельца продукта и пересмотра этого документа.*

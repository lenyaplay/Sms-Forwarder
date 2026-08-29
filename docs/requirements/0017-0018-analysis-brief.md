# Analysis Brief — Android Gateway App: Milestone 16 и 17

*Производный документ от `0017-0018-requirements-doc.md` после (ретроактивного) согласования. Рабочий reference для перехода к спекам/реализации.*

---

**Project:** Milestone 16 (надёжность/управление доставкой) + Milestone 17 (данные/приватность)
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-08-29
**Delivery date:** не зафиксирована

---

## The Question

> Какие из оставшихся issues референсного проекта делаем, и как именно каждая фича должна работать поверх уже существующей архитектуры Gateway App (retry/bulk-resend из 0016, фильтрация из 0015, поиск/архив/удаление из 0014)?

---

## What "Done" Looks Like

1. Обе спеки (`0017`, `0018`) написаны в формате проекта, с конкретными диапазонами/дефолтами/триггерами по каждому пункту — не абстрактные описания.
2. Каждый пункт явно привязан к issue референсного проекта и к решению владельца продукта, принятому в интервью.
3. `docs/Roadmap.md` содержит Milestone 16/17 со статусом `[ ]` (не начато), готовые к запуску полного цикла реализации (`query-validation` → код → тесты → `peer-review-template` → `analysis-qa-checklist`).

---

## Scope

| In scope | Out of scope |
|---|---|
| Пауза форвардинга, test-connection, notification-on-result, лог доставки (M16) | `targetSdk` 35 (issue #93) — не выбран |
| Delete-after-forward + системное удаление `content://sms` (расширяет и ручное удаление), имя контакта в payload (скрыто по умолчанию), экспорт/импорт настроек (M17) | «Archive after forward» — рассмотрен, отклонён (не подходит гранулярность архива) |
| Изменение проводного контракта `WebhookPayload` (новое опциональное поле `contactName`) | Экспорт/импорт новых toggle из 0017 и лога доставки |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Список открытых/закрытых issues референсного проекта, уже отфильтрованный от закрытого в M14/M15 | Предыдущая сессия, 4 параллельных агента | Confirmed |
| 2 | Точные текущие сигнатуры `GatewayConfigStore`, `MessageRepository`, `WebhookRequestWorker`, `MessageDao`, `ConfirmDialog`, `IncomingSmsNotifier`, `ContactNameResolver`, `WebhookPayload`, `NavGraph`, `DeliveryScreen`-стек, `AndroidManifest.xml`, `GatewayDatabase` | Текущий код `android_gateway/` | Confirmed (Explore-агент, эта сессия) |
| 3 | Явные решения владельца продукта по каждому пункту (диапазоны, дефолты, триггеры, объём delete/export) | `AskUserQuestion`, 6 раундов, 2026-08-29 | Confirmed |

---

## Approach (high level)

1. Сгруппировать оставшиеся 7 issues по теме в два Milestone (надёжность/управление vs данные/приватность) — подтверждено владельцем продукта.
2. Для каждого спорного пункта (test-connection semantics, contact-name privacy default, delete scope, export scope) — отдельный раунд `AskUserQuestion`, не предполагать дизайн самостоятельно.
3. Прочитать точный текущий код перед фиксацией архитектуры каждой фичи (не полагаться на память из более ранней сессии — код мог измениться).
4. Написать спеки `0017`/`0018` в установленном формате проекта (Контекст → Допущения → Функциональность → Архитектура → Критерии приёмки → Тесты → Открытые вопросы), со статусом `Draft`.
5. Добавить Milestone 16/17 в `docs/Roadmap.md` со ссылками на спеки, статус `[ ]`.

---

## Output Format

**Deliverable:** `docs/specs/0017-gateway-delivery-reliability-and-visibility.md`, `docs/specs/0018-gateway-delete-contact-privacy-export.md`, обновлённый `docs/Roadmap.md`
**Audience:** Владелец продукта / сам процесс реализации в следующих сессиях
**Delivery channel:** Файлы в репозитории (не отправка вовне)

---

## Constraints and Risks

- Сопоставление `MessageEntity` со строкой `content://sms` (`systemSmsId`) для точного системного удаления — не гарантированно уникально при коллизии `(sender, timestamp)` в пределах секунды; принято как риск личного масштаба, не блокирует Draft.
- Реальный `POST` в test-connection создаёт видимое тестовое сообщение в Viewer App — сознательно принятый побочный эффект, не скрытый баг.
- Изменение поведения уже существующей ручной фичи удаления (теперь чистит и `content://sms`) — это не чисто аддитивное изменение, а модификация принятого ранее поведения (Milestone 12, этап 5); явно согласовано с владельцем продукта в этой сессии, задокументировано в спеке 0018 (допущение 2).

---

## Not In Scope (explicitly)

- `targetSdk`/`compileSdk` 35
- «Archive after forward»
- Экспорт/импорт переключателей паузы/скрытия имени контакта и лога доставки

*Любое расширение объёма требует повторного согласования с владельцем продукта и пересмотра спек 0017/0018.*

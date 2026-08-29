# Requirements Doc — Android Gateway App: остаток issues референсного проекта

*Заполнено задним числом по итогам интервью через `stakeholder-requirements-gathering`, проведённого сериями `AskUserQuestion` вместо `assets/interview_guide.md`. Покрывает обе спеки, т.к. интервью проводилось одним циклом на весь оставшийся список issues.*

**Project / request name:** Оставшиеся актуальные issues референсного проекта (после Milestone 14/15) — надёжность/управление доставкой + данные/приватность
**Requestor:** Владелец продукта (текущий пользователь)
**Primary analyst:** Claude Code
**Date created:** 2026-08-29
**Target delivery date:** не зафиксирован (не срочно)
**Status:** Approved (неявный sign-off через прямые ответы на `AskUserQuestion`, не через подпись документа — см. «Sign-off»)

---

## Business Question

Какие из оставшихся открытых issues референсного проекта bogkonstantin/android_income_sms_gateway_webhook стоит реализовать в Android Gateway App, и как именно должна работать каждая фича с учётом уже принятых архитектурных решений (Milestone 12–15)?

---

## Decision This Informs

**Decision type:** Operational — расширение уже работающего продукта известными, некритичными фичами; не меняет стратегическое направление, обратимо (каждая фича — отдельный toggle/экран).
**Decider:** Владелец продукта.
**Decision deadline:** нет.
**What happens if this analysis недоступен:** Roadmap остаётся неполным, разработка следующих Milestone откладывается без ясного объёма.

---

## Success Criteria

1. Список issues разделён на «делаем» / «явно откладываем» с обоснованием по каждому.
2. Для каждого «делаем»-пункта — согласован конкретный дизайн (не только «да, делаем», а как именно: диапазоны, дефолты, триггеры, UI-размещение).
3. Результат оформлен как спеки в `docs/specs/` (формат проекта, не шаблон скила) + пункты в `docs/Roadmap.md` — то есть готов к передаче в реализацию по процессу `docs/Development.md`.

---

## Scope

**In scope:**
- Пауза форвардинга (issue #59)
- Кнопка «проверить соединение» (issue #58/#56)
- Уведомление о результате пересылки (issue #91)
- Лог доставки в приложении (issue #68)
- Delete-after-forward + расширение существующего ручного удаления на системное `content://sms` (issue #102/#57)
- Имя контакта в webhook payload, скрыто по умолчанию (issue #61/#43)
- Экспорт/импорт настроек: server URL, token, retry, правила фильтрации (issue #76)

**Out of scope:**
- `targetSdk`/`compileSdk` 35 (issue #93) — не выбран владельцем продукта, не блокирует ничего (не публикуемся в Play)
- «Archive after forward» — рассмотрен и явно отклонён (архив сейчас на уровне диалога, не сообщения — не подходит по гранулярности)
- Экспорт/импорт новых переключателей из 0017 (пауза, скрытие имени) и лога доставки — не входит, отмечено как backlog в 0018
- Полное удаление из системного хранилища через `WRITE_SMS`-подобный отдельный флаг — не рассматривалось, используется существующая роль default SMS app

---

## Data Sources

| Source | Table / system | Availability confirmed? |
|---|---|---|
| Issues референсного проекта bogkonstantin/android_income_sms_gateway_webhook | GitHub issues (открытые+закрытые) | Yes — проанализировано в предыдущей сессии 4 параллельными агентами |
| Текущий код Gateway App | `android_gateway/app/src/main/java/...` | Yes — прочитан этой сессией (`GatewayConfigStore`, `MessageRepository`, `WebhookRequestWorker`, `MessageDao`, `ConfirmDialog`, `IncomingSmsNotifier`, `ContactNameResolver`, `WebhookPayload`/`Mapper`, `NavGraph`, `DeliveryScreen`/`ViewModel`/`UiState`, `AndroidManifest.xml`, `GatewayDatabase`) |

**Known data quality issues:** нет данных вне кодовой базы; сопоставление `MessageEntity` ↔ строка `content://sms` (нужное для системного удаления) сейчас не хранится нигде — новый риск, зафиксирован в спеке 0018 как открытый вопрос.

---

## Output Format

**Format:** Спецификации в `docs/specs/` (формат проекта, см. `CLAUDE.md`/`docs/Development.md`) + пункты в `docs/Roadmap.md`
**Delivery channel:** файлы в репозитории
**Audience for the output:** сам владелец продукта, как reference при последующей реализации через мандатные скилы
**Level of detail required:** Full technical — уровень детализации, аналогичный уже принятым спекам 0014–0016 (архитектура, критерии приёмки, план тестов)

---

## Assumptions and Constraints

- Приложение уже держит роль `android.app.role.SMS` (default SMS app) — даёт право на запись/удаление в `content://sms`, без чего пункт про системное удаление был бы невозможен.
- Личный масштаб использования (сотни–тысячи записей) — те же допущения по производительности/индексам, что в 0014/0015/0016, переносятся без пересмотра.
- Изменение формата `WebhookPayload` (поле `contactName`) должно быть строго аддитивным и не ломать существующий backend/Viewer App — уже сформулированное правило проекта (см. 0003-sms-webhook.md).

---

## Open Questions

| # | Question | Owner | Status |
|---|---|---|---|
| 1 | Test-connection: реальный POST с тестовым payload или только ping доступности сервера? | Владелец продукта | Resolved — реальный POST, задокументированное поведение (сообщение попадёт в Viewer App) |
| 2 | Имя контакта в payload — делать вообще, учитывая, что референсный проект отклонил это по privacy? | Владелец продукта | Resolved — делаем, но скрыто по умолчанию, явный opt-in переключателем |
| 3 | Delete-after-forward — только локально (Room) или и из системного хранилища? | Владелец продукта | Resolved — и из системного хранилища (`content://sms`), причём это же поведение распространяется и на уже существующее ручное удаление |
| 4 | Экспорт/импорт — что входит в объём? | Владелец продукта | Resolved — server URL + token + retry + правила фильтрации |
| 5 | Сопоставление `MessageEntity` со строкой `content://sms` при бэкфилле по `(sender, timestamp)` — гарантированно уникально? | — | Open — не гарантировано при коллизии секунды, принято как известный риск личного масштаба, не блокирует |

---

## Sign-off

**Requestor confirms this document accurately describes the requirement:**

Не подписан формально — подтверждение получено как серия явных ответов на структурированные вопросы (`AskUserQuestion`, 6 раундов) в диалоге 2026-08-29, каждый пункт списка выше явно выбран/уточнён владельцем продукта до перехода к написанию спек. Задним числом восстановлено для полноты процесса скила, реального дополнительного review в этот проход не запрашивалось.

**Analyst confirms feasibility given current constraints:**

Confirmed — оба спека (0017, 0018) написаны на основе прочитанного текущего кода, без предположений о несуществующих API; открытые архитектурные риски (сопоставление `systemSmsId`) явно вынесены как backlog, не скрыты.

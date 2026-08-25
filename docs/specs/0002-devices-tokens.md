# 0002 — Устройства и токены

**Статус:** Draft

## Контекст

Milestone 2 (`docs/Roadmap.md`). До сих пор в системе есть только пользователи и авторизация (`0001-auth.md`). Эта фича вводит устройства-шлюзы (`devices`), их `upload_token` (приём SMS от Gateway App) и модель раздачи доступа просматривающим через `download_token` + `viewer_bindings`. Без неё Milestone 3 (приём webhook) и Milestone 4 (REST для просмотра) не имеют к чему привязываться.

## Допущения и решения (assumptions log)

Зафиксировано в интервью с владельцем продукта перед написанием этой спеки — при изменении любого пункта спека требует пересмотра:

1. **Модель download_token — "инвайт-ссылка", не пара (device, viewer)."** У устройства может быть **несколько одновременно активных `download_token`** — каждый независимый "канал приглашения" (например, один для семьи, другой для коллег). Любой аутентифицированный пользователь, знающий конкретный токен, может сам добавить себе доступ (`POST /devices/bindings`). Это отличается от исходной формулировки в `docs/Architecture.md` §4 ("привязан к паре Device–подписчик") — архитектурный документ будет обновлён по итогам этой спеки.
2. **Каждый download_token многоразовый.** Им может воспользоваться любое число пользователей, пока именно он не отозван.
3. **Ревокация одного download_token рвёт только те `viewer_bindings`, что созданы через него.** Остальные токены устройства и их viewer'ы не затрагиваются — это точечный групповой отзыв ("отозвать доступ, выданный коллегам, не трогая семью"). Точечного отзыва одного конкретного viewer'а внутри одного токена в этой версии нет (осознанное ограничение MVP, не забыто — см. Backlog ниже).
4. **TTL токенов задаёт пользователь при создании/перевыпуске**, а не фиксированная константа — опциональное поле `ttl_seconds` в запросе; если не передано — токен бессрочный (до явной ревокации).
5. **Лимитов на количество устройств у пользователя и viewer_bindings у устройства нет** (MVP, backlog на будущее).
6. **DELETE устройства — безвозвратный, каскадный.** Удаляются `messages` и `viewer_bindings` этого устройства без возможности восстановления (обоснование: сообщения физически восстановимы повторным приёмом на телефоне-источнике, ценности в soft-delete на MVP нет).
7. Список устройств (`GET /devices`) в этой спеке отдаёт **и свои (owned), и те, к которым есть viewer_binding** — само разделение по ролям и фильтрация нужны Android-приложению с Milestone 6, но эндпоинт логично сделать сразу полным, а не переделывать в Milestone 4.

## API / интерфейс

Все запросы/ответы — `application/json`. Все эндпоинты, кроме отдельно оговорённых, требуют `Authorization: Bearer <access_token>`.

### `POST /devices`

Создаёт устройство и сразу выпускает `upload_token`. Только для аутентифицированного пользователя (становится `owner_user_id`).

Запрос: `{"name": "string", "upload_token_ttl_seconds": 3600}` — `name` обязателен, непустой; `upload_token_ttl_seconds` опционален (int > 0), при отсутствии — токен бессрочный.

Ответ `201 Created`:
```json
{"id": 1, "name": "...", "upload_token": "...", "upload_token_expires_at": "2026-08-25T12:00:00Z", "created_at": "..."}
```
`upload_token_expires_at` — `null`, если TTL не задан.

Ошибки: `400` — `name` пустой или `upload_token_ttl_seconds` невалиден (не число / ≤ 0).

### `GET /devices`

Список устройств текущего пользователя: свои (`owner_user_id == user_id`) + те, где есть его `viewer_binding`. Для чужих (viewer) устройств `upload_token` и `hmac_secret` в ответе не отдаются.

Ответ `200 OK`:
```json
{"devices": [
  {"id": 1, "name": "...", "role": "owner", "upload_token": "...", "upload_token_expires_at": null, "created_at": "..."},
  {"id": 2, "name": "...", "role": "viewer", "created_at": "..."}
]}
```

### `GET /devices/{id}`

Детали одного устройства. Доступно владельцу или пользователю с `viewer_binding` на это устройство.

Ответ `200 OK` — та же форма, что элемент списка выше (владельцу — с `upload_token`, viewer'у — без).
Ошибки: `404` — устройства нет, либо у пользователя нет к нему отношения (не палим существование чужого устройства).

### `PATCH /devices/{id}`

Переименование устройства. Только владелец.

Запрос: `{"name": "string"}`

Ответ `200 OK`: обновлённый объект устройства.
Ошибки: `400` — пустое имя; `403` — не владелец; `404` — не найдено.

### `DELETE /devices/{id}`

Удаляет устройство, каскадно — все его `messages` и `viewer_bindings` (см. допущение 6). Только владелец. Идемпотентно: повторный вызов на уже удалённое — `404`.

Ответ `204 No Content`.
Ошибки: `403` — не владелец; `404` — не найдено.

### `POST /devices/{id}/upload_token`

Перевыпуск `upload_token` устройства. Старый токен немедленно становится недействителен для приёма webhook. Только владелец.

Запрос: `{"ttl_seconds": 3600}` (опционально, как при создании).

Ответ `200 OK`: `{"upload_token": "...", "upload_token_expires_at": "..." | null}`.
Ошибки: `400` — невалидный `ttl_seconds`; `403` — не владелец; `404` — не найдено.

### `POST /devices/{id}/download_tokens`

Выпуск **нового** `download_token` устройства (дополнительно к уже существующим, если есть — не заменяет их). Только владелец.

Запрос: `{"label": "string", "ttl_seconds": 3600}` — `label` опционален (для удобства владельца отличать токены в списке, например "коллеги"), `ttl_seconds` опционален (иначе бессрочный).

Ответ `201 Created`:
```json
{"id": 5, "download_token": "...", "label": "...", "download_token_expires_at": "..." | null, "created_at": "..."}
```

Ошибки: `400` — невалидный `ttl_seconds`; `403` — не владелец; `404` — устройство не найдено.

### `GET /devices/{id}/download_tokens`

Список активных (не отозванных, не просроченных) `download_token` устройства с числом подключённых по каждому. Только владелец.

Ответ `200 OK`: `{"tokens": [{"id": 5, "download_token": "...", "label": "...", "download_token_expires_at": null, "bindings_count": 3, "created_at": "..."}]}`

### `DELETE /devices/{id}/download_tokens/{token_id}`

Отзыв конкретного `download_token`. **Удаляет только `viewer_bindings`, созданные через этот конкретный токен** (см. допущение 3) — необратимо. Остальные токены и их viewer'ы не затрагиваются. Только владелец.

Ответ `200 OK`: `{"revoked_bindings_count": 2}`.
Ошибки: `403` — не владелец; `404` — устройство или токен не найдены (в т.ч. если `token_id` принадлежит другому устройству).

### `POST /devices/bindings`

Подключение текущего пользователя к устройству по `download_token` — создаёт `viewer_binding`. Любой аутентифицированный пользователь.

Запрос: `{"download_token": "string"}`

Ответ `201 Created`: `{"device_id": 1, "device_name": "..."}`
Ошибки: `400` — поле отсутствует; `401` — токен не найден, истёк или отозван; `409` — у пользователя уже есть binding на это устройство (включая случай, когда он же владелец — сам себе viewer не нужен).

## Модель данных

Изменения к схеме из `0001_init.up.sql` (новая миграция `0002_devices_tokens.up.sql`):

- `devices`: добавить `upload_token_expires_at TIMESTAMP` (nullable).
- Новая таблица `device_download_tokens` — устройство может иметь несколько активных токенов одновременно:
  ```sql
  CREATE TABLE device_download_tokens (
      id         INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id  INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
      token      TEXT NOT NULL UNIQUE,
      label      TEXT,
      expires_at TIMESTAMP,
      revoked_at TIMESTAMP,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
  ```
- `viewer_bindings`: колонка `download_token` заменяется на `download_token_id INTEGER NOT NULL REFERENCES device_download_tokens (id) ON DELETE CASCADE` — привязка к конкретному токену, через который создан binding (нужно для точечного группового отзыва, см. допущение 3). Оставить `UNIQUE(device_id, user_id)`.

Итоговые поля:
- `devices(id, owner_user_id, name, upload_token, upload_token_expires_at, hmac_secret, created_at)`
- `device_download_tokens(id, device_id, token, label, expires_at, revoked_at, created_at)`
- `viewer_bindings(id, device_id, user_id, download_token_id, created_at)`

Отзыв токена (`DELETE .../download_tokens/{token_id}`) — `revoked_at = now()` на записи `device_download_tokens` + `DELETE FROM viewer_bindings WHERE download_token_id = ?`. Токен с `revoked_at` не подходит для `POST /devices/bindings`.

## Сценарии использования

1. Пользователь создаёт устройство → получает `upload_token` (с TTL или без) → настраивает Gateway App с этим токеном (вне системы).
2. Владелец выпускает `download_token` устройства (с меткой и/или TTL или без них) → передаёт его вручную (или через QR — Android, Milestone 6) человеку/группе, которым хочет дать доступ. Может выпустить ещё один отдельный токен для другой группы людей — оба действуют параллельно.
3. Получатель вызывает `POST /devices/bindings` с этим токеном → появляется в списке подключённых, видит устройство в `GET /devices`.
4. Тот же `download_token` передаётся ещё нескольким людям → каждый создаёт свой `viewer_binding` через тот же токен.
5. Владелец решает отозвать доступ, выданный конкретной группе (например, токен "коллеги") → `DELETE /devices/{id}/download_tokens/{token_id}` → все viewer'ы, подключённые именно через этот токен, теряют доступ; те, кто подключился через другой токен (например "семья"), не затронуты.
6. `upload_token` скомпрометирован (утёк) → владелец перевыпускает его → старый переставая приниматься на webhook-эндпоинте (реализация приёма — Milestone 3, здесь только генерация/ревокация).
7. Владелец удаляет устройство → все сообщения и подключения удаляются безвозвратно.

## Критерии приёмки

- `POST /devices` создаёт устройство с уникальным `upload_token`; без `upload_token_ttl_seconds` → `upload_token_expires_at` = `null`; с ним → корректно вычисленная дата истечения.
- `GET /devices` возвращает и owned, и viewer-устройства текущего пользователя; чужие недоступные устройства не попадают в список; `upload_token`/`hmac_secret` не отдаются для viewer-записей.
- `GET /devices/{id}` — `404` для пользователя без отношения к устройству (не `403`, чтобы не палить существование).
- `PATCH /devices/{id}` — переименование доступно только владельцу, `403` для остальных (включая viewer'ов).
- `DELETE /devices/{id}` — каскадно удаляет `messages` и `viewer_bindings`; повторный вызов → `404`; только владелец.
- `POST /devices/{id}/upload_token` — генерирует новый уникальный `upload_token`, старый значение более не совпадает ни с одной записью; только владелец.
- `POST /devices/{id}/download_tokens` — можно выпустить несколько токенов на одно устройство, каждый уникален; ранее выпущенные токены и их `viewer_bindings` не затрагиваются.
- `GET /devices/{id}/download_tokens` — отдаёт только активные (не отозванные, не просроченные) токены с корректным `bindings_count` по каждому.
- `DELETE /devices/{id}/download_tokens/{token_id}` — удаляет `viewer_bindings` только этого токена (`revoked_bindings_count` соответствует фактически удалённому числу); `viewer_bindings`, созданные через другой токен того же устройства, остаются нетронутыми; сам токен становится непригоден для `POST /devices/bindings` (`401`); `404` для `token_id`, принадлежащего другому устройству.
- `POST /devices/bindings` с валидным непросроченным неотозванным токеном → `201`, создаётся ровно одна запись `viewer_bindings(device_id, user_id, download_token_id)`; с истёкшим/отозванным/неизвестным токеном → `401`; повторный вызов тем же пользователем **любым** токеном этого устройства, если binding уже существует → `409`; владелец не может добавить себе viewer_binding на собственное устройство → `409`.
- TTL: истёкший `upload_token`/`download_token` не проходит валидацию (потребуется для Milestone 3 и для `/devices/bindings` уже сейчас).
- Пароли/токены не логируются в открытом виде (наследуется правило из `0001-auth.md`, актуально и для upload/download токенов).

## Тесты

- `internal/storage`: `CreateDevice`, `GetDeviceByID`, `ListDevicesForUser` (owned + viewer, включая пустой список), `UpdateDeviceName`, `DeleteDevice` (каскад messages/viewer_bindings/device_download_tokens — проверить реальными вставками), `ReissueUploadToken`, `CreateDownloadToken` (несколько на одно устройство), `ListActiveDownloadTokens` (с `bindings_count`), `RevokeDownloadToken` (удаляет только bindings этого `token_id`, соседний токен не затронут), `CreateViewerBinding` + конфликт уникальности `(device_id, user_id)`, `GetActiveDownloadTokenByValue` (валидный/истёкший/отозванный/отсутствующий).
- `internal/services`: `DeviceService` — создание с TTL и без, переименование (успех/не владелец), удаление (успех/не владелец/повторное), перевыпуск upload_token, выпуск нескольких download_token и точечный отзыв одного из них (проверить, что второй токен и его viewer'ы не пострадали), добавление binding по токену (успех/просрочен/отозван/неизвестен/дубликат/владелец-сам-себе).
- `internal/handlers`: HTTP end-to-end через `httptest` на все эндпоинты и коды ошибок из раздела «Критерии приёмки», включая проверку, что viewer не видит `upload_token`/`hmac_secret` чужого устройства.

## Открытые вопросы / Backlog (не блокируют Draft → Implemented)

- Точечный отзыв одного viewer'а без разрушения токена для остальных (упомянуто как осознанно отложенное в допущении 3).
- Приглашение по логину конкретного пользователя (вариант "б" из интервью) — не выбран для MVP, но может быть добавлен позже как альтернативный способ создания `viewer_binding`.

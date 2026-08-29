# 0021 — Кнопка сброса настроек доставки (Android Gateway App)

**Статус:** Draft

*Требования собраны через `stakeholder-requirements-gathering`, задокументированы в [docs/requirements/0019-0021-requirements-doc.md](../requirements/0019-0021-requirements-doc.md) и [docs/requirements/0019-0021-analysis-brief.md](../requirements/0019-0021-analysis-brief.md) — эта спека их источник истины по деталям реализации.*

## Контекст

Экран «Доставка» ([0016](0016-gateway-retry-config-and-bulk-resend.md), [0017](0017-gateway-delivery-reliability-and-visibility.md)) хранит `serverUrl`, `uploadToken`, retry-настройки (`maxAttempts`/`baseIntervalSeconds`/`backoffPolicy`) и паузу форвардинга. Сейчас единственный способ их сбросить — вручную очистить/перезаполнить каждое поле. Запрошено напрямую владельцем продукта (2026-08-29): кнопка одного нажатия для сброса именно настроек доставки, явно не затрагивающая правила фильтрации ([0015](0015-gateway-sms-filtering.md)) — они хранятся отдельно в `GatewayConfigStore` (`KEY_RECEPTION_FILTER_MODE`/`KEY_FORWARDING_FILTER_MODE` + отдельная таблица `FilterRuleEntity`) и не должны исчезать вместе с адресом сервера.

## Допущения и решения

1. **Точный список очищаемых полей** (подтверждено владельцем продукта): `serverUrl` → `null`, `uploadToken` → `null`, `maxAttempts`/`baseIntervalSeconds`/`backoffPolicy` → дефолты (`10`/`30`/`EXPONENTIAL` — те же значения, что уже являются дефолтами `GatewayConfigStore.retryMaxAttempts()`/`retryBaseIntervalSeconds()`/`retryBackoffPolicy()` при отсутствующем ключе), пауза форвардинга → `false`.
2. **Не трогает**: правила фильтрации (`FilterRuleEntity`, `filterMode` для обеих стадий), историю сообщений (`messages`, `delivery_log`, `conversation_meta`), `isHistoryImported`/`lastSyncedSmsRowId` (служебные флаги импорта — сброс не должен провоцировать повторный полный импорт истории).
3. **Подтверждение обязательно** — необратимое действие (адрес сервера и токен исчезают, форвардинг перестанет работать до повторной настройки), используется уже существующий `ConfirmDialog` (как в удалении диалога/сообщения, Milestone 12 этап 5), не отдельный компонент.
4. **После сброса** — экран «Доставка» остаётся открытым, поля показывают пустые/дефолтные значения (не требует ручного возврата к списку диалогов); уже накопленные `PENDING`/`FAILED`-сообщения не удаляются и не меняют статус — они просто не будут доставлены, пока настройки не заданы заново (симметрично уже существующему поведению при первом запуске без настроенного сервера).

## Функциональность

- `GatewayConfigStore.resetDeliverySettings()` — новый метод, одним вызовом `prefs.edit()` удаляет `KEY_SERVER_URL`, `KEY_UPLOAD_TOKEN`, `KEY_RETRY_MAX_ATTEMPTS`, `KEY_RETRY_BASE_INTERVAL_SECONDS`, `KEY_RETRY_BACKOFF_POLICY`, `KEY_FORWARDING_PAUSED` (через `.remove(...)` на каждый ключ, не `.clear()` — `.clear()` задел бы фильтры/импорт-флаги, хранящиеся в том же `SharedPreferences`-файле `sms_forwarder_gateway_config`).
- Кнопка «Сбросить настройки доставки» на экране «Доставка» (`DeliveryScreen.kt`), стилизована как деструктивное действие (`MaterialTheme.colorScheme.error`, по аналогии с кнопкой «Повторить» на сообщении с ошибкой из Milestone 12 UI/UX-этапа), с `ConfirmDialog` перед выполнением.
- `DeliveryViewModel.onResetDeliverySettings()`: вызывает `configStore.resetDeliverySettings()`, затем перечитывает состояние экрана из `configStore` заново (эквивалентно тому, что делает конструктор `DeliveryViewModel` при инициализации `_uiState`) — не полагается на клиентский in-memory сброс полей, чтобы не разойтись с реально сохранённым состоянием.

## Архитектура

- `data/local/GatewayConfigStore.kt` — новый `open fun resetDeliverySettings()`.
- `ui/delivery/DeliveryViewModel.kt`/`DeliveryActions` — новый `onResetDeliverySettings()`, новое приватное `refreshFromStore()` (переиспользуется и в `init`, и после сброса — небольшой рефакторинг существующей инициализации `_uiState`, не меняющий её текущее поведение).
- `ui/delivery/DeliveryScreen.kt` — новая кнопка + `ConfirmDialog`, новый `DeliveryTestTags.RESET_BUTTON`/`RESET_CONFIRM_DIALOG`.
- Backend/Viewer App/правила фильтрации не затронуты.

## Критерии приёмки

- Нажатие «Сбросить настройки доставки» требует подтверждения в диалоге; отмена ничего не меняет.
- После подтверждения: поля `serverUrl`/`uploadToken` на экране пустые, `maxAttempts`=10, `baseIntervalSeconds`=30, `backoffPolicy`=EXPONENTIAL, переключатель паузы выключен — все значения реально сохранены в `GatewayConfigStore` (проверяется прямым чтением после сброса, не только UI-состоянием).
- Правила фильтрации (список `FilterRuleEntity`, режим blacklist/whitelist на обеих стадиях) не изменяются сбросом — проверяется прямым запросом к `FilterRuleDao`/`filterMode()` до и после.
- `isHistoryImported()`/`lastSyncedSmsRowId()` не изменяются сбросом (полный повторный импорт истории `content://sms` не запускается).
- Уже сохранённые сообщения (`messages`, `delivery_log`, `conversation_meta`) не удаляются сбросом.

## Тесты

- Unit: `GatewayConfigStoreTest.resetDeliverySettingsClearsOnlyDeliveryKeysNotFilterOrImportKeys` — задать все ключи (delivery + filter + import), вызвать `resetDeliverySettings()`, проверить: delivery-ключи вернулись к дефолтам, filter/import-ключи не изменились.
- Инструментированный (Compose): `DeliveryScreenTest` — кнопка сброса показывает `ConfirmDialog`; подтверждение очищает поля на экране и вызывает `configStore.resetDeliverySettings()` (verify на моке); отмена не вызывает сброс.
- Инструментированный: сквозной тест (аналог существующих `MessageRepositoryTest`/`GatewayDatabase`-тестов) — после сброса записи в `messages`/`delivery_log`/`FilterRuleEntity` в реальной (in-memory Room) БД остаются нетронутыми.
- Ручная проверка на физическом устройстве: сбросить настройки доставки с реально настроенным сервером и активными правилами фильтрации — убедиться, что форвардинг перестал работать (поля пустые), а фильтрация SMS продолжает работать по прежним правилам.

## Открытые вопросы / Backlog

- Нет — объём фичи полностью закрыт одним новым методом `GatewayConfigStore` и одной кнопкой на существующем экране.

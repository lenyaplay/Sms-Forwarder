# 0017 — Пауза форвардинга, проверка соединения, уведомления о результате, лог доставки (Android Gateway App)

**Статус:** Implemented

*Требования собраны через `stakeholder-requirements-gathering`, задокументированы в [docs/requirements/0017-0018-requirements-doc.md](../requirements/0017-0018-requirements-doc.md) и [docs/requirements/0017-0018-analysis-brief.md](../requirements/0017-0018-analysis-brief.md) — эта спека их источник истины по деталям реализации, requirements/brief не дублируются здесь при расхождении.*

## Контекст

После [0016](0016-gateway-retry-config-and-bulk-resend.md) (настраиваемый retry, bulk resend) форвардинг остаётся «чёрным ящиком» для пользователя: нельзя временно приостановить пересылку без отзыва разрешений/удаления настроек сервера; нельзя проверить, что `webhookUrl`/`uploadToken` реально работают, не дожидаясь настоящей SMS; уведомление приходит только на приём SMS (`IncomingSmsNotifier.notifyIncoming`), не на результат доставки; ошибки доставки видны только через `docker logs`/логи `WorkManager` на другом устройстве.

Выявлено при анализе issues референсного проекта bogkonstantin/android_income_sms_gateway_webhook: #59 (Add ability to pause forwarding), #58/#56 (Test connection button), #91 (Notification on forward success/failure), #68 (In-app delivery log).

## Допущения и решения (собраны в интервью с владельцем продукта)

1. **Группировка**: все четыре пункта объединены в один Milestone по теме «надёжность и управление доставкой» — они все живут на экране «Доставка» и в цепочке `MessageRepository.enqueueDelivery`/`WebhookRequestWorker`.
2. **Пауза форвардинга** — глобальный переключатель (как текущие `webhookUrl`/`uploadToken`, не per-rule). Приём и локальное сохранение SMS не затрагиваются — сообщение всё равно попадает в Room со статусом `PENDING` (если прошло фильтр форвардинга), просто не ставится в очередь `WorkManager`, пока пауза активна. Возобновление пересылки повторно ставит в очередь все накопившиеся `PENDING`-сообщения — переиспользуется существующий `retryUndeliveredMessages()` (как уже происходит при сохранении `webhookUrl`/`uploadToken` на экране «Доставка»).
3. **Проверка соединения** — реальный `POST` на `webhookUrl` с тестовым payload (не HEAD/ping без токена). Это осознанно означает, что тестовое сообщение реально попадёт на backend и будет видно в Viewer App как обычное сообщение — задокументированный побочный эффект, не баг. Тестовый payload помечается легко узнаваемым содержимым (`from = "__test__"`, `text` с явным указанием, что это проверка соединения), чтобы пользователь мог отличить его от настоящей SMS в Viewer App.
4. **Уведомление о результате форвардинга** — отдельное от уведомления о приёме SMS (`IncomingSmsNotifier.notifyIncoming`, не меняется). Новое уведомление показывается при переходе сообщения в терминальный `FAILED` (не при каждой промежуточной `Result.retry()`) и, отдельно, при переходе в `SENT` **только если** до этого была хотя бы одна неудачная попытка (`runAttemptCount > 1`) — успешная доставка с первой попытки не создаёт уведомление, чтобы не дублировать уведомление о приёме SMS в штатном случае.
5. **Лог доставки в приложении** — новый экран со списком последних попыток доставки (успех/неудача, timestamp, номер попытки, текст ошибки при неудаче), доступный из экрана «Доставка». Хранится в новой таблице Room, не в `messages` (лог переживает даже удаление самого сообщения).
6. Все четыре пункта — глобальная функциональность форвардинга; расширения до per-rule/per-contact не запрошены и не входят в объём.

## Функциональность

### Пауза форвардинга

- Новый `Switch` «Пауза форвардинга» на экране «Доставка», рядом с полями retry.
- `GatewayConfigStore.isForwardingPaused(): Boolean` (default `false`) / `setForwardingPaused(Boolean)`.
- `MessageRepository.enqueueDelivery(messageId)` — в начале проверяет `configStore.isForwardingPaused()`; если `true`, не вызывает `WorkManager.enqueue`, сообщение остаётся в текущем статусе (`PENDING` для нового входящего, не меняется для ручного/bulk retry — они просто не ставят задачу).
- При сохранении экрана «Доставка», если пауза была включена и теперь выключена, вызывается `retryUndeliveredMessages()` (та же логика, что уже сейчас выполняется при каждом сохранении экрана «Доставка» — расширения отдельного условия не требуется, т.к. `retryUndeliveredMessages()` идемпотентен: если пауза всё ещё включена, `enqueueDelivery` внутри него по-прежнему не поставит задачи).

### Проверка соединения

- Кнопка «Проверить соединение» на экране «Доставка», активна только когда оба поля (`webhookUrl`/`uploadToken`) заполнены и валидны (текущая `canSave`-логика).
- По нажатию — `POST` на собранный `webhookUrl` с телом `WebhookPayload(from = "__test__", text = "Проверка соединения из Gateway App", sentStamp = null, receivedStamp = <now>, sim = null)` (используется тот же `WebhookPayload`/сериализация, что и в `WebhookRequestWorker`, без создания записи в локальной Room-таблице `messages`).
- Результат — снэкбар/инлайн-статус на экране: `"Успешно (HTTP <код>)"` при 2xx, `"Ошибка: <код/сообщение>"` при не-2xx или сетевой ошибке (таймаут, недоступный хост).
- Запрос выполняется с отдельным (более коротким) таймаутом, чем retry-логика форвардинга — не должен зависеть от `maxAttempts`/`backoffPolicy`, это одноразовая синхронная проверка, не через `WorkManager`.

### Уведомления о результате форвардинга

- Новый метод в `IncomingSmsNotifier` (или отдельный `DeliveryResultNotifier` — решить при реализации по объёму кода): `notifyDeliveryFailed(sender: String, attempts: Int)` — вызывается из `WebhookRequestWorker` при переходе в `FAILED` (`runAttemptCount >= maxAttempts`).
- `notifyDeliverySucceededAfterRetry(sender: String, attempts: Int)` — вызывается из `WebhookRequestWorker` при `success == true` и `runAttemptCount > 1`.
- Оба используют тот же канал уведомлений (`CHANNEL_ID = "incoming_sms"`) либо новый отдельный канал `"delivery_status"` — решить при реализации; отдельный канал даёт пользователю возможность независимо отключить уведомления о статусе доставки, не теряя уведомления о приёме SMS — предпочтительно.
- Уважают тот же `POST_NOTIFICATIONS`-permission check, что и существующий `notifyIncoming`.

### Лог доставки

- Новая таблица `delivery_log` (Room): `id` (PK autoincrement), `sender: String`, `attemptNumber: Int`, `timestamp: Long`, `success: Boolean`, `errorMessage: String?` (HTTP-код или текст исключения при неудаче, `null` при успехе).
- Запись создаётся в `WebhookRequestWorker.doWork()` после каждой попытки (успешной или нет), включая промежуточные `Result.retry()`, не только терминальные состояния.
- Новый `DeliveryLogDao`: `insert(entry)`, `observeRecent(limit: Int = 200): Flow<List<DeliveryLogEntity>>` (последние N записей по `timestamp DESC`, лимит — чтобы не грузить неограниченный список в UI при большом объёме).
- Новый экран `ui/deliverylog/DeliveryLogScreen.kt` — список записей (иконка успех/неудача, отправитель, время, номер попытки, текст ошибки при неудаче), доступен через новый пункт на экране «Доставка» (`onOpenDeliveryLog`).
- Без ретеншна/очистки в этой спеке (backlog) — таблица растёт неограниченно; на личном масштабе использования (см. уже принятое решение по объёму данных в 0014/0015/0016) не проблема, задокументировать как открытый вопрос.

## Архитектура

- `data/local/GatewayConfigStore.kt` — новый ключ `KEY_FORWARDING_PAUSED`, метод-пара `isForwardingPaused()/setForwardingPaused(Boolean)`, паттерн boolean-хранения как у `isHistoryImported()`.
- `data/repository/MessageRepository.kt` — `enqueueDelivery` получает ранний `if (configStore.isForwardingPaused()) return` перед сборкой `WorkRequest`; новый `open suspend fun testConnection(): TestConnectionResult` (sealed class/enum `Success(httpCode)`/`Failure(reason)`), использует тот же `OkHttpClient`/`Json`, что и `WebhookRequestWorker` (внедряются туда же через Hilt, либо через `MessageRepository`, если он получит эти зависимости — решить при реализации, вероятно проще внедрить `OkHttpClient`/`Json` напрямую в `DeliveryViewModel`, минуя `MessageRepository`, т.к. это не операция над `messages`).
- `data/remote/WebhookRequestWorker.kt` — после каждой попытки пишет `DeliveryLogDao.insert(...)`; при терминальном `FAILED` и при `success && runAttemptCount > 1` вызывает нотификатор.
- Новая таблица + `DeliveryLogEntity.kt`/`DeliveryLogDao.kt`, добавление в `GatewayDatabase.kt` (`@Database(entities = [..., DeliveryLogEntity::class], version = 5)`, `MIGRATION_4_5` создаёт таблицу `delivery_log`).
- `ui/delivery/DeliveryScreen.kt`/`DeliveryUiState.kt`/`DeliveryViewModel.kt` — новый `Switch` паузы, новая кнопка «Проверить соединение» с состоянием результата проверки (`testConnectionResult: TestConnectionResult? = null` в `UiState`), новый переход `onOpenDeliveryLog`.
- `ui/nav/NavGraph.kt` — новый route `delivery_log`.
- `sms/IncomingSmsNotifier.kt` (или новый `DeliveryResultNotifier.kt`) — новые методы уведомлений, вызываются из `WebhookRequestWorker` (внедряется туда же через Hilt, `WebhookRequestWorker` уже получает зависимости через `@AssistedInject`).

## Критерии приёмки

- При включённой паузе новое входящее SMS сохраняется в Room со статусом `PENDING`, но **не** ставит задачу `WorkManager` (проверяется отсутствием записи в `getWorkInfosByTag`).
- Выключение паузы (сохранение экрана «Доставка») реально ставит в очередь все накопившиеся `PENDING`-сообщения.
- «Проверить соединение» с корректным `webhookUrl`/`uploadToken` (тестовый локальный backend) показывает «Успешно», с неверным токеном/недоступным хостом — «Ошибка» с деталями; не создаёт запись в локальной таблице `messages`.
- После `maxAttempts` неудачных попыток форвардинга появляется системное уведомление о неудаче; при успехе с первой попытки уведомление **не** появляется (кроме уже существующего уведомления о приёме SMS); при успехе после ≥2 попыток появляется уведомление об успехе.
- Экран «Лог доставки» показывает записи в порядке от новых к старым, каждая попытка (успех/неудача) реального форвардинга отражена в списке.
- Backend/Viewer App не затронуты — формат [0003-sms-webhook.md](0003-sms-webhook.md) не меняется (тестовый payload проверки соединения использует существующий формат, без новых полей).

## Тесты

- Unit/инструментированные: `GatewayConfigStore.isForwardingPaused/setForwardingPaused` — сохранение, дефолт `false`.
- Инструментированные: `MessageRepository.enqueueDelivery` не ставит задачу `WorkManager`, когда `isForwardingPaused() == true`; ставит, когда `false`.
- Инструментированные: `testConnection()` против тестового HTTP-сервера (`MockWebServer` или аналог, уже используемый в проекте, если есть прецедент — проверить перед реализацией) — успешный/неуспешный ответ корректно классифицируются.
- Инструментированные: `WebhookRequestWorker` — запись в `delivery_log` создаётся после каждой попытки; нотификатор вызывается при терминальном `FAILED` и при успехе после `runAttemptCount > 1`, не вызывается при успехе с первой попытки (verify-тесты на моках нотификатора).
- Инструментированные (Compose): `DeliveryScreen` — переключатель паузы сохраняется; кнопка «Проверить соединение» показывает результат; `DeliveryLogScreen` — список записей отображается, пустое состояние при отсутствии записей.
- Ручная проверка на физическом устройстве: включить паузу, отправить SMS, убедиться что сообщение висит `PENDING` без попытки доставки; выключить паузу, убедиться что доставка происходит; нажать «Проверить соединение» с реальным backend; убедиться, что при недоступном сервере после исчерпания попыток приходит уведомление о неудаче.

## Открытые вопросы / Backlog (не блокируют Draft → Implemented)

- Ретеншн/очистка старых записей `delivery_log` — таблица растёт без ограничения; на личном масштабе не проблема сейчас.
- Отдельный notification channel для статуса доставки vs общий с приёмом SMS — решить при реализации, не блокирует критерии приёмки.
- Не рассматривается пауза per-rule/per-контакт.

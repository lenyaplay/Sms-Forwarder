# 0018 — Delete-after-forward (+ удаление из системного SMS-хранилища), имя контакта в payload, экспорт/импорт настроек (Android Gateway App)

**Статус:** Draft

*Требования собраны через `stakeholder-requirements-gathering`, задокументированы в [docs/requirements/0017-0018-requirements-doc.md](../requirements/0017-0018-requirements-doc.md) и [docs/requirements/0017-0018-analysis-brief.md](../requirements/0017-0018-analysis-brief.md) — эта спека их источник истины по деталям реализации, requirements/brief не дублируются здесь при расхождении.*

## Контекст

Сейчас удаление сообщения/диалога в Gateway App (`MessageRepository.deleteMessage`/`deleteConversation`, кнопки в `ThreadScreen`/`ConversationsScreen`) стирает запись только из локальной Room-БД приложения — системное SMS-хранилище устройства (`content://sms`, куда пишет штатное приложение «Сообщения») не затрагивается вообще. Нет автоматического удаления после успешной пересылки. Имя контакта резолвится (`ContactNameResolver`) только для локального UI, никогда не попадает в отправляемый на backend payload. Нет способа перенести настройки (server URL, токен, retry, правила фильтрации) на другое устройство/после переустановки без ручного повторного ввода.

Выявлено при анализе issues референсного проекта bogkonstantin/android_income_sms_gateway_webhook: #102/#57 (Delete/mute SMS after successful forward), #61/#43 (Include contact name in payload), #76 (Export/import configuration).

## Допущения и решения (собраны в интервью с владельцем продукта)

1. **Группировка**: три пункта объединены в один Milestone по теме «данные и приватность» — все связаны с тем, что происходит с данными сообщения/настроек за пределами локального UI.
2. **Delete-after-forward и удаление из системного хранилища — одна и та же механика.** Приложение уже держит роль `android.app.role.SMS` (default SMS app), что даёт право писать/удалять в `content://sms` — обычные приложения такого права не имеют. Решено: **и ручное удаление** через UI (кнопки в `ThreadScreen`/`ConversationsScreen`), **и** новая настройка **«удалять после успешной пересылки»** используют один и тот же путь удаления, который теперь чистит **и** локальную Room-запись, **и** соответствующую запись в `content://sms`. Это меняет поведение уже существующей фичи ручного удаления, не только добавляет новую — явное решение владельца продукта после обсуждения (см. ниже).
3. **Необратимость.** В Android нет платформенной «корзины» для SMS — штатные приложения (AOSP «Сообщения», Google Messages, OEM-оболочки) при удалении тоже стирают запись из `content://sms` безвозвратно. Новое поведение не менее безопасно, чем штатное поведение системных приложений — не вносит нового риска сверх уже принятого пользователем при использовании любого SMS-клиента.
4. **«Archive after forward» отдельной фичей не делаем** — рассматривался вариант автоматической архивации диалога при успешной пересылке, отклонён (архив сейчас работает на уровне диалога, не сообщения — не подходит по гранулярности для этой автоматизации).
5. **Имя контакта в payload — опционально, скрыто по умолчанию.** Новое поле `contactName` в `WebhookPayload` — это изменение проводного контракта [0003-sms-webhook.md](0003-sms-webhook.md) (обратно совместимое добавление, не breaking для существующих потребителей, которые незнакомое поле проигнорируют). Новый переключатель на экране «Доставка»: **«Скрывать имя контакта в пересылке»**, **включён по умолчанию** (т.е. по умолчанию `contactName` в payload не передаётся, поведение не меняется для существующих пользователей молча) — пользователь должен явно выключить переключатель, чтобы имя контакта начало попадать на backend.
6. **Экспорт/импорт — объём: server URL, upload token, retry-настройки (`maxAttempts`/`baseIntervalSeconds`/`backoffPolicy`), правила фильтрации.** Не включены: новые переключатели из [0017](0017-gateway-delivery-reliability-and-visibility.md) (пауза форвардинга, скрытие имени контакта) и лог доставки — вынесено в открытые вопросы этой спеки, не блокирует Draft → Implemented.
7. Все три пункта — глобальная функциональность, не per-rule/per-diалог, за исключением того, что удаление по своей природе применяется к конкретному сообщению/диалогу (как и сейчас).

## Функциональность

### Delete-after-forward + удаление из системного SMS-хранилища

- Новый `Switch` «Удалять после успешной пересылки» на экране «Доставка» (`GatewayConfigStore.deleteAfterForward(): Boolean`, default `false` — не меняет поведение существующих пользователей молча).
- `WebhookRequestWorker` — при переходе сообщения в `SENT` (успешная доставка), если `configStore.deleteAfterForward() == true`, вызывает `messageRepository.deleteMessage(messageId)` **после** записи статуса `SENT` (чтобы `delivery_log`/уведомление об успехе из 0017, если применимо, увидели корректный статус до удаления).
- `MessageRepository.deleteMessage(id)`/`deleteConversation(sender)` — расширяются: после существующего Room-удаления дополнительно удаляют соответствующую запись(и) из `content://sms` через `ContentResolver.delete(Telephony.Sms.CONTENT_URI, "_id = ?", arrayOf(systemSmsId))`, используя новое поле `MessageEntity.systemSmsId: Long?` (см. «Архитектура»). Если `systemSmsId == null` (сообщение ещё не сопоставлено с системным хранилищем — например, сразу после приёма, до фонового сопоставления) — Room-удаление всё равно происходит, удаление из `content://sms` пропускается молча, задокументированный известный пробел (см. «Открытые вопросы»).
- `SecurityException`/иная ошибка при удалении из `content://sms` (например, роль default SMS app была потеряна — известный кейс на некоторых OEM, см. Milestone 12) — перехватывается, логируется, **не** прерывает и не откатывает уже выполненное Room-удаление: пользователь не должен получить необъяснимый сбой удаления там, где раньше удаление всегда срабатывало.

### Имя контакта в payload

- `data/remote/WebhookPayload.kt` — новое поле `val contactName: String? = null`.
- `GatewayConfigStore.hideContactNameInPayload(): Boolean` (default `true`) / `setHideContactNameInPayload(Boolean)`.
- `WebhookRequestWorker.doWork()` — перед сборкой payload: если `!configStore.hideContactNameInPayload()`, резолвит `contactNameResolver.displayNameFor(message.sender)` и передаёт результат (может быть `null`, если контакт не найден или нет разрешения `READ_CONTACTS`) в `WebhookPayloadMapper.toPayload(message, contactName)`; иначе передаёт `null`.
- Новый `Switch` «Скрывать имя контакта в пересылке» на экране «Доставка», `checked = hideContactNameInPayload` (по умолчанию включён).

### Экспорт/импорт настроек

- Новые кнопки «Экспортировать настройки» / «Импортировать настройки» на `SettingsScreen` (не на «Доставка» — это действие над всей конфигурацией приложения, а не только доставкой).
- Экспорт — сериализация в JSON (`kotlinx.serialization`, тот же стек, что уже используется для `WebhookPayload`): `serverUrl`, `uploadToken`, `retryMaxAttempts`, `retryBaseIntervalSeconds`, `retryBackoffPolicy`, список `FilterRuleEntity` (без `id`/`sortOrder`-коллизий с целевым устройством — `id` перегенерируется при импорте, `sortOrder` переносится как относительный порядок). Сохранение через Storage Access Framework (`ACTION_CREATE_DOCUMENT`, `application/json`) — без сетевых вызовов, без облака.
- Импорт — чтение через `ACTION_OPEN_DOCUMENT`, парсинг, валидация **до** применения: диапазоны retry-полей (те же `[1,50]`/`[10,3600]`, что и в UI-валидации 0016), валидность regex-паттернов в правилах фильтрации (переиспользовать существующую валидацию `FilterRuleEditViewModel`). При любой ошибке валидации — ничего не применяется (атомарно, все-или-ничего), показывается диалог с описанием ошибки.
- UI-предупреждение при экспорте: файл содержит `uploadToken` в открытом виде — показать явный текст-предупреждение перед сохранением («файл содержит токен доступа в открытом виде, храните его так же бережно, как пароль»).

## Архитектура

- `data/local/db/MessageEntity.kt` — новое поле `systemSmsId: Long? = null` (ID строки в `content://sms`, для точного сопоставления при удалении). `GatewayDatabase.kt` — `version = 6` (после `MIGRATION_4_5` из [0017](0017-gateway-delivery-reliability-and-visibility.md)), `MIGRATION_5_6` добавляет колонку.
- `data/local/SmsHistoryImporter.kt` — расширяется: при синхронизации новых строк из `content://sms` (`syncNewMessages()`) и при первичном импорте истории, если для входящего сообщения уже существует Room-запись без `systemSmsId` (создана напрямую через `SmsDeliverReceiver`/`storeAndForward` до появления записи в системном хранилище), сопоставляет по `(sender, sentStamp/receivedStamp)` и бэкфиллит `systemSmsId` вместо создания дубликата. **Известный риск, требующий подтверждения при реализации**: сопоставление по времени+отправителю не гарантированно уникально при двух сообщениях от одного номера в одну секунду — задокументировать как ограничение, не блокирующее Draft → Implemented.
- `data/remote/OutgoingSmsSender.kt`/`MessageRepository.sendMessage` — после `SmsManager.sendTextMessage`, аналогичное сопоставление для исходящих (запись в `content://sms` для исходящих создаётся системой автоматически после отправки) — тот же механизм бэкфилла через `SmsHistoryImporter`, не дублировать логику.
- `data/repository/MessageRepository.kt` — `deleteMessage`/`deleteConversation` получают доступ к `ContentResolver` (`@ApplicationContext context` уже внедрён); новый приватный `deleteFromSystemStore(systemSmsId: Long?)` — `try { context.contentResolver.delete(...) } catch (e: SecurityException) { Log.w(...) }`.
- `data/remote/WebhookRequestWorker.kt` — после `messageDao.update(... SENT)`, если `configStore.deleteAfterForward()`, вызывает `messageRepository.deleteMessage(messageId)` (внедрить `MessageRepository` в `WebhookRequestWorker` через Hilt, либо провести операцию через `messageDao` напрямую — решить при реализации, вероятно проще внедрить `MessageRepository`, чтобы не дублировать логику удаления из `content://sms`).
- `data/remote/WebhookPayload.kt`/`WebhookPayloadMapper.kt` — новое поле `contactName`, `toPayload(message, contactName: String?)`.
- `GatewayConfigStore.kt` — новые ключи `deleteAfterForward`/`hideContactNameInPayload`, тот же boolean-паттерн.
- Новый `data/local/GatewaySettingsExporter.kt` — `suspend fun exportToJson(): String`, `suspend fun importFromJson(json: String): Result<Unit>` (валидация внутри, `Result.failure` с описанием ошибки при провале валидации — ничего не применяется).
- `ui/settings/SettingsScreen.kt`/`SettingsViewModel.kt` (существующий тонкий wrapper — снова получает состояние/actions для двух новых кнопок, либо создаётся заново с минимальным `UiState`, если полностью пуст сейчас) — интеграция SAF через `ActivityResultContracts.CreateDocument`/`OpenDocument`.
- `ui/delivery/DeliveryUiState.kt`/`DeliveryViewModel.kt`/`DeliveryScreen.kt` — новые поля/переключатели `deleteAfterForward`, `hideContactNameInPayload`.

## Критерии приёмки

- Удаление сообщения/диалога через UI (`ThreadScreen`/`ConversationsScreen`) стирает запись как из Room, так и из `content://sms` (проверяется прямым запросом к `content://sms` до/после в инструментированном тесте или живой проверкой на устройстве через `adb shell content query`).
- Включённая настройка «Удалять после успешной пересылки» приводит к тому, что сообщение исчезает и из списка диалогов Gateway App, и из `content://sms` сразу после перехода в `SENT`, без ручного действия пользователя.
- Ошибка удаления из `content://sms` (смоделированная потеря роли/разрешения) не мешает завершиться локальному Room-удалению и не роняет приложение.
- Payload на backend содержит `contactName` **только** когда «Скрывать имя контакта» выключен явно пользователем; по умолчанию (сразу после обновления, без действий пользователя) поле отсутствует/`null` — существующие пользователи не начинают молча передавать имена контактов.
- Экспорт создаёт валидный JSON-файл со всеми перечисленными полями через SAF; импорт этого же файла на чистой установке восстанавливает `serverUrl`/`uploadToken`/retry-настройки/правила фильтрации идентично исходным.
- Импорт файла с невалидным значением (например, `retryMaxAttempts = 999` вне диапазона, или невалидный regex в правиле) не применяет **ничего** из файла и показывает пользователю ошибку.
- Backend/Viewer App совместимы с новым полем `contactName` без изменений на их стороне (неизвестное поле в JSON, обратная совместимость) — формат [0003-sms-webhook.md](0003-sms-webhook.md) расширяется аддитивно, дополнить документ описанием нового опционального поля.

## Тесты

- Unit/инструментированные: `GatewayConfigStore.deleteAfterForward`/`hideContactNameInPayload` — сохранение, дефолты (`false`/`true` соответственно).
- Инструментированные: `MessageRepository.deleteMessage`/`deleteConversation` — с замоканным/тестовым `ContentResolver` (или реальным `content://sms` на эмуляторе/устройстве с ролью default SMS app в инструментированном тесте) подтверждают удаление обеих записей; отдельный тест на `SecurityException` при удалении из `content://sms` не прерывает Room-удаление.
- Инструментированные: `WebhookRequestWorker` — с `deleteAfterForward = true` после успешной доставки сообщение отсутствует в `messageDao.observeAll()`.
- Инструментированные: `WebhookRequestWorker`/`WebhookPayloadMapper` — с `hideContactNameInPayload = false` и известным контактом (тестовый `ContactNameResolver`-мок) собранный payload содержит `contactName`; с `hideContactNameInPayload = true` (default) — не содержит.
- Unit: `GatewaySettingsExporter` — round-trip экспорт→импорт восстанавливает идентичные значения; импорт заведомо невалидного JSON (диапазон/regex) не изменяет текущие настройки, возвращает описательную ошибку.
- Инструментированные (Compose): `DeliveryScreen` — новые переключатели сохраняются; `SettingsScreen` — кнопки экспорта/импорта запускают SAF-интенты (проверка через `Intents.intended`, если в проекте уже есть прецедент, иначе через прямую проверку колбэков ViewModel).
- Ручная проверка на физическом устройстве: удаление сообщения из UI действительно стирает соответствующую SMS из штатного приложения «Сообщения»; включение «Удалять после успешной пересылки» — реальная входящая SMS исчезает из обоих мест после доставки; экспорт файла и импорт на переустановленном приложении восстанавливает рабочую конфигурацию без ручного ввода.

## Открытые вопросы / Backlog (не блокируют Draft → Implemented)

- Сопоставление `systemSmsId` по `(sender, timestamp)` при бэкфилле — не строго уникально при коллизии секунды; для личного масштаба использования риск принят, не блокирует.
- Экспорт/импорт не включает переключатели из [0017](0017-gateway-delivery-reliability-and-visibility.md) (пауза, скрытие имени) и не включает лог доставки — можно расширить позже отдельным пунктом, если понадобится.
- Полная синхронизация удаления в обратную сторону (удаление SMS в штатном приложении «Сообщения» → исчезновение из Gateway App) не входит в объём — текущий `ContentObserver`/watermark-sync (Milestone 12, этап 4) реагирует только на **новые** записи, не на удаления.

# 0016 — Настраиваемый retry форвардинга и bulk resend (Android Gateway App)

**Статус:** Implemented

## Контекст

Форвардинг SMS на backend-webhook реализован через `WorkManager` (`MessageRepository.enqueueDelivery`, `WebhookRequestWorker`) с захардкоженными параметрами: `MAX_RETRIES = 10`, `BackoffPolicy.EXPONENTIAL` с базовым интервалом 30 секунд. Пользователь не может ни изменить эти значения, ни выбрать другую стратегию backoff. Ручной повтор одного сообщения (`retryMessage(id)`, кнопка «Повторить» на `FAILED`-сообщении) уже есть, но нет массового действия «повторить все неудавшиеся».

Выявлено при анализе issues референсного проекта bogkonstantin/android_income_sms_gateway_webhook: #69 (Changing the number of retries and the time interval between them), #38 (Be able to configure MAX_ATTEMPT — дубликат #69), #3 (Feature request: Should be a button for Failed message — bulk resend).

## Допущения и решения (собраны в интервью с владельцем продукта)

1. **Настраиваются оба параметра**: максимальное число попыток (`maxAttempts`) и базовый интервал backoff (`baseIntervalSeconds`).
2. **Стратегия backoff тоже настраивается** — пользователь выбирает между `EXPONENTIAL` (WorkManager сам увеличивает интервал с каждой повторной попыткой) и `LINEAR` (фиксированный интервал каждый раз), обе стратегии — нативно поддерживаемые значения `androidx.work.BackoffPolicy`.
3. **Диапазоны UI-валидации**: `maxAttempts` — от 1 до 50; `baseIntervalSeconds` — от 10 до 3600 (10 секунд — минимально допустимый WorkManager'ом интервал экспоненциального backoff, `MIN_BACKOFF_MILLIS`; час — верхняя разумная граница между попытками). Значения вне диапазона не позволяют сохранить настройки. Дефолты при первом запуске (до того как пользователь явно сохранил свои значения) — текущее поведение: `maxAttempts = 10`, `baseIntervalSeconds = 30`, `backoffPolicy = EXPONENTIAL` — апдейт не меняет поведение существующих пользователей молча.
4. **Конфигурация глобальная** (одна на всё приложение, как текущий `webhookUrl`/`uploadToken`), не per-rule и не per-message. Расширение до per-rule не запрошено и не входит в объём.
5. **Bulk resend затрагивает только сообщения со статусом `FAILED`.** Сообщения со статусом `NOT_FORWARDED` (заблокированные правилом фильтра форвардинга, см. [0015](0015-gateway-sms-filtering.md)) **не входят** в bulk resend — это осознанное решение фильтра, а не сбой доставки, и повторная отправка обошла бы фильтр без явного намерения пользователя. Bulk resend — это массовая версия уже существующего `retryMessage(id)`, применённая к каждому текущему `FAILED`-сообщению.
6. **Bulk resend всегда требует подтверждения** через существующий `ui/common/ConfirmDialog.kt`, с указанием количества затрагиваемых сообщений в тексте диалога — независимо от того, сколько их (одно или много).
7. **UI-размещение**:
   - Настройки retry (`maxAttempts`, `baseIntervalSeconds`, `backoffPolicy`) переезжают на **новый отдельный экран «Доставка»**, куда также переносятся уже существующие поля `webhookUrl` и `uploadToken` (+копирование токена) из `SettingsScreen`. `SettingsScreen` после этого содержит только точку входа (переход) на экран «Доставка» и уже существующую точку входа в «Фильтрацию SMS» — все настройки, касающиеся форвардинга и его надёжности, теперь физически в одном месте, а не разбросаны между `SettingsScreen` и новым экраном.
   - Кнопка «Повторить неудавшиеся» — в `TopAppBar` экрана списка диалогов (`ConversationsScreen`), видна только когда в Room есть хотя бы одно сообщение со статусом `FAILED` (в любом диалоге, не только в открытом сейчас).

## Функциональность

- Экран «Доставка» (новый route, отдельный от `SettingsScreen`): поля `webhookUrl`, `uploadToken` (с копированием, перенесены как есть из `SettingsScreen`), `maxAttempts` (числовое поле, 1–50), `baseIntervalSeconds` (числовое поле, 10–3600), `backoffPolicy` (переключатель `EXPONENTIAL`/`LINEAR`), кнопка «Сохранить». Некорректные значения (вне диапазона, не число) блокируют сохранение и показывают ошибку под полем — по аналогии с regex-валидацией в экране правил фильтрации (0015).
- `SettingsScreen` теряет поля `webhookUrl`/`uploadToken`/«Сохранить» — остаются только переходы на экран «Доставка» и на экран «Фильтрация SMS».
- `MessageRepository.enqueueDelivery` читает `maxAttempts`/`baseIntervalSeconds`/`backoffPolicy` из `GatewayConfigStore` (не константы) при каждой постановке задачи `WebhookRequestWorker`; `WebhookRequestWorker` читает `maxAttempts` оттуда же вместо константы `MAX_RETRIES` при принятии решения `Result.retry()` vs `Result.failure()`.
- Изменение настроек retry в UI применяется к **новым** задачам форвардинга (новые входящие SMS, ручной или bulk resend после сохранения); уже поставленные в очередь `WorkManager`-задачи с прежним `BackoffPolicy`/интервалом не пересоздаются — `WorkManager` не поддерживает горячую замену параметров активной задачи, это ограничение платформы, а не недосмотр.
- `ConversationsScreen`: `TopAppBar` показывает кнопку «Повторить неудавшиеся» при `failedCount > 0` (агрегат по всем диалогам). По нажатию — `ConfirmDialog` с текстом, включающим `failedCount`; при подтверждении — `MessageRepository.retryAllFailed()` (новый метод: выбирает все сообщения со статусом `FAILED`, вызывает `enqueueDelivery` для каждого — той же логикой, что и одиночный `retryMessage`, но без индивидуального сброса статуса на `PENDING` до момента, пока конкретная задача реально не начнёт попытку, — как сейчас делает `retryMessage`).

## Архитектура

- `data/local/GatewayConfigStore.kt` — новые ключи: `retryMaxAttempts: Int` (default 10), `retryBaseIntervalSeconds: Long` (default 30), `retryBackoffPolicy: BackoffPolicy` (хранится как `.name`/`.valueOf` по паттерну, уже установленному для `FilterMode` в 0015; default `EXPONENTIAL`).
- `data/repository/MessageRepository.kt`:
  - `enqueueDelivery(messageId)` — параметры `setBackoffCriteria` читаются из `GatewayConfigStore` вместо констант.
  - Новый `open suspend fun retryAllFailed()` — `messageDao.getByStatus(DeliveryStatus.FAILED)` (новый DAO-метод либо переиспользование существующего запроса с фильтром) → `enqueueDelivery(it.id)` для каждого.
- `data/remote/WebhookRequestWorker.kt` — `MAX_RETRIES` заменяется на значение, читаемое из `GatewayConfigStore` (либо передаётся через `Data`/`inputData` при постановке задачи — решить при реализации, что проще: constraints делают повторное чтение конфигурации внутри `doWork()` тривиальным, т.к. `configStore` уже внедряется через Hilt).
- `data/local/db/MessageDao.kt` — метод для выборки всех `FAILED`-сообщений (если такого ещё нет отдельно от `getUndelivered()`, который сейчас исключает `SENT`/`NOT_FORWARDED`, но включает и `PENDING`, что для bulk resend не подходит — нужен именно строгий `FAILED`-фильтр).
- UI: `ui/delivery/DeliveryScreen.kt` (+`DeliveryContent` stateless, по установленному в проекте MVVM/MVI-паттерну `UiState`/`Actions`) с полями `webhookUrl`/`uploadToken`/`maxAttempts`/`baseIntervalSeconds`/`backoffPolicy`; `DeliveryViewModel` читает/пишет `GatewayConfigStore`. `SettingsScreen.kt` — убрать поля webhook, оставить переходы (`onOpenDelivery`, уже существующий `onOpenFilterRules`).
- `NavGraph.kt` — новый route `delivery`.
- `ui/conversations/ConversationsScreen.kt`/`ConversationsViewModel.kt` — `UiState` получает `failedCount: Int` (наблюдение за Room, аналогично существующим `Flow`-подпискам), `Actions` получает `onResendAllFailed()`; `TopAppBar` — новая иконка/кнопка, видимая при `failedCount > 0`, открывающая `ConfirmDialog`.

## Критерии приёмки

- Изменение `maxAttempts`/`baseIntervalSeconds`/`backoffPolicy` на экране «Доставка» сохраняется и применяется к следующей поставленной задаче форвардинга (проверяется через `WorkManager.getWorkInfosByTag`/переданные `BackoffCriteria` в инструментированном тесте).
- Значения `maxAttempts` вне `[1, 50]` и `baseIntervalSeconds` вне `[10, 3600]` не позволяют сохранить настройки экрана «Доставка», ошибка показывается под полем.
- `WebhookRequestWorker` уходит в терминальный `FAILED`-статус после ровно `maxAttempts` попыток (не захардкоженных 10), если сервер продолжает отвечать ошибкой/недоступен.
- Выбор `LINEAR` действительно даёт фиксированный интервал между попытками (не растущий), `EXPONENTIAL` — растущий — на уровне переданного в `WorkManager` `BackoffPolicy`.
- `SettingsScreen` больше не содержит полей `webhookUrl`/`uploadToken`/кнопки «Сохранить» — только переходы на «Доставка» и «Фильтрация SMS»; экран «Доставка» содержит все перенесённые поля и работает идентично прежнему поведению сохранения.
- Кнопка «Повторить неудавшиеся» в `TopAppBar` списка диалогов невидима, когда `FAILED`-сообщений нет, и видна, когда есть хотя бы одно (в любом диалоге).
- Нажатие кнопки открывает `ConfirmDialog` с указанием количества сообщений; отмена ничего не меняет; подтверждение ставит в очередь форвардинг для каждого `FAILED`-сообщения (и только `FAILED` — `NOT_FORWARDED`-сообщения не затронуты).
- Backend/Viewer App не затронуты — формат `0003-sms-webhook.md` не меняется.

## Тесты

- Unit/инструментированные: `GatewayConfigStore` — сохранение/чтение `retryMaxAttempts`/`retryBaseIntervalSeconds`/`retryBackoffPolicy`, дефолты при первом запуске.
- Инструментированные: `MessageRepository.enqueueDelivery` читает `BackoffCriteria` из `GatewayConfigStore`, а не из констант. **Отклонение от изначально описанного способа проверки (не самовольное — обнаружено и задокументировано при `analysis-qa-checklist`)**: `WorkManager`'s тестовое API (`WorkInfo`) не раскрывает переданные `BackoffCriteria` для инспекции, поэтому дословная проверка "переданные в `WorkRequest` `BackoffCriteria` соответствуют текущим значениям" технически невыполнима через `getWorkInfosByTag`. Смысл критерия (настройки реально читаются из хранилища, а не захардкожены) проверяется эквивалентно через `verify(configStore).retryBackoffPolicy()`/`verify(configStore).retryBaseIntervalSeconds()` после вызова, дополнено сквозной живой проверкой на устройстве (см. ниже) и `WebhookRequestWorkerTest`'s тестами на `maxAttempts`-границу.
- Инструментированные: `WebhookRequestWorker` — с уменьшенным `maxAttempts` (например, 2) сообщение переходит в `FAILED` после ровно 2 неудачных попыток, не 10.
- Инструментированные: `MessageRepository.retryAllFailed()` — ставит задачи форвардинга только для `FAILED`-сообщений, не трогает `NOT_FORWARDED`/`SENT`/`PENDING`.
- Инструментированные (Compose): `DeliveryScreen` — сохранение валидных значений, отклонение значений вне диапазона; `SettingsScreen` — отсутствие полей webhook, наличие переходов на оба экрана; `ConversationsScreen` — кнопка bulk resend появляется/исчезает в зависимости от `failedCount`, открывает `ConfirmDialog`, подтверждение вызывает `onResendAllFailed()`.
- Ручная проверка на физическом устройстве: изменение `maxAttempts` на маленькое значение и наблюдение, что сообщение реальному недоступному серверу переходит в `FAILED` быстрее; bulk resend реально переставляет `FAILED`-сообщения в очередь и они уходят на backend при восстановлении доступности.

## Открытые вопросы / Backlog (не блокируют Draft → Implemented)

- Индивидуальная настройка retry per-rule/per-контакт — не запрошено, не в объёме v1.
- Уведомление пользователя о переходе сообщения в терминальный `FAILED` (issue #91 из смежного анализа) — отдельная тема, не входит в эту спеку.

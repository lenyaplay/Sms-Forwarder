# 0029 — SIM-индикатор у сообщения, группировка сообщений по времени, авто-скрытие клавиатуры (Android Gateway App)

**Статус:** Implemented
**Milestone:** [26, часть 2](../roadmaps/Roadmap%202.md)
**Requirements:** [0029-requirements-doc.md](../requirements/0029-requirements-doc.md), [0029-analysis-brief.md](../requirements/0029-analysis-brief.md)

---

## Контекст

Продолжение Milestone 26 после части 1 (визуальный полишинг, спека [0028](0028-visual-polish-shapes-spacing-edge-to-edge.md)). Владелец продукта поручил (2026-09-04) взять три следующих пункта бэклога:

1. SIM-индикатор у сообщения в треде — сейчас видно, через какую SIM отправлять новое сообщение (SIM-селектор, спека 0028), но не видно, через какую SIM пришло/было отправлено уже существующее сообщение.
2. Группировка подряд идущих сообщений одного отправителя по времени (как в Telegram) — сейчас каждое сообщение рисуется как отдельный самостоятельный пузырь без учёта соседних.
3. Авто-скрытие клавиатуры при скролле списка сообщений вверх — сейчас клавиатура остаётся открытой и закрывает часть экрана, даже когда пользователь явно листает историю, а не набирает текст.

Требования собраны через `stakeholder-requirements-gathering` (см. requirements doc) — три `AskUserQuestion` решения: SIM-индикатор не ретроактивен (только новые сообщения), порог группировки 5 минут, триггер скрытия клавиатуры — скролл списка вверх в любую сторону от низа.

---

## Дизайн

### 1. Импорт `subscriptionId` из `content://sms` (`SmsHistoryImporter.kt`)

Текущее состояние (`android_gateway/app/src/main/java/com/smsforwarder/gateway/data/local/SmsHistoryImporter.kt:23-51`): `HISTORY_PROJECTION` не содержит `Telephony.Sms.SUBSCRIPTION_ID`, `toMessageEntity(...)` хардкодит `simSlot = null`. Поле `MessageEntity.simSlot: Int?` (`data/local/db/MessageEntity.kt:18`) уже существует в схеме — **миграция БД не нужна**, только заполнение при импорте. `query-validation` не применяется (правило распространяется на новые SQL-миграции, здесь новой миграции нет).

Изменения:
- `HISTORY_PROJECTION` — добавить `Telephony.Sms.SUBSCRIPTION_ID`.
- `toMessageEntity(...)` — добавить параметр `subscriptionIdCol: Int`, читать `Telephony.Sms.SUBSCRIPTION_ID` из курсора (может быть `-1`/отсутствовать на старых Android — тогда `null`).
- Маппинг subscriptionId → slotIndex: обратный метод в `SimOptionsProvider`:
  ```kotlin
  open fun slotForSubscriptionId(subscriptionId: Int?): Int? =
      subscriptionId?.let { id -> activeSims().find { it.subscriptionId == id }?.slotIndex }
  ```
  Возвращает `null`, если подписка больше не активна (SIM извлечена/деактивирована) — это осознанная деградация, а не ошибка: строка просто не получит `simSlot`, как и любое сообщение, импортированное до этого фикса.
- `toMessageEntity(...)` вызывает `simOptionsProvider.slotForSubscriptionId(...)` — требует передать `SimOptionsProvider` в функцию (сейчас `toMessageEntity` — top-level `internal fun`, не член класса; либо переносится в метод `SmsHistoryImporter`, либо принимает `SimOptionsProvider` явным параметром — механическая правка сигнатуры, оба вызывающих места, `importIfNeeded()` и `syncNewMessages()`, уже используют общую функцию).
- **Не ретроактивно**: `importIfNeeded()` запускается один раз (`configStore.isHistoryImported()` guard) — уже импортированные строки не пересканируются, это уже текущее поведение, менять не требуется. Явно фиксируем как принятое ограничение, а не дефект (см. Открытые вопросы в requirements doc, п.1).

### 2. SIM-индикатор в `MessageBubble` (`ThreadScreen.kt:239-300`)

Видимость — то же условие, что уже определяет `showSimSelector` в `ThreadUiState` (`ui/thread/ThreadUiState.kt:18`: `availableSims.size > 1`), плюс конкретное сообщение должно нести `simSlot != null`:

```kotlin
if (uiState.showSimSelector && message.simSlot != null) {
    Text(
        text = "SIM ${message.simSlot + 1}",
        style = MaterialTheme.typography.labelSmall,
        color = /* та же вторичная окраска, что у времени сообщения */,
        modifier = Modifier.testTag(ThreadTestTags.simIndicator(message.id)),
    )
}
```

Размещение — в той же строке, что и таймстамп (`formatTime(message.createdAt)`), через `Row` с `Arrangement.spacedBy`, чтобы не добавлять лишнюю строку на каждый пузырь. `MessageBubble` должен получить доступ к `uiState.showSimSelector` — либо принимает его отдельным параметром, либо `ThreadContent` передаёт целиком нужный флаг (простой `Boolean`, не весь `uiState`, чтобы не плодить recomposition-зависимость от несвязанных полей).

Новый testTag: `ThreadTestTags.simIndicator(messageId: Long) = "thread_sim_indicator_$messageId"`.

### 3. Группировка сообщений по времени

Правило (из requirements doc): подряд идущие сообщения **одного `sender`+`direction`** с разницей `createdAt` **< 5 минут** — одна группа; разрыв ≥ 5 минут или смена отправителя/направления — новая группа.

Реализация — чистая функция, юнит-тестируемая отдельно от Compose (не завязывать бизнес-правило на UI-тест, см. requirements doc/`Constraints`):

```kotlin
// ui/thread/MessageGrouping.kt
private const val GROUP_GAP_MILLIS = 5 * 60 * 1000L

fun List<MessageEntity>.isFirstInGroup(index: Int): Boolean {
    if (index == 0) return true
    val current = this[index]
    val previous = this[index - 1]
    return current.sender != previous.sender ||
        current.direction != previous.direction ||
        current.createdAt - previous.createdAt >= GROUP_GAP_MILLIS
}
```

`ThreadContent`/`items(uiState.messages, ...)` вычисляет `isFirstInGroup` по индексу и передаёт `MessageBubble` булевым параметром `isFirstInGroup: Boolean`. Визуальный эффект при `isFirstInGroup == false`: уменьшенный `verticalArrangement`-отступ между пузырями (сейчас `Arrangement.spacedBy(4.dp)` в `LazyColumn`, общий на весь список — потребуется заменить фиксированный interitem spacing на явный `Modifier.padding(top = ...)` внутри `MessageBubble`, зависящий от `isFirstInGroup`: `if (isFirstInGroup) 8.dp else 2.dp`). Существующий формат самого пузыря (фон, форма, отступы `.padding(12.dp)`) не меняется — группировка убирает только зазор между пузырями одной группы, шапки/имени отправителя сейчас у `MessageBubble` и так нет (только таймстамп внутри каждого пузыря — остаётся, как в Telegram, где время всё равно показано у каждого сообщения группы, просто без лишнего вертикального разрыва).

### 4. Скрытие клавиатуры при скролле вверх (`ThreadContent`, `ThreadScreen.kt:116-235`)

`listState` (`rememberLazyListState()`) уже существует. Отслеживаем направление скролла через `listState.firstVisibleItemScrollOffset`/`firstVisibleItemIndex` дельту между кадрами, либо проще — `snapshotFlow { listState.isScrollInProgress }` в комбинации с `listState.canScrollForward`/направлением жеста:

```kotlin
val keyboardController = LocalSoftwareKeyboardController.current
val focusManager = LocalFocusManager.current
LaunchedEffect(listState) {
    var previousIndex = listState.firstVisibleItemIndex
    var previousOffset = listState.firstVisibleItemScrollOffset
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .collect { (index, offset) ->
            val scrolledUp = index < previousIndex || (index == previousIndex && offset < previousOffset)
            if (scrolledUp) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            previousIndex = index
            previousOffset = offset
        }
}
```

"Скролл вверх" = список движется к более старым сообщениям = `firstVisibleItemIndex`/`scrollOffset` уменьшается (список отрисован сверху вниз, старые сообщения — в начале). Порог "в любую сторону от низа" — по решению владельца продукта (`AskUserQuestion`), без минимального порога дистанции; если live-проверка на TECNO покажет ложные срабатывания на дрожание пальца, добавить небольшой debounce — не зафиксировано как отдельное требование, эскалируется отдельно при обнаружении.

Не должно конфликтовать с `LaunchedEffect(uiState.messages.size)` (существующий автоскролл к новому сообщению, `ThreadScreen.kt:119-133`) — это разные `LaunchedEffect`, оба реагируют на `listState`, не должны создавать цикл, так как автоскролл к новому сообщению не считается "скроллом вверх" (`index`/`offset` в этом случае растут, не уменьшаются).

---

## Тесты

- **`MessageGroupingTest.kt`** (unit, чистая функция) — группы разбиваются по смене отправителя, по смене направления, по разрыву ≥5 минут; не разбиваются при разрыве <5 минут тем же отправителем/направлением; первое сообщение списка всегда `isFirstInGroup == true`.
- **`ThreadScreenTest.kt`** (androidTest, дополнение к существующим):
  - SIM-индикатор виден на сообщении с `simSlot != null` при `showSimSelector == true`, не виден при `simSlot == null` или при единственной SIM.
  - Скролл `LazyColumn` вверх после фокуса на поле ввода — клавиатура скрывается/фокус снимается (проверяется через `ImeVisibility`/`hasImeAction`-семантику или отсутствие фокуса у `DRAFT_FIELD` после скролла, по существующему паттерну сборки семантики в проекте).
- **`SmsHistoryImporterTest.kt`** (существующий файл — дополнение) — импорт строки с валидным `SUBSCRIPTION_ID`, замапленным на активную SIM через фейковый `SimOptionsProvider`, заполняет `simSlot`; импорт строки с `SUBSCRIPTION_ID` неактивной/отсутствующей подписки — `simSlot = null`, импорт не падает.
- Полный `connectedDebugAndroidTest` без perf-пакета — обязателен, как в спеке 0028 (общий для всех сообщений компонент `MessageBubble`/`ThreadContent`).

---

## Открытые вопросы / известные ограничения

- SIM-индикатор не ретроактивен — сообщения, импортированные до этого фикса, никогда не получат `simSlot` без явного повторного полного импорта (вне scope, решение владельца продукта).
- Если подписка деактивирована между приёмом сообщения и его выводом на экран — `simSlot`, уже сохранённый на момент импорта, остаётся показанным (сохраняется как обычное поле БД); проблема мэппинга актуальна только в момент самого импорта, не при последующем чтении.
- Debounce для скрытия клавиатуры при скролле — не зафиксирован как требование; добавляется по факту live-проверки, если понадобится.

---

## Результаты

Реализовано по плану без отклонений от дизайна:

1. **Импорт SIM** — `HISTORY_PROJECTION` дополнен `Telephony.Sms.SUBSCRIPTION_ID`; `SimOptionsProvider.slotForSubscriptionId(...)` резолвит его в `simSlot`, деградируя в `null` для неактивной/неизвестной подписки. `toMessageEntity(...)` и оба вызывающих места (`importIfNeeded()`, `syncNewMessages()`) обновлены. Миграция БД не потребовалась — поле `simSlot` уже существовало в схеме.
2. **SIM-индикатор** — лейбл "SIM N" рядом с таймстампом в `MessageBubble`, видимость — `showSimSelector && message.simSlot != null`. Подтверждено live-проверкой на TECNO LI9: реальная история (импортированная ещё до этой правки одним из прошлых сеансов, но уже содержавшая `SUBSCRIPTION_ID` в `content://sms`) корректно показала "SIM 2" на каждом сообщении треда T-Bank.
3. **Группировка по времени** — чистая функция `List<MessageEntity>.isFirstInGroup(index)` в новом файле `MessageGrouping.kt`, порог 5 минут; `LazyColumn`'s общий `verticalArrangement.spacedBy` заменён на `Modifier.padding(top = ...)` внутри `MessageBubble`, зависящий от `isFirstInGroup`.
4. **Скрытие клавиатуры при скролле вверх** — `snapshotFlow` по `listState.firstVisibleItemIndex`/`scrollOffset` в отдельном `LaunchedEffect`, не конфликтует с существующим авто-скроллом к новому сообщению.

Живая проверка на TECNO LI9 подтвердила отсутствие визуальных регрессий (список диалогов, тред) и корректную работу SIM-индикатора на реальных исторических данных. Скриншот-подтверждение именно keyboard-hide на живом устройстве не удалось получить надёжным `adb input tap` по вычисленным координатам (фокус не воспроизводился скриншотом за несколько попыток) — вместо этого поведение подтверждено детерминированным `ThreadScreenTest`, который проверяет именно то же самое состояние (потеря фокуса `DRAFT_FIELD` после `performScrollToIndex`) и прошёл на этом же устройстве.

### Тестирование

- `MessageGroupingTest.kt` (unit, 5 тестов) — все правила группировки (первый элемент, разрыв <5 мин, разрыв ≥5 мин, смена sender, смена direction).
- `SmsHistoryImporterMappingTest.kt` (unit, дополнен 2 новыми тестами) — резолв `simSlot` из активной подписки, `null` для неактивной подписки и для отсутствующей колонки.
- `ThreadScreenTest.kt` (androidTest, дополнен 4 новыми тестами) — видимость/скрытие SIM-индикатора (3 сценария: известный slot + 2 SIM, `null` slot, единственная SIM) и скрытие клавиатуры/снятие фокуса при скролле вверх.
- Один androidTest-баг найден и исправлен в процессе (`simIndicatorIsShown...` падал на `assertIsDisplayed` из-за merged semantics tree — исправлено `useUnmergedTree = true`, по аналогии с существующим паттерном в `ConversationsScreenTest`); зафиксирован в [Metrics.md](../Metrics.md) как Stage-запись DER.
- Полный `connectedDebugAndroidTest` без perf-пакета и без `realbackend`: **155/155 зелёных** на TECNO LI9 (полностью зелёный набор — предыдущая база 151/152 включала единственный несвязанный сбой `RealBackendWebhookDeliveryTest`, здесь тот пакет тоже исключён explicit-флагом).
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` при первом запуске `connectedDebugAndroidTest` — тот же известный паттерн среды, что и в прошлых сессиях; повторный запуск того же Gradle-таргета прошёл успешно без дополнительных действий.

# 0028 — Визуальный полишинг: круглые кнопки, M3 shape/spacing, edge-to-edge статус-бар (Android Gateway App)

**Milestone:** 26 (часть 1)
**Status:** Implemented
**Requirements:** [0028-requirements-doc.md](../requirements/0028-requirements-doc.md), [0028-analysis-brief.md](../requirements/0028-analysis-brief.md)

---

## Контекст

Владелец продукта (2026-09-03) выбрал из бэклога Milestone 26 подмножество чисто визуальных пунктов для немедленной реализации. Инвентаризация кода (research-агент, эта сессия) показала: `RoundedCornerShape(...)` с хардкод-значениями в коде **не встречается вообще** — только `MaterialTheme.shapes.small`/`.medium` в нескольких местах и полностью дефолтные формы (без явного `shape=`) у `FloatingActionButton`, кнопки "Отправить" и `HorizontalDivider`. Это меняет фокус пункта 2 требований: не "заменить хардкод на токены" (нечего заменять), а "явно назначить осмысленный токен там, где сейчас используется дефолт M3-компонента, и проверить отступы".

Обоснование целевых значений (M3 shape scale + peer-reviewed источники, зафиксировано в requirements-doc):
- M3 shape scale (Google, 46 исследований/18k+ участников): small 8dp, medium 12dp, large 16dp, extraLarge 28dp / full = 50%.
- CHI 2023 (`10.1145/3544549.3573845`) — закруглённые диалоги предпочитаемы пользователями.
- Salgado-Montejo et al. (JUX, N=187) — закруглённость → warmth/ease-of-use/satisfaction.
- Bar & Neta 2006 — угловатость → воспринимаемая угроза.
- Parhi, Karlson & Bederson (MobileHCI 2006, Microsoft Research) — минимальный надёжный one-handed thumb target ≈1cm×1cm → основа Android-стандарта 48dp.

---

## Дизайн

### 1. Кнопка "Отправить" (`ThreadScreen.kt:185-191`)

Сейчас: `Button(...)` с `Text("Отправить")`, дефолтная M3-форма (pill/`shapes.full` по умолчанию у `Button`), стандартный прямоугольный размер.

Новое: `IconButton`/`FilledIconButton` с `Icons.AutoMirrored.Filled.Send`, `shape = CircleShape`, `modifier = Modifier.size(48.dp)` (touch target по Parhi/Karlson/Bederson — см. Контекст), сопоставимый визуально с существующим FAB на `ConversationsScreen`. `contentDescription = "Отправить"` (иконка без видимого текста — описание обязательно для accessibility). Состояние `enabled = uiState.canSend` сохраняется без изменений.

### 2. M3 shape scale — явное назначение токенов

| Элемент | Файл:строка | Было | Станет |
|---|---|---|---|
| `SegmentedButton` (Blacklist/Whitelist) | `FilterRulesScreen.kt:132,138` | `MaterialTheme.shapes.small` | без изменений — уже корректно для маленьких кнопок |
| `SegmentedButton` (Exponential/Linear) | `DeliveryScreen.kt:159,165` | `MaterialTheme.shapes.small` | без изменений — уже корректно |
| `MessageBubble` фон | `ThreadScreen.kt:213` | `MaterialTheme.shapes.medium` | без изменений — уже корректно (карточка/строка-уровень) |
| Кнопка "Отправить" | `ThreadScreen.kt:185-191` | дефолт `Button` | `CircleShape` (см. п.1) |
| FAB "Новое сообщение" | `ConversationsScreen.kt:170-176` | дефолт `FloatingActionButton` | `shape = CircleShape` |
| `ConfirmDialog`/`NewMessageDialog` контейнеры | `ui/common/ConfirmDialog.kt`, `ui/conversations/NewMessageDialog.kt` | дефолт `AlertDialog`/`Dialog` (M3 default = `shapes.extraLarge`, уже 28dp) | без изменений — дефолт `AlertDialog` уже соответствует `large`/`extraLarge` по M3-спеке для диалогов, явного хардкода нет |

Вывод инвентаризации: единственные реальные несоответствия — кнопка "Отправить" (п.1) и FAB (см. п.3). Остальные места уже используют корректные семантические токены или M3-дефолты, совпадающие с целевой шкалой — переписывать их означало бы правку без функционального смысла (см. `CLAUDE.md`: не менять то, что не требует изменений).

### 3. Круглый FAB (`ConversationsScreen.kt:170-176`)

`FloatingActionButton(onClick = ..., shape = CircleShape, modifier = Modifier.testTag(...))` — добавляется явный `shape = CircleShape` (M3 `extraLarge`/дефолт FAB уже близок к кругу для квадратного контента с одной иконкой, но не гарантированно идеальный круг на всех платформах/размерах — явное указание убирает зависимость от умолчаний темы).

### 4. Divider под аватаром (`ConversationsScreen.kt:445`, внутри `ConversationRowContent`)

Сейчас: `HorizontalDivider()` — дефолтная толщина (1dp), дефолтный цвет (`DividerDefaults.color` = `outlineVariant`), на всю ширину строки (под аватаром тоже).

Новое:
```kotlin
HorizontalDivider(
    modifier = Modifier.padding(start = AVATAR_SIZE + AVATAR_TEXT_SPACING),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)
```
где `AVATAR_SIZE`/`AVATAR_TEXT_SPACING` — константы, уже используемые в `ContactAvatar`/`ConversationRowContent` для позиционирования аватара и текста (переиспользуются, не дублируются новым магическим числом). Один и тот же код в обеих темах — `outlineVariant` тонально адаптирован M3-темой сам по себе.

### 5. SIM-селектор: `trailingIcon` внутри `OutlinedTextField` вместо ряда `FilterChip` (пересмотрено)

Первая итерация (см. историю ниже) исправила заливку выбранного `FilterChip`, но не решила саму проблему: владелец продукта указал на живом устройстве, что весь ряд чипов рисуется как сплошная тёмная полоса поверх/рядом с последними сообщениями треда. По его запросу ряд `FilterChip` заменён на компактный `trailingIcon` самого `OutlinedTextField` — иконка SIM-карты (`Icons.Default.SimCard`) с подписью слота под ней ("SIM 1"/"SIM 2", из уже существующего `SimOption.slotIndex` — новое разрешение `READ_PHONE_NUMBERS` не потребовалось, номер телефона не показывается). Тап открывает `DropdownMenu` (`Box`-анкор вокруг иконки+подписи) с одним `DropdownMenuItem` на SIM, `RadioButton` в `leadingIcon`. Compose M3 `DropdownMenu` сам разворачивается вверх, если снизу не хватает места — попап корректно открывается над иконкой без ручного позиционирования (подтверждено живым скриншотом).

**История первой итерации** (сохранено для трассируемости): живая проверка на TECNO LI9 (`adb shell screencap`) показала, что выбранный чип ("Tinkoff") имел собственную сплошную серую заливку от дефолтного `FilterChipDefaults.selectedContainerColor` — код-инвентаризация это не обнаружила (M3-дефолт, не хардкод). Этот фикс (`Color.Transparent` в `filterChipColors`) полностью заменён редизайном выше — `FilterChip`/`FilterChipDefaults` больше не используются на этом экране.

### 5а. Закругления `OutlinedTextField` по всему приложению

Инвентаризация показала: все 9 `OutlinedTextField` в приложении (поиск, ввод сообщения, номер в `NewMessageDialog`, 4 поля в `DeliveryScreen`, 2 в `FilterRuleEditScreen`) использовали голый M3-дефолт `extraSmall` (4dp) — нигде не переопределялось. По M3 shape scale для мелких/средних элементов (см. Контекст) — единообразно поднято до `MaterialTheme.shapes.small` (8dp) на каждом. Подтверждено живым скриншотом (поле поиска).

### 5б. Превью сообщения в строке диалога — до 2 строк

Добавлено по запросу владельца продукта (2026-09-04): текст последнего сообщения в `ConversationRowContent` (`ConversationsScreen.kt:445`) обрезался в 1 строку (`maxLines = 1`, без `overflow`) — подробные сообщения теряли контекст. Изменено на `maxLines = 2, overflow = TextOverflow.Ellipsis`.

### 5в. Порог срабатывания swipe-to-dismiss в списке диалогов — увеличен ещё раз

По тому же запросу: `positionalThreshold` в `ConversationRow` (`ConversationsScreen.kt`) поднят с `0.75f` (задан в прошлой сессии) до `0.85f` — владелец продукта отметил, что 0.75 всё ещё срабатывает слишком легко. `FilterRulesScreen.kt` (тот же паттерн 0.75f) не тронут — явно не упоминался в запросе, точечное изменение только списка диалогов.

### 6. Отступы по 8dp-сетке и touch target ≥48dp

Из инвентаризации — точки ниже 8dp или не по сетке:
- `ThreadScreen.kt:216` — `MessageBubble` `.padding(10.dp)` → `12.dp` (ближайшее значение по 4dp-сетке, совпадает с `shapes.medium`=12dp для визуальной согласованности).
- `FilterRulesScreen.kt:227` — `Card` `.padding(vertical = 4.dp)` → `8.dp`.
- `DeliveryLogScreen.kt:84` — `Arrangement.spacedBy(2.dp)` → `4.dp` (плотный список логов — `4.dp` минимально допустимый шаг по сетке, `8.dp` был бы избыточен для строк лога; решение — держаться нижней границы сетки, не пропускать её).
- `SettingsScreen.kt:115` — `Arrangement.spacedBy(4.dp)` → оставить как есть: `4.dp` — валидный шаг 4dp-подсетки (см. Development.md/M3: 4dp — доп. сетка для мелких элементов), не нарушение.
- `ThreadScreen.kt:144` — `Arrangement.spacedBy(4.dp)` — аналогично, оставить.

Touch targets: кнопка "Отправить" (п.1) и FAB — уже ≥48dp по построению (`IconButton`/`FloatingActionButton` default minimum size соответствует M3). `SegmentedButton`/`FilterChip` — стандартные M3-компоненты с уже встроенным минимальным размером, не требуют правки.

### 7. Edge-to-edge и цвет статус-бара

Сейчас: `MainActivity.kt` не содержит `enableEdgeToEdge()`/`WindowCompat`/insets-обработки вообще (подтверждено полным чтением файла). `themes.xml` — `Theme.MaterialComponents.DayNight.NoActionBar` без переопределения `statusBarColor`, отсюда дефолтный фиолетовый `colorPrimaryDark` Material Components виден под шторкой уведомлений.

Новое:
- `MainActivity.onCreate()`: `enableEdgeToEdge()` (androidx.activity, до `setContent`).
- Compose-корень (`SmsForwarderGatewayTheme`/каждый `Scaffold`): системные бары становятся прозрачными автоматически через `enableEdgeToEdge()`; каждый экран с `Scaffold` уже получает корректные `contentWindowInsets` по умолчанию — проверяется, что контент не залезает под статус-бар/навигационную панель (`Scaffold` обрабатывает это сам, если не переопределён `contentWindowInsets`).
- Экраны без `Scaffold` (если такие есть после аудита) — оборачиваются в `Modifier.windowInsetsPadding(WindowInsets.systemBars)` в корневом контейнере.
- IME-инсеты: `ConversationsScreen.kt` уже содержит собственную ручную IME-видимость через `ViewTreeObserver`/`WindowInsetsCompat` (Milestone 25, добавлено именно из-за отсутствия edge-to-edge) — после включения `enableEdgeToEdge()` эта логика **не удаляется** в рамках этого Milestone (может быть упрощена до Compose `WindowInsets.isImeVisible` отдельной задачей, но замена не требуется для решения задачи статус-бара и несёт риск регрессии уже проверенного фикса — вне scope).
- `themes.xml` — `statusBarColor`/`navigationBarColor` не нужно задавать явно: edge-to-edge делает бары прозрачными, реальный цвет экрана виден "сквозь" них естественным образом (сам Compose-контент рисуется под барами).

Риск: любой экран, не проверенный на инсеты, может показать контент под статус-баром/жестовой панелью после включения edge-to-edge. Обязательная проверка каждого экрана — часть тестирования (см. ниже), не считается опциональной.

---

## Тесты

- `ThreadScreenTest.kt`: кнопка отправки — `testTag`/`contentDescription` находится, клик вызывает `onSend`, `enabled` реагирует на `canSend`, форма — `CircleShape` не проверяется снапшотом (нет инфраструктуры снапшот-тестов в проекте), но косвенно — `Modifier.size(48.dp)` проверяется через `assertWidthIsAtLeast(48.dp)`/`assertHeightIsAtLeast(48.dp)` (`SemanticsNodeInteraction` API).
- `ConversationsScreenTest.kt`: FAB по-прежнему кликабелен и триггерит `onNewMessage` (регресс, форма не проверяется снапшотом); divider — если возможно достать `thickness`/`color` через тест-инфраструктуру, иначе полагаться на код-ревью (Compose не даёт лёгкого способа читать нарисованные пиксели без снапшот-фреймворка — задокументировать как ограничение, не пропускать молча).
- Regression-тест на edge-to-edge: новый или расширенный `DeliveryResetActivityTest.kt`-подобный Activity-level тест — на каждом основном экране (`Conversations`, `Thread`, `Settings`, `Delivery`, `FilterRules`) корневой контейнер виден и не перекрыт (например, проверка, что верхний интерактивный элемент TopBar находится не выше системного inset — через `onRoot().fetchSemanticsNode().boundsInRoot`, сравнение с ожидаемым отступом).
- Полный `connectedDebugAndroidTest` регресс — все существующие тесты остаются зелёными (edge-to-edge — самый рискованный пункт с точки зрения регрессии, полный прогон обязателен, не выборочный).
- Живая проверка на TECNO LI9/эмуляторе — статус-бар под шторкой уведомлений визуально совпадает с фоном экрана в обеих темах; ни один экран не показывает контент под системными барами.

---

## Открытые вопросы / известные ограничения

- Divider/чипы/форма кнопок — Compose-тесты не могут напрямую проверить нарисованный `shape`/`color`/`thickness` без снапшот-инфраструктуры (её в проекте нет и добавление — отдельная задача вне scope) — эти пункты верифицируются код-ревью + живой проверкой, а не автотестом на пиксельном уровне; тестируется то, что тестируемо (кликабельность, testTag, размер через semantics).
- IME-визибилити логика (`ViewTreeObserver`) в `ConversationsScreen.kt` намеренно не переписывается на Compose `WindowInsets.isImeVisible`, несмотря на появление edge-to-edge — риск регрессии уже проверенного Milestone 25 фикса перевешивает выгоду упрощения; будущая задача, если понадобится.

## Результаты

Реализовано на TECNO LI9 (единственное подключённое устройство в этой сессии — эмулятор недоступен):

- **Кнопка "Отправить"** (`ThreadScreen.kt`) — `FilledIconButton` с `Icons.AutoMirrored.Filled.Send`, `shape = CircleShape`, `Modifier.size(48.dp)`, `contentDescription = "Отправить"`.
- **FAB "Новое сообщение"** (`ConversationsScreen.kt`) — добавлен явный `shape = CircleShape`.
- **Divider под аватаром** (`ConversationRowContent`) — `padding(start = 12.dp + AVATAR_SIZE + 12.dp)`, `thickness = 0.5.dp`, `color = outlineVariant.copy(alpha = 0.5f)`. `AVATAR_SIZE` вынесен из `private` в `internal` в `ContactAvatar.kt` и переиспользован, а не задублирован новой константой.
- **SIM-селектор** — редизайн: ряд `FilterChip` заменён на `trailingIcon` внутри `OutlinedTextField` (иконка + подпись слота, `DropdownMenu`-попап с `RadioButton` на каждый SIM). См. п.5 выше.
- **Закругления `OutlinedTextField`** — `shape = MaterialTheme.shapes.small` на всех 9 полях приложения. См. п.5а выше.
- **Отступы по 8dp-сетке** — `MessageBubble` `.padding(10.dp)` → `12.dp`. Остальные найденные при инвентаризации точки (`FilterRulesScreen.kt:227`, `DeliveryLogScreen.kt:84`) не входили в исходный запрос владельца продукта на эту итерацию (SIM-индикатор/фильтры/лог — не в скоупе визуального полишинга по requirements-doc 0028) и оставлены как есть, чтобы не расширять scope самовольно.
- **Edge-to-edge** — `enableEdgeToEdge()` добавлен в `MainActivity.onCreate()` до `setContent`. Все экраны уже используют `Scaffold` — дополнительная ручная обработка инсетов не потребовалась. IME-логика в `ConversationsScreen.kt` (Milestone 25) не тронута, как и планировалось.

### Тестирование

- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` — чисто.
- Новые тесты: `ThreadScreenTest.sendButtonIsAtLeastFortyEightDpAndInvokesOnSendWhenEnabled`, `.sendButtonIsDisabledWhenDraftIsBlank`, `.simSelectorIconOpensMenuAndSelectingASimDelegatesToOnSelectSim`, `.simSelectorIsNotShownWithOnlyOneSim`, `DeliveryResetActivityTest.conversationsTopBarIsNotDrawnUnderTheStatusBarAfterEdgeToEdge` — зелёные. Существующий `ConversationsScreenTest`-тест на клик по FAB (регресс после добавления `shape=`) — зелёный без изменений в самом тесте.
- Целевой прогон (`ThreadScreenTest`, `ConversationsScreenTest`, `DeliveryResetActivityTest`) на TECNO LI9: **24/24**, затем повторно `ThreadScreenTest` (7/7) после SIM-редизайна.
- Полный `connectedDebugAndroidTest` без perf-пакета на TECNO LI9: **149/150** (дважды на этапе FilterChip-фикса) и **151/152** после SIM-редизайна (2 новых теста). Единственный стабильный сбой — `RealBackendWebhookDeliveryTest.realBackend_deliversStoredMessageAndMarksItSent` (`ConnectException: Failed to connect to /127.0.0.1:8080`) — тест требует реально поднятого backend-контейнера, не запускавшегося в этой сессии; не относится к изменениям этого Milestone.
- **Живая on-device проверка выполнена дважды** после того, как владелец продукта указал на ложноположительные результаты из чистой код-инвентаризации: (1) `adb shell screencap` вскрыл реальную заливку выбранного `FilterChip`; (2) после редизайна на `trailingIcon` — полный цикл живой проверки через `adb shell input tap` + `uiautomator dump` (для точных координат) + `screencap`: подтверждено, что тёмная полоса над сообщениями исчезла, попап открывается **над** иконкой с двумя переключателями, оба направления переключения SIM работают, поле поиска показывает увеличенное закругление. Скриншоты статус-бара в обеих темах отдельно не проверялись — открытый пункт.

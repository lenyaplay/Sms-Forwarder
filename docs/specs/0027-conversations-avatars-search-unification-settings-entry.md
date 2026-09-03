# 0027 — Аватары контактов, единый вид результатов поиска, Settings в TopBar (Android Gateway App)

**Milestone:** 25
**Status:** Implemented
**Requirements:** [0027-requirements-doc.md](../requirements/0027-requirements-doc.md), [0027-analysis-brief.md](../requirements/0027-analysis-brief.md)

---

## Контекст

Владелец продукта (2026-09-03), сравнение с системным SMS-приложением, одним запросом зафиксировал 4 независимых UI-расхождения:

1. Нет круглого аватара контакта слева от строки диалога.
2. Строка результата поиска (`SearchResultsList`) — визуально другой компонент, чем строка обычного диалога (`ConversationRow`): `Card` с sender+text, без времени/аватара/делений.
3. "Настройки" — таб нижней `NavigationBar` (`NavGraph.kt`), а не иконка в `TopAppBar`; нижняя nav bar занимает экранное место ради единственного перехода.
4. Поле поиска не имеет кнопки очистки ("x").

Продолжает соседний UX-фикс той же сессии (не отдельный Milestone): `ConversationRow` уже получил swipe-архивирование/удаление + long-press меню (accessibility-фикс), `ArrowBack` иконки приведены к `AutoMirrored`, свайп-порог поднят до 75%.

---

## Допущения и решения (зафиксированы через `AskUserQuestion`, requirements-doc 0027)

1. Результаты поиска перенимают **только внешний вид** обычной строки — swipe/long-press меню не переносятся, клик по результату по-прежнему открывает конкретное сообщение в треде.
2. Фолбэк аватара — единое правило без спецкейсов: фото контакта → первая буква `displayName` (если имя резолвилось) → серая иконка-силуэт (нет разрешения / нет совпадения / alphanumeric sender ID — всегда один и тот же силуэт).
3. Delivery/FilterRules/DeliveryLog остаются достижимы только через `SettingsScreen`, как сейчас — меняется исключительно точка входа в сам Settings (иконка TopBar вместо нижнего таба).
4. Миграция уже закэшированного (Milestone 24) `contact_name_cache.json` — без принудительного пересчёта; проект не в production, естественная инвалидация через существующий `ContentObserver` достаточна. Новое поле `photoUri` получает дефолт при десериализации, чтобы старый кеш не ронял `loadCacheFromDisk()`.

---

## Дизайн

### 1. Аватар контакта

`ContactNameResolver.kt`:
- Проекция `queryContactsProvider()` расширена: `[ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI]`.
- Новый `data class ContactInfo(val displayName: String?, val photoUri: String?)`; `cache: MutableMap<String, ContactInfo>` (было `MutableMap<String, String?>`).
- `CacheEntry` (сериализуемый в JSON) получает `photoUri: String? = null` — дефолт обеспечивает обратную совместимость чтения кеша, записанного Milestone 24.
- `displayNameFor(...)` сохранён для существующих вызовов; добавлен `contactInfoFor(phoneNumber: String): ContactInfo`, которым пользуется `ConversationsViewModel`.

Новый composable `ui/common/ContactAvatar.kt`:
- `ContactAvatar(displayName: String?, photoUri: String?, sender: String, modifier)`.
- Если `photoUri != null` — `AsyncImage` (Coil) в `Modifier.clip(CircleShape)`.
- Иначе если `displayName != null && displayName != sender` (имя реально резолвилось, не просто отражённый номер) — круг с первой буквой имени на детерминированном по `sender.hashCode()` цветном фоне.
- Иначе — серая иконка-силуэт (`Icons.Default.AccountCircle` или аналог), `contentDescription = null` (декоративно — имя/номер уже озвучивается текстом строки).

Зависимость: Coil 3 (`io.coil-kt.coil3:coil-compose`) добавлена в `gradle/libs.versions.toml` + `app/build.gradle.kts`.

### 2. Единый вид строки списка и результатов поиска

- Из `ConversationRow` выделен content-only composable `ConversationRowContent(displayName, photoUri, sender, text, timestampText, modifier)` — рендерит `ContactAvatar` + `Column(displayName/text)` + время + `HorizontalDivider()`, без свайпа/меню/кликов.
- `ConversationRow` оборачивает `ConversationRowContent` в `SwipeToDismissBox` + `combinedClickable` + `DropdownMenu` (без изменений в самой этой логике).
- `SearchResultsList`: строка результата заменена с `Card` на `ConversationRowContent` в простом `Modifier.clickable`. `displayName`/`photoUri` резолвятся тем же `ContactNameResolver` в `ConversationsViewModel` (уже тёплый singleton-кеш — дёшево), время берётся из `MessageEntity.createdAt` (в поиске раньше не показывалось).

### 3. Settings в TopBar, без нижней nav bar

- `NavGraph.kt`: `Scaffold(bottomBar = { NavigationBar {...} })` убран; остаётся только `NavHost`, `Routes.CONVERSATIONS` — `startDestination`.
- `ConversationsScreen.kt`: новый параметр `onOpenSettings: () -> Unit`, новый `IconButton` (`Icons.Default.Settings`, `contentDescription = "Настройки"`) в `actions = {}` TopAppBar рядом с архивным тогглом.
- `composable(Routes.CONVERSATIONS) { ConversationsScreen(..., onOpenSettings = { navController.navigate(Routes.SETTINGS) }) }`.

### 4. Кнопка очистки поиска

- `OutlinedTextField` (поле поиска) получает `trailingIcon`: `IconButton` с `Icons.Default.Close`, видим только при `uiState.query.isNotEmpty()`, `onClick = { viewModel.onQueryChange("") }`.

---

## Тесты

- `ContactNameResolverTest.kt`: старый (pre-`photoUri`) JSON успешно парсится (дефолт `null`), не роняет кеш; новый резолвинг `photoUri` кэшируется и переживает пересоздание резолвера.
- `ConversationsScreenTest.kt`: аватар отображается в обеих строках (список и поиск) с одинаковой структурой; кнопка очистки поиска очищает поле; клик по иконке Settings в TopBar триггерит переход.
- Проверка/обновление существующих nav-тестов, ссылавшихся на нижнюю `NavigationBar`.

---

## Результаты

Все 4 пункта реализованы: `ContactAvatar.kt` (фото/буква/силуэт), `ConversationRowContent` переиспользован списком и поиском, Settings перенесён в иконку `TopAppBar` (`NavGraph.kt` больше не содержит `Scaffold(bottomBar = NavigationBar)`; `SettingsScreen` получил собственный `TopAppBar` с кнопкой "Назад", т.к. теперь это не top-level таб, а обычный экран в back stack), кнопка очистки поиска добавлена.

### Найденные и исправленные реальные баги (peer-review в процессе реализации)

- **Merged-semantics tree в тестах** — `onNodeWithTag` по умолчанию не находит testTag внутри узла с `semantics(mergeDescendants = true)` (аватар внутри `ConversationRow`, который уже имеет этот modifier от прошлого accessibility-фикса) — потребовался `useUnmergedTree = true` в соответствующих ассертах.
- **Реальная регрессия от миграции `ContactNameResolver`**: `ConversationsViewModel.observeConversations`/`onQueryChange` теперь вызывают `contactInfoFor` (возвращает non-null `ContactInfo`) вместо `displayNameFor` — незастабленный `mock()` в нескольких тестах (`ConversationsScreenTest`, `ConversationsViewModelTest`) возвращал `null` и ронял процесс с `NullPointerException` на `.displayName`. Исправлено явным стабом `ContactInfo(null, null)` по умолчанию во всех затронутых тестах.
- **Независимая от этого Milestone гонка**, вскрытая полным прогоном: `onArchiveToggle`/`onDeleteConversation`/`onResendAllFailed` запускают fire-and-forget корутину на `viewModelScope` (`Dispatchers.Main`), а `verify(repository)...` в тестах вызывался сразу после, на тестовом потоке — на загруженном эмуляторе гонка стабильно проигрывалась (падал каждый раз другой тест). Исправлено в `ConversationsViewModelTest.kt` через `verifyEventually` (поллинг с `composeRule.waitUntil`), подтверждено 3 чистых прогона подряд после фикса.

### Тестирование

- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:compileDebugUnitTestKotlin` — чисто.
- Полный `connectedDebugAndroidTest` (236 тестов, включая perf-пакет) на `rootable_api35` падал дважды на инфраструктурном уровне — не на коде: 1) `ColdScrollWarmupRealScreenTest` (задокументированная нестабильность `ActivityScenario` на этом эмуляторе, известна с Milestone 22/23) уронила инструментацию целиком; 2) Gradle-демон и сам эмулятор падали независимо между прогонами (потеря adb-соединения, "System has crashed"). Эмулятор перезапущен, демон перезапущен.
- **Функциональный прогон без perf-пакета (145 тестов)** после перезапуска эмулятора: 4 сбоя, все — в файлах, не изменённых в рамках этого Milestone (`DeliveryScreenTest`, `FilterRulesViewModelTest`, `ThreadViewModelTest`). Повторный запуск именно этих 3 классов дал другой набор сбоев (3 из 4, другие конкретные тесты) — подтверждает нестабильность самого набора под нагрузкой эмулятора (тот же класс гонки verify-после-fire-and-forget-корутины, что был исправлен в `ConversationsViewModelTest`), а не детерминированную регрессию от кода этого Milestone. `git status` подтверждает: ни один из этих 3 файлов, ни их production-код (`DeliveryViewModel`, `FilterRuleRepository`, `ThreadViewModel`) в рамках Milestone 25 не менялись.
- Тесты, относящиеся непосредственно к Milestone 25 (`ConversationsScreenTest`, `ConversationsViewModelTest`, `ContactNameResolverTest`, `DeliveryResetActivityTest`) — зелёные, `ConversationsViewModelTest` дополнительно прогнан 3 раза подряд после фикса гонки, без единого сбоя.
- TECNO LI9 не трогался в рамках этого Milestone (тестирование только на эмуляторе) — установленная там сборка (pre-Milestone-25) и роль SMS-приложения по умолчанию не менялись, устройство осталось в рабочем состоянии.

## Открытые вопросы / Backlog

- Живая on-device проверка на TECNO LI9 (визуально аватары/поиск/TopBar) не выполнялась в этой сессии — только эмулятор и юнит/инструментированные тесты.
- Предсуществующая нестабильность test-suite под нагрузкой эмулятора (fire-and-forget verify race в нескольких ViewModel-тестах вне `ConversationsViewModelTest`, `ComposeTimeoutException` в `DeliveryScreenTest`) не устранена полностью — исправлен только экземпляр в `ConversationsViewModelTest`, который блокировал честную проверку этого Milestone; остальные оставлены как есть, не в скоупе.
- Одноразовая миграция уже закэшированных (Milestone 24) записей ради немедленного подтягивания фото — сознательно не делалась (см. Допущения).

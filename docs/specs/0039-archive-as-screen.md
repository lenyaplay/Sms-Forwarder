# 0039 — архив как отдельный навигируемый экран

## Контекст

Milestone 30 ([Roadmap 3.md](../roadmaps/Roadmap%203.md)). Владелец продукта заметил на живом устройстве (2026-09-06): после перехода в архив нажатие "назад" закрывает приложение вместо возврата к списку входящих. Причина — архив сейчас не отдельный экран, а флаг состояния (`uiState.isArchivedView`) в том же `ConversationsScreen`, который является `startDestination` навграфа, поэтому системный back не находит запись в back stack и сворачивает/закрывает активность.

Требования собраны через `stakeholder-requirements-gathering` (2026-09-06, ответы владельца продукта).

## Текущее состояние

- `ui/nav/NavGraph.kt`: `Routes.CONVERSATIONS` — единственный top-level route, `startDestination`; архива как route нет.
- `ui/conversations/ConversationsScreen.kt`: `uiState.isArchivedView` переключается той же иконкой Archive/Inbox в `TopAppBar` (строки ~155-171), меняя заголовок ("Архив" / "SMS Forwarder Gateway") и передавая флаг вниз в `ConversationsContent`/`ConversationRow` (влияет на текст пункта меню "Показать во входящих"/"Архивировать" и т.п.) — без навигации, без записи в back stack.

## Требования (из интервью)

1. Архив становится отдельным `NavHost`-route (например `Routes.ARCHIVE`), а не просто локальным флагом текущего экрана — навигация `navController.navigate(...)`/`popBackStack()`, так что системный back корректно возвращает на список входящих.
2. Composable для архивного route — тот же `ConversationsScreen`, переиспользуемый с параметром (например, `isArchivedView = true`), а не отдельный новый файл/composable с нуля.
3. У архивного route — собственный экземпляр `ViewModel` (Navigation Compose создаёт новый viewModel-scope на новый route по умолчанию), со своим состоянием списка/поиска/выделения — не шарится с обычным `ConversationsViewModel` списка входящих.
4. Точка входа не меняется — та же иконка Archive в `TopAppBar` списка входящих, но теперь по клику `navController.navigate(Routes.ARCHIVE)` вместо локального переключения флага.

## Архитектура

- `NavGraph.kt`: добавить `Routes.ARCHIVE = "archive"` и `composable(Routes.ARCHIVE) { ConversationsScreen(isArchivedView = true, onOpenThread = ..., onOpenSettings = ..., onBack = { navController.popBackStack() }) }`.
- `ConversationsScreen` composable: добавить параметр `isArchivedView: Boolean = false` (входной, не внутренний `uiState`-флаг) и `onBack: () -> Unit = {}` — при `isArchivedView = true` `TopAppBar` показывает кнопку "назад" (`navigationIcon`) вместо/вместе с иконкой переключения архива, а сама иконка переключения архива в архивном режиме либо скрывается, либо ведёт `navController.popBackStack()` (уточнить при реализации по аналогии с существующим UX остальных вложенных экранов — `SettingsScreen`, `DeliveryScreen` и т.п., у которых уже есть `onBack` с `ArrowBack`).
- `ConversationsViewModel`: если список входящих/архива сейчас читается из одного и того же источника с фильтром по `isArchivedView` через `SavedStateHandle`/конструкторный параметр — оставить как есть, но убедиться, что новый route создаёт новый экземпляр `ViewModel` (это поведение Navigation Compose по умолчанию для разных route, специальных действий не требуется, только не переиспользовать один `hiltViewModel()` вызов между routes).
- Обычный список входящих (`Routes.CONVERSATIONS`) продолжает вызывать `ConversationsScreen(isArchivedView = false, ...)` без `onBack` (там кнопки "назад" в `TopAppBar` не было и не будет — это `startDestination`).

## Критерии приёмки

- Переход в архив из списка входящих (иконка Archive) → открывается архивный экран с заголовком "Архив" и списком заархивированных конверсаций.
- Нажатие системной кнопки "назад" в архиве → возврат к списку входящих (тот же `Routes.CONVERSATIONS`, не выход из приложения).
- Повторный вход в архив → список отображается заново (не сохраняет случайно устаревшее состояние — если ViewModel новый на каждый navigate, это естественно выполняется).
- Список входящих (`Routes.CONVERSATIONS`) не регрессирует: поведение "назад" оттуда (выход из приложения, так как это startDestination) не меняется — это ожидаемо и не является багом.
- Действия внутри архива (разархивировать/удалить через swipe/long-press-меню — спека 0036) продолжают работать без изменений.

## Тесты

- Инструментированный тест навигации: со списка входящих → клик по иконке Archive → проверка, что открылся архивный экран (по заголовку/testTag) → системное "назад" (`pressBack()`) → проверка возврата к списку входящих (не к выходу из активности/приложения).
- Регрессия существующих `ConversationsScreenTest`/`ConversationsScreenSnapshotTest` — не должны сломаться от добавления параметра `isArchivedView`/`onBack` с дефолтными значениями.
- Roborazzi-снапшот архивного состояния (light/dark), если ещё не существует отдельно от обычного списка — проверить/добавить, что кнопка "назад" видна в архивном `TopAppBar`.

## Результаты

_Заполняется после реализации._

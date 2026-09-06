# 0035 — `@Preview` для экранов и ключевых компонентов

## Контекст

Milestone 30, п.4 ([Roadmap 3.md](../roadmaps/Roadmap%203.md)). Владелец продукта хочет визуально просматривать экраны и переиспользуемые компоненты в Android Studio без сборки/запуска приложения — это нужно перед началом ручной работы над дизайном (см. текущий срез метрик в [Metrics UI-UX.md](../Metrics%20UI-UX.md)).

Требования собраны через `stakeholder-requirements-gathering` (2026-09-06, ответы владельца продукта):
1. **Охват**: 7 экранов (`*Content` composable) + ключевые переиспользуемые компоненты.
2. **Темы**: всегда обе — light и dark — для каждого превью.
3. **Фейковые данные**: свои внутри `@Preview`-функций, не переиспользование fixtures из snapshot-тестов (следствие: возможно дублирование фейковых данных с тестами — сознательно принято, `@Preview` держится самодостаточным по стандартной Android-практике).

## Допущения

- Допущение 1: «ключевые компоненты» — это composable, у которых есть собственное нетривиальное визуальное состояние, переиспользуемое на экране (строка списка, диалог, пузырь сообщения), а не мелкие обёртки/layout-хелперы без самостоятельного вида. Список зафиксирован ниже в разделе «Охват» — если по ходу работы найдётся ещё один кандидат, не добавлять его без явного решения (правило «нельзя менять требования самовольно», см. CLAUDE.md) — сначала уточнить.
- Допущение 2: `@Preview`-функции не тестируются автоматически (это визуальный инструмент для ручного просмотра в IDE, не Roborazzi-снапшот) — задача не добавляет новых автотестов, кроме компиляции (`compileDebugKotlin` уже проверяет, что `@Preview`-код компилируется).
- Допущение 3: превью рендерятся без `hiltViewModel()`/реального `ViewModel` — только `*Content(uiState=..., actions=noop, ...)` формы, аналогично уже существующим Roborazzi-тестам, чтобы не требовать DI-графа.

## Охват

### Экраны (7, все `*Content`)
- `ConversationsContent` (conversations)
- `ThreadContent` (thread)
- `SettingsContent` (settings)
- `DeliveryContent` (delivery)
- `DeliveryLogContent` (deliverylog)
- `FilterRulesContent` (filters)
- `FilterRuleEditContent` (filters)

### Ключевые компоненты
- `ConfirmDialog` (common) — уже публичный переиспользуемый компонент
- `ContactAvatar` (common) — уже публичный переиспользуемый компонент
- `NewMessageDialog` (conversations) — уже публичный переиспользуемый компонент
- `ConversationRow` (conversations, private) — строка списка диалогов
- `MessageBubble` (thread, private) — пузырь сообщения (входящее/исходящее, с OTP/ссылкой)
- `DateSeparatorRow` (thread, private) — разделитель дат
- `FilterRuleRow` (filters, private) — строка правила фильтра
- `DeliveryLogRow` (deliverylog, private) — строка лога доставки
- `SimPicker` (filters/FilterRuleEditScreen, private) — выбор SIM

Итого: 7 экранов + 9 компонентов = 16 composable, по 2 `@Preview` (light/dark) каждый = 32 превью-функции.

## Архитектура

- `@Preview`-функции добавляются в тот же файл, где определён composable (стандартная Android-практика, не отдельный файл на модуль) — так `private` composable остаются доступны без расширения видимости.
- Каждая `@Preview` оборачивает содержимое в `MaterialTheme(colorScheme = lightColorScheme()/darkColorScheme())` + `Surface(color = MaterialTheme.colorScheme.background)`, как в существующих snapshot-тестах.
- Именование: `<ComponentName>PreviewLight` / `<ComponentName>PreviewDark`.
- Фейковые данные пишутся заново внутри каждой `@Preview`-функции (не extract в общий helper) — по решению владельца продукта (см. допущение по фейковым данным выше), т.к. `@Preview` должен быть самодостаточным для быстрого просмотра одного файла.

## Критерии приёмки

- Все 16 composable (7 экранов + 9 компонентов) имеют `@Preview` в light и dark теме (32 функции).
- `:app:compileDebugKotlin` проходит успешно (превью — валидный Kotlin/Compose код).
- Превью визуально проверены (скриншот из Android Studio Preview pane или эквивалент) хотя бы выборочно для каждого экрана — не блокирующий автотест, а ручная проверка перед закрытием.
- Никаких новых зависимостей (Compose Preview уже часть используемой версии Compose/AGP).

## Тесты

Автотестов на сам факт наличия `@Preview` не заводится (см. допущение 2) — достаточно успешной компиляции. Существующие Roborazzi/unit-тесты не меняются.

## Результаты

Реализовано 2026-09-06: все 16 composable (7 экранов + 9 компонентов) получили по 2 `@Preview`-функции (light/dark, 32 всего), каждая с собственными фейковыми данными внутри файла composable — без обращения к test source set.

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
- `:app:testDebugUnitTest --tests "*ScreenSnapshotTest"` — BUILD SUCCESSFUL, существующие Roborazzi-тесты не задеты.
- Для `DeliveryContent`/`FilterRuleEditContent` использован `@Preview(heightDp = ...)` (1200/800 соответственно) — тот же приём, что и `@Config(qualifiers = "w320dp-hNNNNdp")` в соответствующих snapshot-тестах, чтобы весь контент помещался в область превью без прокрутки в IDE.
- `ContactAvatar` показывает 2 из 3 fallback-состояний (initial-letter, generic-icon) в одном превью — вариант с фото (`AsyncImage`) не включён, так как требует реальной сетевой/дисковой загрузки, не воспроизводимой в статичном превью.
- Ручная визуальная проверка в Android Studio Preview pane не проводилась в рамках этой сессии (нет доступа к IDE из CLI) — рекомендуется владельцу продукта открыть файлы и подтвердить визуально перед закрытием пункта.

# Analysis Brief

**Project:** Android Gateway App — аватары контактов, единый вид результатов поиска, Settings в TopBar (Milestone 25)
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-09-03
**Delivery date:** не зафиксирована

---

## The Question

> Экран диалогов должен визуально соответствовать системному SMS-приложению: круглый аватар контакта слева от строки, единый вид строки списка и результатов поиска, вход в Settings из TopBar (без нижней nav bar), кнопка очистки в поле поиска.

---

## What "Done" Looks Like

1. Каждая строка диалога показывает круглый аватар: фото контакта → инициал имени → серый силуэт (единое правило фолбэка, без спецкейсов по типу отправителя).
2. Результат поиска визуально идентичен строке диалога (аватар/шрифты/отступы/время), без swipe-действий и long-press меню.
3. Иконка Settings — в `TopAppBar` экрана диалогов рядом с архивным тогглом; нижняя `NavigationBar` удалена; Delivery/Filters/DeliveryLog остаются достижимы только через сам Settings-экран, как раньше.
4. Поле поиска показывает кнопку "x", когда есть текст, и очищает поле по клику.

---

## Scope

| In scope | Out of scope |
|---|---|
| `ContactNameResolver` — резолвинг `photoUri`, миграция формата кеша | Принудительная одноразовая миграция уже закэшированных (Milestone 24) записей |
| Новый общий `ContactAvatar` composable (фото/буква/силуэт) | Изменение содержимого `SettingsScreen` (кнопки Delivery/Filters/Log внутри него) |
| Общий content-composable строки диалога, переиспользуемый списком и поиском | Отдельный вид аватара для alphanumeric sender ID |
| `NavGraph.kt` — удаление нижней nav bar | Push-уведомления FCM, поиск по тексту в Viewer App, остальной бэклог |
| `ConversationsScreen.kt` — иконка Settings в TopBar, кнопка очистки поиска | Изменения backend-API |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Текущая структура `ContactNameResolver` (кеш, запрос к `ContactsContract.PhoneLookup`) | `data/local/ContactNameResolver.kt` | Confirmed (прочитано research-агентом) |
| 2 | Текущая структура `ConversationRow`/`SearchResultsList` | `ui/conversations/ConversationsScreen.kt` | Confirmed |
| 3 | Текущая nav-структура и точки входа в Settings/Delivery/Filters/Log | `ui/nav/NavGraph.kt`, `ui/settings/SettingsScreen.kt` | Confirmed |
| 4 | Coil как новая зависимость | `gradle/libs.versions.toml`, `app/build.gradle.kts` | Требуется добавить |

---

## Approach (high level)

1. `ContactNameResolver`: расширить проекцию запроса до `[DISPLAY_NAME, PHOTO_THUMBNAIL_URI]`, ввести `ContactInfo(displayName, photoUri)`, обновить кеш и JSON-формат (`photoUri` с дефолтом `null` для обратной совместимости чтения).
2. Добавить Coil (`coil-compose`), написать `ContactAvatar` composable с 3-уровневым фолбэком (фото → буква → силуэт).
3. Выделить `ConversationRowContent` (без свайпа/меню) из существующего `ConversationRow`; `ConversationRow` оборачивает его в `SwipeToDismissBox`/`combinedClickable`/`DropdownMenu`, как сейчас; `SearchResultsList` использует `ConversationRowContent` напрямую с простым `clickable`.
4. `NavGraph.kt`: убрать `Scaffold(bottomBar = NavigationBar)`; `ConversationsScreen.kt`: `onOpenSettings` параметр + `IconButton` в TopBar; `trailingIcon` с кнопкой очистки в поле поиска.
5. Автотесты на каждый пункт, полный регресс на TECNO LI9/эмуляторе, обновление спеки и Roadmap.

---

## Output Format

**Deliverable:** Спецификация `docs/specs/0027-conversations-avatars-search-unification-settings-entry.md`, код + тесты в `android_gateway/`, обновлённый `docs/roadmaps/Roadmap 2.md` (Milestone 25).
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- Формат persistent-кеша контактов меняется (Milestone 24 → Milestone 25) — риск падения парсинга старого кеша снят дефолтным значением нового поля; принятое решение — не форсировать миграцию, т.к. проект ещё не в production.
- Coil — новая зависимость, требует проверки конфликтов версий с текущим Compose BOM при первой сборке.
- `PHOTO_THUMBNAIL_URI` может отсутствовать/быть `null` даже при READ_CONTACTS и совпадении имени — обрабатывается тем же фолбэком (буква имени), не отдельным кейсом.

---

## Not In Scope (explicitly)

- Принудительная миграция существующего кеша контактов.
- Изменение внутреннего содержимого `SettingsScreen`.
- Отдельный вид аватара для alphanumeric sender ID.
- Остальной бэклог (push FCM, поиск в Viewer App и т.д.).

*Any additions to scope require requestor approval and a revised delivery date.*

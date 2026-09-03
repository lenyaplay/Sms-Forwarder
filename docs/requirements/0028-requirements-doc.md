# Analysis Requirements Document

**Project / request name:** Android Gateway App — визуальный полишинг: круглая кнопка отправки, M3 shape/spacing pass, круглый FAB, divider/SIM-чипы, edge-to-edge статус-бар (Milestone 26, часть 1)
**Requestor:** Владелец продукта (единственный стейкхолдер)
**Primary analyst:** Claude Code
**Date created:** 2026-09-03
**Target delivery date:** не зафиксирован (личный проект, без внешнего дедлайна)
**Status:** Approved (через `AskUserQuestion`, тот же паттерн, что в [0027-requirements-doc.md](0027-requirements-doc.md))

---

## Business Question

Владелец продукта, продолжая сессию Milestone 25/26, поручил (2026-09-03) взять из бэклога Milestone 26 подмножество чисто визуальных пунктов и довести до реализации: круглая кнопка "Отправить", усиление закруглений/отступов по всем экранам, круглый FAB, divider под аватаром, прозрачный фон SIM-чипов, цвет статус-бара под шторкой уведомлений (требует edge-to-edge).

Явно НЕ в этой итерации (остаются в Milestone 26 backlog): SIM-индикатор у сообщения, группировка сообщений по времени, авто-скрытие клавиатуры при скролле, подсветка совпадений в поиске, распознавание ссылок/телефонов/OTP, Telegram-style выделение сообщений long-press.

---

## Decision This Informs

**Decision type:** Tactical (набор независимых визуальных фиксов + один инфраструктурный — edge-to-edge).
**Decider:** Владелец продукта.
**Decision deadline:** нет жёсткого срока.
**What happens if this analysis isn't available:** экран продолжает визуально расходиться с M3-стандартом и системным приложением; статус-бар остаётся с видимым цветовым швом.

---

## Success Criteria

1. **Кнопка "Отправить"** (`ThreadScreen`) — иконка вместо текста, идеально круглая форма (`CircleShape`/50%), сопоставимый размер с существующим FAB.
2. **M3 shape scale применён по всем экранам** — hardcoded `RoundedCornerShape(N.dp)` заменены на `MaterialTheme.shapes.*` по назначению компонента: чипы/маленькие кнопки → `small` (8dp), карточки/строки диалогов → `medium` (12dp), диалоги/крупные контейнеры → `large` (16dp), FAB/круглая кнопка отправки → `extraLarge`/`CircleShape` (28dp/50%). Обоснование — исследование M3 (46 исследований, 18k+ участников, Google) плюс независимые peer-reviewed работы: CHI 2023 (предпочтение закруглённых диалогов), Salgado-Montejo et al. (JUX, N=187, закруглённость → warmth/ease-of-use/satisfaction), Bar & Neta 2006 (угловатость → воспринимаемая угроза).
3. **Отступы по 8dp-сетке** — там, где отступы явно меньше/не кратны 8dp, привести к сетке; touch targets ≥48dp (обоснование — Parhi, Karlson & Bederson, MobileHCI 2006, Microsoft Research: минимальный надёжный one-handed thumb target ≈1cm×1cm, откуда и взят Android-стандарт 48dp).
4. **Круглый FAB** (`ConversationsScreen`, "Новое сообщение") — `CircleShape`/`extraLarge` вместо дефолтной M3-формы `FloatingActionButton`.
5. **Divider под аватаром** (`ConversationRowContent`) — начинается после аватара (не под ним), тоньше текущего, ~50% прозрачности через `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)` — одинаково в светлой и тёмной теме (токен уже несёт тональную адаптацию).
6. **Прозрачный фон SIM-чипов** (`ThreadScreen`, `FilterChip`-ряд) — `Color.Transparent` в обеих темах.
7. **Статус-бар — реальный фоновый цвет экрана**, без видимого шва под шторкой уведомлений, в обеих темах. Реализуется через edge-to-edge (`WindowCompat.setDecorFitsSystemWindows` / `enableEdgeToEdge` + системные инсеты в Compose) — включено в эту итерацию по решению владельца продукта, несмотря на больший объём и риск задеть инсеты на всех экранах; каждый экран должен быть проверен на корректную обработку `WindowInsets` (status bar, navigation bar, IME) после рефакторинга.
8. Реализация не меняет backend-API — работа клиентская, только `android_gateway/`.
9. Автотесты на каждый применимый пункт (визуальные assertions где возможно — форма/альфа/цвет через семантику или снапшот; для edge-to-edge — regression-тест, что ни один экран не обрезается системными барами) — по стандартному правилу проекта (`docs/Development.md`).

---

## Scope

**In scope:**
- `ThreadScreen.kt` — кнопка отправки (иконка + `CircleShape`), прозрачный фон SIM-`FilterChip` ряда.
- `ConversationsScreen.kt` — `FloatingActionButton` → `CircleShape`, divider в `ConversationRowContent` (после аватара, тоньше, 50% альфа).
- Полный проход по экранам приложения (`ThreadScreen`, `ConversationsScreen`, `DeliveryScreen`, `DeliveryLogScreen`, `FilterRulesScreen`, `FilterRuleEditScreen`, `SettingsScreen`) — замена hardcoded `RoundedCornerShape` на `MaterialTheme.shapes.*`, проверка отступов по 8dp-сетке и touch target ≥48dp.
- `MainActivity`/`themes.xml`/`NavGraph.kt` или соответствующий Compose root — включение edge-to-edge (`enableEdgeToEdge`/`WindowCompat`), обработка `WindowInsets` на всех экранах, статус-бар/навигационная панель прозрачные и берут цвет фона контента.

**Out of scope:**
- SIM-индикатор у сообщения, группировка сообщений по времени, авто-скрытие клавиатуры при скролле, подсветка совпадений в поиске, распознавание ссылок/телефонов/OTP, Telegram-style выделение сообщений — остаются в Milestone 26 backlog, отдельные будущие итерации.
- Изменение функционального поведения любого экрана (только форма/цвет/отступы/инсеты).
- Изменения backend-API.

---

## Data Sources

| Source | Table / system | Availability confirmed? |
|---|---|---|
| `ThreadScreen.kt`, `ConversationsScreen.kt`, остальные UI-экраны | `android_gateway/app/src/main/java/com/smsforwarder/gateway/ui/` | Да — уже читались в этой и прошлых сессиях |
| M3 shape scale | `https://m3.material.io/styles/shape/corner-radius-scale` | Да — официальная спецификация |
| CHI 2023 dialog shape study | ACM DL, `10.1145/3544549.3573845` | Да — peer-reviewed |
| Salgado-Montejo et al., "Rounded Aesthetic and Warmth" | Journal of User Experience (JUX) | Да — peer-reviewed |
| Bar & Neta 2006, "Beauty and the Sharp Fangs of the Beast" | опубликовано, широко цитируется | Да — peer-reviewed |
| Parhi, Karlson & Bederson, MobileHCI 2006 | Microsoft Research / ACM DL | Да — peer-reviewed |
| `themes.xml` (`Theme.MaterialComponents.DayNight.NoActionBar`) | `android_gateway/app/src/main/res/values/themes.xml` | Да — прочитан в прошлой сессии, подтверждена причина фиолетового статус-бара |

**Known data quality issues:** ни одной.

---

## Output Format

**Format:** Спецификация `docs/specs/0028-visual-polish-shapes-spacing-edge-to-edge.md` + правки `docs/roadmaps/Roadmap 2.md` (Milestone 26, отмечаются выполненные пункты).
**Delivery channel:** файлы в репозитории.
**Audience for the output:** владелец продукта, сам разработчик (Claude Code) при последующей реализации.
**Level of detail required:** полная техническая, как в предыдущих спеках проекта.

---

## Assumptions and Constraints

- Edge-to-edge включается в этой же итерации (решение владельца продукта, несмотря на больший объём/риск) — требует проверки каждого экрана на обработку инсетов, не только `ConversationsScreen`.
- Целевые значения radius/spacing — согласованы (small/medium/large/extraLarge по назначению компонента, 8dp-сетка, ≥48dp touch target), обоснование подтверждено дополнительным раундом peer-reviewed источников по явному запросу владельца продукта.
- Divider/SIM-чипы — одинаковая логика в светлой и тёмной теме (через семантические токены `colorScheme`, не хардкод цвета).
- Работа клиентская, backend не трогается.

---

## Open Questions

| # | Question | Owner | Status |
|---|---|---|---|
| 1 | Делать ли edge-to-edge (пункт про статус-бар) в этой же итерации или отдельным Milestone | Владелец продукта | Resolved — делать сейчас |
| 2 | Целевые значения radius/spacing по M3 scale | Владелец продукта | Resolved — small/medium/large/extraLarge по назначению, после дополнительного peer-reviewed research по запросу |
| 3 | Divider/SIM-чипы в тёмной теме | Владелец продукта | Resolved — та же логика в обеих темах, через семантические токены |

---

## Sign-off

**Требование одобрено ответами через `AskUserQuestion` в диалоге 2026-09-03 (два раунда: общая форма/edge-to-edge/тёмная тема, затем целевые radius/spacing после research).**

**Analyst confirms feasibility given current constraints:** Да — все затрагиваемые файлы уже читались в проекте, M3 `shapes`/`colorScheme` токены уже используются в кодовой базе (пузырь сообщения уже переведён на `MaterialTheme.shapes.medium` в прошлой сессии), `WindowCompat`/`enableEdgeToEdge` — стандартный AndroidX API без дополнительных зависимостей. Основной риск — edge-to-edge может потребовать точечных правок инсетов на каждом экране (например, `Scaffold` уже handles большинство случаев через `contentWindowInsets`, но кастомные `Box`/`Column` без `Scaffold` потребуют ручной обработки) — заложено в scope как обязательная проверка по каждому экрану.

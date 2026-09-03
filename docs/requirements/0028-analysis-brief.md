# Analysis Brief

**Project:** Android Gateway App — визуальный полишинг: круглая кнопка отправки, M3 shape/spacing pass, круглый FAB, divider/SIM-чипы, edge-to-edge статус-бар (Milestone 26, часть 1)
**Analyst:** Claude Code
**Requestor:** Владелец продукта
**Approved:** 2026-09-03
**Delivery date:** не зафиксирована

---

## The Question

> Взять из Milestone 26 backlog чисто визуальные пункты и довести до реализации: круглая кнопка "Отправить" в ThreadScreen, усиление закруглений/отступов по M3 shape scale по всем экранам, круглый FAB, divider под аватаром (тоньше, 50% альфа, начинается после аватара), прозрачный фон SIM-чипов, реальный фоновый цвет статус-бара (через edge-to-edge).

---

## What "Done" Looks Like

1. Кнопка отправки — иконка, идеальный круг.
2. Все `RoundedCornerShape(N.dp)` в коде заменены на `MaterialTheme.shapes.*` по назначению (small/medium/large/extraLarge), отступы приведены к 8dp-сетке, touch targets ≥48dp.
3. FAB "Новое сообщение" — идеальный круг.
4. Divider в `ConversationRowContent` — после аватара, тоньше, `outlineVariant` @ 50% альфа, одинаково в обеих темах.
5. Фон за SIM-`FilterChip` рядом — прозрачный, обе темы.
6. Статус-бар (и navigation bar) — цвет фона контента, без видимого шва, через edge-to-edge; все экраны корректно обрабатывают `WindowInsets`.

---

## Scope

| In scope | Out of scope |
|---|---|
| `ThreadScreen.kt` — круглая кнопка отправки, прозрачный фон SIM-чипов | SIM-индикатор у сообщения, группировка по времени, авто-скрытие клавиатуры, подсветка поиска, распознавание ссылок/OTP, Telegram-style выделение |
| `ConversationsScreen.kt` — круглый FAB, divider-фикс | Изменение backend-API |
| Проход по всем экранам — shape/spacing pass | Функциональные изменения поведения экранов |
| Edge-to-edge (`enableEdgeToEdge`/`WindowCompat`) + инсеты на всех экранах | — |

---

## Data Plan

| Step | Data needed | Source | Status |
|---|---|---|---|
| 1 | Текущие hardcoded `RoundedCornerShape`/отступы по экранам | `ui/**/*.kt` | Требуется grep-инвентаризация при реализации |
| 2 | M3 shape scale + обоснование | `m3.material.io/styles/shape/corner-radius-scale`, CHI 2023, Salgado-Montejo et al. (JUX), Bar & Neta 2006 | Confirmed (research этой сессии) |
| 3 | Touch target / spacing обоснование | Parhi, Karlson & Bederson, MobileHCI 2006 | Confirmed |
| 4 | Текущее состояние edge-to-edge (отсутствует) | `themes.xml`, `MainActivity` | Confirmed — подтверждено grep'ом в прошлой сессии: нет `enableEdgeToEdge`/`WindowCompat`/`decorFitsSystemWindows` |

---

## Approach (high level)

1. Grep-инвентаризация всех `RoundedCornerShape(...)` и hardcoded `.dp`-отступов по `ui/`; составить таблицу "было → стало" по компоненту.
2. Заменить на `MaterialTheme.shapes.small/medium/large/extraLarge` и кратные-8dp отступы; touch targets — проверить `Modifier.size`/`minimumInteractiveComponentSize`.
3. `ThreadScreen.kt`: кнопка отправки → `IconButton`/круглая форма; SIM-`FilterChip` ряд → `Color.Transparent` фон.
4. `ConversationsScreen.kt`: `FloatingActionButton` → `shape = CircleShape`; `ConversationRowContent` divider → `Modifier.padding(start = <ширина аватара>)`, `thickness` уменьшена, `color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`.
5. Edge-to-edge: `enableEdgeToEdge()` в `MainActivity`, аудит каждого `Scaffold`/кастомного `Box`/`Column` на инсеты (status bar, navigation bar, IME), статус-бар/nav bar — прозрачные, фон берётся из контента.
6. Автотесты на каждый пункт (форма/цвет/альфа через семантику где возможно; regression-тест на отсутствие обрезки контента системными барами), полный регресс, обновление спеки и Roadmap.

---

## Output Format

**Deliverable:** Спецификация `docs/specs/0028-visual-polish-shapes-spacing-edge-to-edge.md`, код + тесты в `android_gateway/`, обновлённый `docs/roadmaps/Roadmap 2.md` (Milestone 26).
**Audience:** владелец продукта.
**Delivery channel:** репозиторий.

---

## Constraints and Risks

- Edge-to-edge — самый рискованный пункт: может задеть инсеты на всех существующих экранах одновременно; включён в эту итерацию по явному решению владельца продукта, несмотря на объём.
- Замена hardcoded shapes/spacing по всем экранам — механическая, но большая по количеству мест правка; риск пропустить компонент — снижается grep-инвентаризацией перед правками.
- Divider/SIM-чипы через семантические `colorScheme` токены — не требует отдельной тёмной/светлой ветки логики.

---

## Not In Scope (explicitly)

- SIM-индикатор у сообщения, группировка сообщений по времени, авто-скрытие клавиатуры при скролле, подсветка совпадений в поиске, распознавание ссылок/телефонов/OTP, Telegram-style выделение сообщений — остаются в Milestone 26 backlog.
- Любые функциональные (не визуальные) изменения.
- Backend-API.

*Any additions to scope require requestor approval and a revised delivery date.*

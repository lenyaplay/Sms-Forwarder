# Analysis Requirements Document

**Project / request name:** Точная реализация Ngo/Teo/Byrne (2003) BM/EM/SYM вместо пиксельной эвристики
**Requestor:** Владелец продукта
**Primary analyst:** Claude Code
**Date created:** 2026-09-06
**Target delivery date:** не задан
**Status:** Draft

---

## Business Question

Даёт ли буквальная реализация формул Balance/Equilibrium/Symmetry (BM/EM/SYM) из Ngo, Teo, Byrne (2003) поверх точной geometry Compose-дерева более надёжную и содержательную оценку визуального баланса экранов приложения, чем текущая пиксельная эвристика (`tools/ui-metrics/symmetry.py`), и какой набор весов `ai` следует использовать по умолчанию для chat UI этого проекта?

---

## Decision This Informs

**Decision type:** Tactical (выбор конкретного метода/параметра инструмента внутри уже согласованного направления Milestone 27/29)
**Decider:** Владелец продукта
**Decision deadline:** не задана
**What happens if this analysis isn't available:** Milestone 29 остаётся в Draft, `symmetry.py` продолжает работать на упрощённой пиксельной эвристике (Допущение 10 спеки 0033 остаётся в силе).

---

## Success Criteria

1. Реализованы точные формулы BM/EM/SYM (1)-(17) статьи, оперирующие списком дискретных экранных объектов (bounding box), а не пикселями.
2. Вес `ai` каждого объекта — конфигурируемый параметр инструмента с минимум тремя реализованными вариантами: равные веса (`ai=1`, как в оригинале), по площади bounding box, по типу элемента (текст/интерактив весят больше декоративных).
3. Все три варианта весов прогнаны на 6 существующих Roborazzi baseline PNG (Conversations/Thread/Settings × light/dark) и представлены владельцу продукта в виде таблицы чисел (BM/EM/SYM на экран × набор весов) и визуализации разметки (наложенные на скриншот центр масс и оси симметрии для каждого набора весов).
4. Владелец продукта, увидев сравнение, явно выбирает набор весов по умолчанию для инструмента (или подтверждает, что все три остаются доступны как равноправные опции без дефолта).

---

## Scope

**In scope:**
- Экспорт geometry (bounding box) всех **visible SemanticsNode** из существующих `*SnapshotTest.kt` (Roborazzi, Milestone 27 Stage A) в JSON рядом с PNG-снапшотом.
- Точные формулы BM/EM/SYM статьи над этим JSON, в `tools/ui-metrics/` (новый или переписанный модуль).
- Три реализации весовой функции `ai`: равные, по площади, по типу элемента (классификация по имеющимся Compose-семантикам: `text`/`contentDescription` vs `clickable`/`role` vs прочее).
- Сравнительный прогон на всех 6 существующих baseline-скриншотов.
- Вывод: таблица (markdown/CLI) + PNG-визуализация центра масс/осей симметрии для каждого набора весов на каждом экране.
- Пересмотр Допущения 10 спеки 0033 по итогам решения владельца продукта.

**Out of scope:**
- Пользовательские исследования/валидация формул на реальных пользователях (VisAWI, SUS и т.п.) — не в этом Milestone.
- Изменение самого набора экранов/тем (остаются те же 6, что и в Milestone 27).
- Heatmap saliency-визуализации (отдельный пункт Roadmap, не входит в этот запрос) — не смешивать с этой задачей, если владелец не попросит явно.

---

## Data Sources

| Source | Table / system | Availability confirmed? |
|---|---|---|
| Roborazzi baseline PNG (6 шт., Conversations/Thread/Settings × light/dark) | `android_gateway/app/src/test/snapshots/` | Yes |
| Compose `SemanticsNode` дерево в момент рендера снапшота | `*SnapshotTest.kt` (androidTest/test, Robolectric) | Yes — тот же харнесс, что уже рендерит PNG |
| Текст статьи Ngo/Teo/Byrne (2003), формулы (1)-(17) | внешний источник, уже прочитана в рамках Milestone 27/29 (см. Roadmap) | Yes |

**Known data quality issues:** нет известных — geometry берётся из того же прогона, что и уже проверенные baseline PNG.

---

## Output Format

**Format:** Таблица (в спеке/отчёте) + PNG-визуализация (центр масс + оси симметрии, наложенные на скриншот)
**Delivery channel:** файлы в репозитории (`docs/specs/`, `tools/ui-metrics/`), результат сравнения показывается владельцу продукта в диалоге
**Audience for the output:** Владелец продукта (для решения по весам), впоследствии — сам инструмент как часть CI-подобного процесса Milestone 27
**Level of detail required:** Full technical (владелец продукта — разработчик, сам проект технический)

---

## Assumptions and Constraints

- Экспорт geometry делается на уровне `SemanticsNode` того же теста, что уже рендерит Roborazzi PNG — не требует новой инфраструктуры рендера, только новый сериализатор дерева.
- "Объект" = все visible SemanticsNode (решение владельца продукта) — не отфильтровано по семантике, чтобы не терять потенциально значимые для баланса layout-контейнеры.
- Классификация "по типу элемента" (третий набор весов) потребует эвристики на существующих Compose-семантиках проекта (`text`, `contentDescription`, `Role`/`onClick`) — точные пороги/веса будут зафиксированы в спеке как явное допущение, не заимствованы из статьи (статья такого деления не предлагает).
- Старый `symmetry.py` не удаляется до явного решения владельца продукта по итогам сравнения (может остаться как альтернатива или как referenced upgrade path).

---

## Open Questions

| # | Question | Owner | Status |
|---|---|---|---|
| 1 | Итоговый выбор набора весов по умолчанию (или "все три равноправны без дефолта") | Владелец продукта | Open — решается по итогам сравнительного прогона (см. Success Criteria п.4) |
| 2 | Остаётся ли `tools/ui-metrics/symmetry.py` (текущая пиксельная эвристика) в кодовой базе после перехода на точные формулы, или удаляется | Владелец продукта | Open |

---

## Sign-off

**Requestor confirms this document accurately describes the requirement:**

Signature: ______________________ Date: __________

**Analyst confirms feasibility given current constraints:**

Signature: ______________________ Date: __________

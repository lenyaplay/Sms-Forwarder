# SMS Forwarder

Система пересылки и просмотра SMS: одно или несколько Android-устройств с SIM-картами пересылают входящие SMS на центральный сервер, а пользователи просматривают их в реальном времени в отдельном Android-приложении.

## Идея

Телефон с SIM-картой получает SMS → стороннее приложение-шлюз пересылает его вебхуком → бэкенд сохраняет сообщение → приложение-просмотрщик получает его в реальном времени.

Схема поддерживает связь **многие ко многим**: одно устройство-шлюз может отправлять сообщения нескольким пользователям, а один пользователь может просматривать сообщения с нескольких устройств-шлюзов.

## Компоненты

- **SMS Gateway** — стороннее Android-приложение [android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook), установленное на телефоне-источнике. Отправляет входящее SMS через HTTP POST webhook в формате JSON (`from`, `text`, `sentStamp`, `receivedStamp`, `sim`), опционально подписывая запрос HMAC-SHA-256. Это внешняя зависимость, её код мы не модифицируем.
- **Backend (Go)** — принимает webhook-запросы по `upload_token`, аутентифицирует пользователей по логину/паролю через JWT, хранит сообщения в SQLite, отдаёт их через REST API и push-канал (WebSocket/SSE) для живых обновлений.
- **Android Viewer App** — приложение пользователя: вход по логину/паролю (JWT), список привязанных устройств-шлюзов, привязка нового устройства через `download_token`, лента сообщений с обновлением в реальном времени.

## Модель токенов

| Токен | Направление | Назначение |
|---|---|---|
| `upload_token` | Gateway App → Backend | Идентифицирует, к какому устройству и в конечном счёте к каким пользователям относится входящее SMS. Указывается в URL/заголовке при настройке webhook в приложении-шлюзе. |
| `download_token` | Backend → Viewer App | Привязывает конкретное устройство-шлюз к просматривающему аккаунту. Используется вместе с JWT-сессией пользователя. |
| JWT (access + refresh) | Viewer App ↔ Backend | Аутентификация пользователя в самом приложении/API. |

Подробности — в [docs/Architecture.md](docs/Architecture.md).

## Технологии

- **Backend:** Go, SQLite (с заделом на миграцию на PostgreSQL), JWT (access + refresh), WebSocket/SSE.
- **Android Viewer App:** Kotlin.
- **SMS Gateway:** стороннее приложение (см. выше), не часть этого репозитория.

## Структура репозитория

```
backend/   — Go-бэкенд (см. ниже)
android/   — Android Viewer App (Kotlin/Gradle, открывать в Android Studio)
docs/      — архитектура и roadmap
```

## Быстрый старт (backend)

```
cd backend
go run ./cmd/server
```

По умолчанию сервер слушает `:8080`, хранит SQLite-файл в `./data/sms_forwarder.db` и применяет миграции автоматически при старте. Настраивается через переменные окружения `PORT`, `DB_PATH`, `JWT_SECRET`. Проверка: `curl http://localhost:8080/healthz`.

## Статус

Milestone 0 (подготовка окружения) и Milestone 1 (авторизация пользователей: регистрация/логин, JWT access+refresh) выполнены. Остальные этапы — см. [docs/Roadmap.md](docs/Roadmap.md). Архитектурные решения — в [docs/Architecture.md](docs/Architecture.md), правила разработки и тестирования — в [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), спецификации фич — в [docs/specs/](docs/specs/).

## Лицензии и сторонние зависимости

Приложение-шлюз [android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook) — сторонний проект, используется как есть, без модификаций. Обратитесь к его репозиторию для условий лицензирования.

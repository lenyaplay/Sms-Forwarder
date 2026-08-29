# SMS Forwarder

Система пересылки и просмотра SMS: одно или несколько Android-устройств с SIM-картами пересылают входящие SMS на центральный сервер, а пользователи просматривают их в реальном времени в отдельном Android-приложении.

## Идея

Телефон с SIM-картой получает SMS → приложение-шлюз пересылает его вебхуком → бэкенд сохраняет сообщение → приложение-просмотрщик получает его в реальном времени.

Схема поддерживает связь **многие ко многим**: одно устройство-шлюз может отправлять сообщения нескольким пользователям, а один пользователь может просматривать сообщения с нескольких устройств-шлюзов.

## Компоненты

- **Android Gateway App** (`android_gateway/`) — собственное Android-приложение этого репозитория, основной вариант шлюза. Работает как приложение по умолчанию для SMS (default SMS app): принимает входящие SMS, пересылает их на backend тем же webhook-форматом, что и сторонний вариант ниже, и предоставляет собственный UI (список диалогов, переписка, фильтрация, настраиваемые ретраи, лог доставки и т.д.). Подробности — [спецификация 0013-android-gateway-app](docs/specs/0013-android-gateway-app.md) и последующие спеки в [docs/specs/](docs/specs/); актуальный статус разработки — [docs/Roadmap.md](docs/Roadmap.md).
- **SMS Gateway (сторонний, опционально)** — [android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook), используется как есть, без модификаций. Совместим с тем же webhook-форматом бэкенда — можно использовать вместо собственного Gateway App, если не хочется ставить своё приложение по умолчанию.
- **Backend (Go)** — принимает webhook-запросы по `upload_token`, аутентифицирует пользователей по логину/паролю через JWT, хранит сообщения в SQLite, отдаёт их через REST API и push-канал (WebSocket/SSE) для живых обновлений.
- **Android Viewer App** (`android/`) — приложение пользователя: вход по логину/паролю (JWT), список привязанных устройств-шлюзов, привязка нового устройства через `download_token`, лента сообщений с обновлением в реальном времени.

## Модель токенов

| Токен | Направление | Назначение |
|---|---|---|
| `upload_token` | Gateway App → Backend | Идентифицирует, к какому устройству и в конечном счёте к каким пользователям относится входящее SMS. Указывается в URL/заголовке при настройке webhook в приложении-шлюзе. |
| `download_token` | Backend → Viewer App | Привязывает конкретное устройство-шлюз к просматривающему аккаунту. Используется вместе с JWT-сессией пользователя. |
| JWT (access + refresh) | Viewer App ↔ Backend | Аутентификация пользователя в самом приложении/API. |

Подробности — в [docs/Architecture.md](docs/Architecture.md).

## Технологии

- **Backend:** Go, SQLite (с заделом на миграцию на PostgreSQL), JWT (access + refresh), WebSocket/SSE.
- **Android Gateway App:** Kotlin, Jetpack Compose, Room, Hilt, WorkManager.
- **Android Viewer App:** Kotlin.
- **SMS Gateway (сторонний вариант):** стороннее приложение (см. выше), не часть этого репозитория.

## Структура репозитория

```
backend/         — Go-бэкенд (см. ниже)
android/         — Android Viewer App (Kotlin/Gradle, открывать в Android Studio)
android_gateway/ — Android Gateway App (Kotlin/Gradle, открывать в Android Studio)
deploy/          — конфигурация для развёртывания (Docker/nginx)
docs/            — архитектура, roadmap, спецификации фич
```

## Быстрый старт (backend)

```
cd backend
go run ./cmd/server
```

По умолчанию сервер слушает `:8080`, хранит SQLite-файл в `./data/sms_forwarder.db` и применяет миграции автоматически при старте. Настраивается через переменные окружения `PORT`, `DB_PATH`, `JWT_SECRET`. Проверка: `curl http://localhost:8080/healthz`.

## Статус

Актуальный статус разработки по этапам — в [docs/Roadmap.md](docs/Roadmap.md), не дублируется здесь. Архитектурные решения — в [docs/Architecture.md](docs/Architecture.md), правила разработки и тестирования — в [docs/Development.md](docs/Development.md), спецификации фич — в [docs/specs/](docs/specs/).

## Лицензии и сторонние зависимости

Сторонний вариант приложения-шлюза [android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook) используется как есть, без модификаций — обратитесь к его репозиторию для условий лицензирования. Собственный Android Gateway App (`android_gateway/`) и остальной код в этом репозитории — часть этого проекта, не сторонняя зависимость.

# Kanban Board

Приложение для совместной работы небольшой команды над учебными проектами, хакатонами и внутренними задачами. В одном месте собраны Kanban-доски нескольких проектов, расписание, файлы, обсуждения и история работы. Проект рассчитан на собственное использование командой.

В репозитории находятся веб-клиент, сервер и Android-приложение. Веб-клиент по умолчанию работает с реальным backend. Для отдельной демонстрации интерфейса предусмотрен mock-режим.

## Содержание

- [Основные возможности](#основные-возможности)
- [Стек и устройство приложения](#стек-и-устройство-приложения)
- [Запуск всего приложения в Docker](#запуск-всего-приложения-в-docker)
- [Создание первого капитана](#создание-первого-капитана)
- [Подключение расписания УУНиТ](#подключение-расписания-уунит)
- [Локальная разработка без контейнеров приложения](#локальная-разработка-без-контейнеров-приложения)
- [Запуск Android-приложения](#запуск-android-приложения)
- [Структура репозитория](#структура-репозитория)

## Основные возможности

- **Проекты.** Отдельные участники, функциональные роли, доска и источники каждого проекта. На доске и в расписании можно выбрать один проект или все проекты.
- **Kanban-доска.** Четыре этапа: «Ожидает», «В работе», «На проверке», «Выполнено». У задачи есть описание, исполнитель, приоритет, необязательный дедлайн, исходники и результаты работы.
- **Проверка задач.** Исполнитель сдаёт результат, капитан принимает работу или возвращает её с замечаниями. Комментарии и изменения статуса сохраняются в истории задачи.
- **Файлы и ссылки.** Общие источники проектов, исходные материалы и результаты задач. Файлы загружаются в хранилище и скачиваются через API сервера.
- **Расписание.** Командные, групповые и личные события, напоминания, типы событий, просмотр дня или недели. Учебные пары загружаются сервером из расписания УУНиТ при включённой синхронизации.
- **Участники и доступ.** Капитан создаёт аккаунты, управляет участниками проектов, блокирует доступ и сбрасывает пароли. Открытой самостоятельной регистрации нет.
- **Профиль и настройки.** Аватар, логин, учебная группа, смена пароля, приватность личного расписания, светлая и тёмная темы.
- **Уведомления и архив.** Уведомления о работе над задачами и событиях, индивидуальное прочтение и «Прочитать все», поиск и фильтрация завершённых задач.

Системная роль `CAPTAIN` означает капитана общей команды, `MEMBER` — обычного участника. Функциональная роль внутри проекта, например дизайнер или backend-разработчик, задаётся отдельно и может отличаться в разных проектах.

## Стек и устройство приложения

| Часть | Технологии |
| --- | --- |
| Веб-клиент | React, TypeScript, Vite, Redux Toolkit, Redux Saga, React Router, Axios, SCSS |
| Сервер | Go, стандартный HTTP-сервер, JWT, PostgreSQL |
| Хранение файлов | MinIO с S3 API; при локальном запуске без MinIO доступно файловое хранилище |
| Android | Kotlin, Jetpack Compose, Material 3, Gradle |
| Инфраструктура | Docker Compose, Nginx, GitHub Actions |

В основном Docker-стеке Nginx раздаёт собранный веб-клиент и передаёт запросы `/api/v1/*` серверу. Backend хранит данные в PostgreSQL, а содержимое файлов — в MinIO. Браузеру не требуется прямой доступ к PostgreSQL или S3 API.

## Запуск всего приложения в Docker

Это основной способ запустить веб-приложение вместе с backend, базой данных и файловым хранилищем.

### 1. Подготовить инструменты

Нужны Git, Docker Engine или Docker Desktop и Docker Compose v2 с поддержкой `--wait`. На Windows Docker Desktop должен работать в режиме Linux-контейнеров. Локально устанавливать Go, Node.js и PostgreSQL для этого варианта не требуется.

Проверка в терминале:

```sh
git --version
docker version
docker compose version
```

Клонируйте репозиторий и перейдите в его корень — каталог, где расположен `compose.yaml`. Все команды этого раздела выполняются из корня.

### 2. Создать файл окружения

PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux / macOS:

```sh
cp .env.example .env
```

Команда копирования нужна только при первой настройке: существующий `.env` с рабочими значениями сохраняйте.

Откройте `.env` и заполните обязательные значения. Пример структуры:

```dotenv
POSTGRES_DB=kanban
POSTGRES_USER=kanban
POSTGRES_PASSWORD=replace-with-your-database-password
HTTP_PORT=8080

MINIO_ROOT_USER=kanban-storage
MINIO_ROOT_PASSWORD=replace-with-your-storage-password
MINIO_BUCKET=kanban-attachments
MINIO_CONSOLE_PORT=9001

SCHEDULE_SYNC_ENABLED=false
SCHEDULE_SYNC_SOURCE_URL=https://dev.uust-time.ru/api/v/852972
SCHEDULE_SYNC_SEMESTER_ID=241
SCHEDULE_SYNC_SEMESTER_START=2026-09-01
SCHEDULE_SYNC_INTERVAL=6h
SCHEDULE_SYNC_HTTP_TIMEOUT=20s
```

Замените примеры паролей собственными значениями. `.env` исключён из Git.

| Переменная | Назначение |
| --- | --- |
| `POSTGRES_DB` | Имя БД, создаваемой при первой инициализации PostgreSQL |
| `POSTGRES_USER` | Пользователь PostgreSQL |
| `POSTGRES_PASSWORD` | Обязательный непустой пароль PostgreSQL |
| `HTTP_PORT` | Порт веб-приложения на компьютере, по умолчанию `8080` |
| `MINIO_ROOT_USER` | Логин MinIO, не менее 3 символов |
| `MINIO_ROOT_PASSWORD` | Пароль MinIO, не менее 8 символов |
| `MINIO_BUCKET` | Bucket для загруженных файлов; backend создаёт его автоматически |
| `MINIO_CONSOLE_PORT` | Порт административной консоли MinIO, по умолчанию `9001` |
| `SCHEDULE_SYNC_*` | Параметры необязательной синхронизации учебных пар; подробнее ниже |

Корневой `.env` используется Docker Compose для подстановки значений в `compose.yaml`. Backend получает только переменные, переданные ему в `environment`; произвольная новая строка в `.env` сама по себе не становится настройкой сервера.

### 3. Проверить конфигурацию backend

Путь YAML-конфига по умолчанию — `backend/config/config.yaml`, но этот файл исключён из Git правилом `backend/.gitignore` и отсутствует на свежем клоне. Сервер допускает отсутствие YAML: в таком случае все обязательные настройки должны поступить из окружения. Если YAML существует, сервер сначала читает его, затем переопределяет поля переменными окружения. Имена переменных самого backend записаны в нижнем регистре, например `db_host`, `jwt_access_secret`, `schedule_sync_enabled`.

В Docker параметры PostgreSQL, MinIO, HTTP и синхронизации уже передаются через `compose.yaml`. Поэтому для контейнерного запуска менять `postgres.db_host` с `localhost` на `postgres` в YAML не нужно — Compose переопределяет его.

Основной Compose-файл пока не передаёт обязательные JWT-настройки. Поэтому для запуска со свежего клона добавьте в корневой `.env` собственные независимые секреты:

```dotenv
JWT_ACCESS_SECRET=replace-with-a-long-random-access-secret
JWT_REFRESH_SECRET=replace-with-another-long-random-refresh-secret
```

И создайте рядом с `compose.yaml` локальный `compose.override.yaml`:

```yaml
services:
  backend:
    environment:
      jwt_access_secret: ${JWT_ACCESS_SECRET:?Set JWT_ACCESS_SECRET in .env}
      jwt_refresh_secret: ${JWT_REFRESH_SECRET:?Set JWT_REFRESH_SECRET in .env}
      access_token_ttl: "1m"
      refresh_token_ttl: "1h"
      session_cleanup_interval: "30m"
```

Docker Compose автоматически подключает `compose.override.yaml` при командах без `-f`, в том числе в CD. В этом файле остаются ссылки на переменные, сами секреты хранятся в `.env`. Чтобы локальный override не мешал проверке чистоты рабочей копии при деплое, исключите его локально из корня репозитория (Bash):

```sh
printf '\n/compose.override.yaml\n' >> "$(git rev-parse --git-path info/exclude)"
git status --porcelain
```

Перед CD вывод `git status --porcelain` должен быть пустым. С таким override отдельный `config.yaml` для Docker-запуска не нужен. Пример задаёт срок access-токена `1m`, refresh-токена `1h` и интервал очистки сессий `30m`; эти значения можно изменить. Клиент автоматически обновляет истёкший access-токен через refresh-токен.

### 4. Собрать и запустить сервисы

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

При первой сборке Docker скачивает образы и зависимости. PostgreSQL и MinIO запускаются первыми; backend ждёт их готовности, frontend — готовности backend. Миграции БД выполняются сервером автоматически.

После успешного запуска:

| Что открыть | Адрес по умолчанию |
| --- | --- |
| Веб-приложение | http://localhost:8080 |
| Проверка HTTP-доступности backend через Nginx | http://localhost:8080/api/v1/system/health |
| Проверка приложения через Nginx | http://localhost:8080/health |
| Консоль MinIO | http://127.0.0.1:9001 |

Если меняли `HTTP_PORT` или `MINIO_CONSOLE_PORT`, подставьте свои порты. Для входа в MinIO используйте `MINIO_ROOT_USER` и `MINIO_ROOT_PASSWORD`.

Порты PostgreSQL `5432`, backend `3005` и S3 API `9000` в основном стеке не публикуются на хост. Консоль MinIO доступна только через loopback-интерфейс компьютера.

### 5. Создать капитана и войти

Новая БД не содержит готового аккаунта администратора. Создайте первого капитана по инструкции следующего раздела, затем войдите с его логином и паролем. Капитан сможет добавить остальных участников через интерфейс.

### Остановка, обновление и просмотр логов

```sh
# Последние сообщения сервисов
docker compose logs --tail=100 backend frontend postgres minio

# Следить за backend в реальном времени; Ctrl+C завершает просмотр логов
docker compose logs -f backend

# После получения изменений из Git — пересобрать и запустить
docker compose up -d --build --wait --wait-timeout 180

# Применить изменение окружения backend без пересборки образа
docker compose up -d --force-recreate backend

# Остановить стек с сохранением данных
docker compose down
```

Обычный `docker compose restart` не применяет изменённые переменные окружения. Изменения `backend/config/config.yaml` требуют пересборки образа, поскольку конфиг копируется внутрь него при сборке.

PostgreSQL хранится в томе `postgres_data`, файлы MinIO — в `minio_data`. `docker compose down` сохраняет оба тома. Опция `down -v` удаляет их вместе с данными и не нужна для обычной остановки или обновления.

Параметры `POSTGRES_DB`, `POSTGRES_USER` и `POSTGRES_PASSWORD` инициализируют новый том PostgreSQL. Изменение `.env` не переименовывает существующую БД и не меняет пароль уже созданного пользователя. Настройки приложения должны соответствовать фактической БД.

## Создание первого капитана

Утилита `create-captain` создаёт пользователя с системной ролью `CAPTAIN` и указанную учебную группу, если её ещё нет. Она не вызывается автоматически при старте сервера.

### В запущенном Docker-стеке

Из корня репозитория выполните одну команду, заменив пароль и данные пользователя:

```sh
docker compose exec backend /app/create-captain -config /app/config/config.yaml -login captain -password "ReplaceWithYourPassword123!" -name "Иван Иванов" -group "ТОП-204Б"
```

Команда использует окружение контейнера и подключается к той же БД, что и сервер. После сообщения `captain account is ready` войдите в веб-приложение с указанными логином и паролем.

Если такой логин уже существует, утилита активирует аккаунт и назначает роль `CAPTAIN`, но **не меняет его пароль, имя и группу**. Повторный вызов с другим `-password` не является сбросом пароля.

### При локальном запуске Go

Из папки `backend`, с настроенным локальным конфигом:

```sh
go run ./cmd/create-captain -config ./config/config.local.yaml -login captain -password "ReplaceWithYourPassword123!" -name "Иван Иванов" -group "ТОП-204Б"
```

При установленном GNU Make доступен эквивалент:

```sh
make create-captain CONFIG_PATH=./config/config.local.yaml CAPTAIN_LOGIN=captain CAPTAIN_PASSWORD='ReplaceWithYourPassword123!' CAPTAIN_NAME='Иван Иванов' CAPTAIN_GROUP='ТОП-204Б'
```

Все четыре аргумента — логин, пароль, имя и группа — обязательны для прямого вызова утилиты. Используйте обычные кавычки и пробел между именем флага и его значением.

## Подключение расписания УУНиТ

Синхронизация необязательна: собственные события работают и без неё. Backend получает учебные пары из JSON API УУНиТ и сохраняет их в PostgreSQL; клиенты читают готовое расписание через backend.

Для основного Docker-стека в корневом `.env` задайте:

```dotenv
SCHEDULE_SYNC_ENABLED=true
SCHEDULE_SYNC_SOURCE_URL=https://dev.uust-time.ru/api/v/852972
SCHEDULE_SYNC_SEMESTER_ID=241
SCHEDULE_SYNC_SEMESTER_START=2026-09-01
SCHEDULE_SYNC_INTERVAL=6h
SCHEDULE_SYNC_HTTP_TIMEOUT=20s
```

Адрес источника, идентификатор и начало семестра приведены по текущему примеру конфигурации проекта. При смене семестра проверьте их и обновите; идентификатор семестра — значение из API источника, а не номер учебной недели.

Примените настройки:

```sh
docker compose up -d --force-recreate backend
docker compose logs -f backend
```

Первая синхронизация запускается сразу после старта backend, последующие — через `SCHEDULE_SYNC_INTERVAL`. В логах ищите `SCHEDULE SYNC COMPLETED` с количеством занятий или `SCHEDULE SYNC FAILED` с причиной ошибки.

Синхронизируются группы, уже существующие в таблице `student_groups`. Создайте капитана или участников с нужной группой до синхронизации. Если группу добавили после первого запуска парсера, дождитесь следующего прохода или пересоздайте backend. При недоступности источника сохраняется последний загруженный кэш; `lessons=0` само по себе не подтверждает загрузку нужной группы.

Для локального Go-процесса эти параметры задаются секцией `schedule_sync` в YAML либо соответствующими переменными окружения в нижнем регистре. Пример YAML находится в [документации инфраструктуры backend](backend/docs/INFRASTRUCTURE.md#расписание).

## Локальная разработка без контейнеров приложения

Этот вариант нужен для разработки с перезапуском Go-сервера и горячим обновлением React. PostgreSQL и MinIO остаются в Docker, а backend и frontend запускаются на компьютере.

### Требования

| Инструмент | Версия / назначение |
| --- | --- |
| Go | `1.25.3` или совместимая более новая версия; версия модуля указана в `backend/go.mod` |
| Node.js и npm | Node.js `24`, как в Dockerfile и CI; npm поставляется с Node.js |
| Docker и Compose | PostgreSQL и MinIO для локальной разработки |
| GNU Make | Необязателен: ниже приведены прямые команды |

Не запускайте этот инфраструктурный стек одновременно с основным, если они используют одинаковые порты. Это отдельная установка со своими томами; данные основного стека автоматически в неё не переносятся.

### 1. Запустить PostgreSQL и MinIO

Из корня:

```sh
docker compose -f backend/docker-compose.infrastructure.yml up -d --build
docker compose -f backend/docker-compose.infrastructure.yml ps
```

Этот файл имеет собственные настройки и не использует значения основного стека:

| Сервис | Локальный адрес | Данные подключения для разработки |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | БД `kanban-db`, пользователь `postgres`, пароль `postgres` |
| MinIO S3 API | `localhost:9000` | Access key `minioadmin`, secret key `minioadmin` |
| MinIO Console | `localhost:9001` | Те же логин и пароль MinIO |

MinIO в этом варианте собирается из исходников; первая сборка может занять время. Дождитесь готовности PostgreSQL и доступности MinIO перед запуском backend.

### 2. Настроить и запустить backend

Перейдите в `backend` и создайте файл `config/config.local.yaml` вручную по примеру ниже. Копировать `config/config.yaml` со свежего клона нельзя: он не хранится в Git.

PowerShell:

```powershell
Set-Location backend
```

Linux / macOS:

```sh
cd backend
```

Содержимое `config/config.local.yaml` для отдельного инфраструктурного стека (замените JWT-секреты собственными значениями):

```yaml
log_level: "info"
http_server:
  http_address: ":3005"
  http_timeout: 4s
  http_idle_timeout: 60s

jwt:
  jwt_access_secret: "replace-with-a-long-random-access-secret"
  jwt_refresh_secret: "replace-with-another-long-random-refresh-secret"
  access_token_ttl: "1m"
  refresh_token_ttl: "1h"
  session_cleanup_interval: "30m"

postgres:
  db_host: "localhost"
  db_user: "postgres"
  db_port: 5432
  db_pass: "postgres"
  db_name: "kanban-db"
  ssl_mode: "disable"

minio:
  enabled: true
  endpoint: "localhost:9000"
  access_key: "minioadmin"
  secret_key: "minioadmin"
  bucket: "kanban-attachments"
  use_ssl: false
```

Здесь `db_name` должен быть именно `kanban-db`, как в инфраструктурном Compose. Основной корневой Compose по умолчанию использует другую БД — `kanban`. Локальный конфиг с собственными секретами не добавляйте в Git; исключите `/backend/config/config.local.yaml` через `.git/info/exclude`.

Загрузите зависимости и запустите сервер из `backend`:

```sh
go mod download
go run ./cmd/kanban-board -config ./config/config.local.yaml
```

При наличии Make: `make run CONFIG_PATH=./config/config.local.yaml`.

Backend доступен на `http://localhost:3005`, проверка — `http://localhost:3005/api/v1/system/health`. Миграции выполняются автоматически. В другом терминале создайте капитана, используя **тот же локальный конфиг** и команду из раздела выше.

При `minio.enabled: false` backend использует каталог `data` относительно рабочего каталога процесса. Для общего стека и постоянного хранения файлов используйте MinIO.

Go-приложение не читает корневой `.env` автоматически. При прямом запуске оно использует YAML и переменные текущего процесса. Экспортированные ранее `db_*`, `jwt_*`, `minio_*` и другие переменные имеют приоритет над YAML.

### 3. Настроить и запустить frontend

Откройте новый терминал в корне, перейдите в `client`:

```sh
cd client
npm ci
```

Создайте `client/.env.local` из `client/.env.example`:

```powershell
# PowerShell, из client
Copy-Item .env.example .env.local
```

```sh
# Linux / macOS, из client
cp .env.example .env.local
```

Содержимое для локального backend:

```dotenv
VITE_API_MODE=backend
VITE_API_URL=/api/v1
API_PROXY_TARGET=http://127.0.0.1:3005
```

```sh
npm run dev
```

Откройте адрес, показанный Vite, обычно `http://localhost:5173`. Vite проксирует `/api` на `API_PROXY_TARGET`, сохраняя префикс `/api/v1`.

Если нужен локальный React с backend из **основного** Docker-стека, задайте `API_PROXY_TARGET=http://127.0.0.1:8080` либо свой `HTTP_PORT`. Прямой порт `3005` этого стека на хост не опубликован.

После изменения `.env.local` перезапустите Vite. Переменные `VITE_*` встраиваются в клиент при сборке и не должны содержать секреты. Dockerfile frontend исключает локальные `.env*` из контекста и по умолчанию собирает клиент в backend-режиме с `/api/v1`.

Для демонстрации только интерфейса задайте `VITE_API_MODE=mock`. В этом режиме backend не нужен; тестовые аккаунты: `alexey / lead123` и `maria / member123`. Они относятся только к mock-режиму и автоматически в PostgreSQL не создаются.

После `git pull` при изменении зависимостей выполните `npm ci` повторно. Ошибка `Cannot find package 'vitest'` при загрузке Vite-конфига обычно означает, что зависимости не установлены или установлены без devDependencies; для разработки нужны и они.

### 4. Проверки и сборка

Из `client`:

```sh
npm test
npm run lint
npm run build
```

Результат сборки frontend — `client/dist`. Тесты находятся рядом с кодом в `src/**/*.test.ts` и `src/**/*.test.tsx`, общие настройки и фикстуры — в `src/test`.

Из `backend`:

```sh
go test ./...
go vet ./...
go build ./...
```

Для остановки отдельной инфраструктуры из корня:

```sh
docker compose -f backend/docker-compose.infrastructure.yml down
```

## Запуск Android-приложения

Android-клиент находится в `mobile`. Для веб-версии его собирать не требуется.

1. Откройте `mobile` как отдельный проект в Android Studio.
2. Установите Android SDK 37 и настройте JDK 17 для Gradle.
3. Дождитесь Gradle Sync, выберите эмулятор или устройство с Android 10+ (`minSdk 29`).
4. Выберите вариант сборки `cleanDebug` или `mockDebug` и запустите приложение.

Из папки `mobile`, PowerShell:

```powershell
.\gradlew.bat testCleanDebugUnitTest assembleCleanDebug
```

Linux / macOS:

```sh
./gradlew testCleanDebugUnitTest assembleCleanDebug
```

Без `backendBaseUrl` приложение использует автономные данные на устройстве. Вариант `clean` предназначен для чистого старта, `mock` — для заполненной демонстрации. Для сборки с сервером задайте адрес через Gradle; например, с основным Docker-стеком и стандартным Android-эмулятором:

```powershell
.\gradlew.bat assembleCleanDebug -PbackendBaseUrl=http://10.0.2.2:8080
```

Для Linux / macOS используйте `./gradlew` с теми же аргументами. `10.0.2.2` в стандартном Android-эмуляторе указывает на компьютер разработчика. Для физического телефона нужен доступный ему адрес компьютера в локальной сети. Префикс `/api/v1` приложение добавляет само — в `backendBaseUrl` его указывать не нужно. HTTP разрешён debug-манифестом; для release-окружения используйте HTTPS.

При подключении к backend используются аккаунты серверной БД. Подробности мобильного клиента — в [mobile/README.md](mobile/README.md) и [документации разработки](docs/MOBILE_DEVELOPMENT.md).

## Структура репозитория

```text
kanban-board/
├── README.md                       # Описание проекта и запуск
├── compose.yaml                    # Общий стек: frontend, backend, PostgreSQL, MinIO
├── .env.example                    # Пример окружения общего Docker-стека
├── .github/workflows/              # CI/CD
├── backend/
│   ├── cmd/
│   │   ├── kanban-board/           # Точка входа HTTP-сервера
│   │   └── create-captain/         # Утилита создания первого капитана
│   ├── config/                     # YAML-конфигурации backend
│   ├── internal/
│   │   ├── app/                    # Инициализация приложения и фоновых процессов
│   │   ├── config/                 # Чтение YAML, окружения и проверка настроек
│   │   ├── middleware/             # HTTP middleware
│   │   ├── models/                 # Модели данных и определения таблиц
│   │   ├── objectstorage/          # MinIO и локальное файловое хранилище
│   │   ├── push/                   # Отправка push-уведомлений
│   │   ├── services/               # Бизнес-логика, расписание, фоновые планировщики
│   │   ├── storage/                # PostgreSQL, миграции и репозитории
│   │   └── transport/http/         # Маршрутизация и обработчики API
│   ├── pkg/                        # Вспомогательный код
│   ├── docs/                       # API, OpenAPI и инфраструктура backend
│   ├── deploy/minio/               # Сборка MinIO для отдельной инфраструктуры
│   ├── docker-compose.infrastructure.yml
│   ├── Dockerfile
│   ├── Makefile
│   └── go.mod
├── client/
│   ├── src/
│   │   ├── api/                    # Axios, контракты, мапперы, реальный и mock API
│   │   ├── assets/                 # Статические ресурсы
│   │   ├── components/             # Компоненты интерфейса и формы
│   │   ├── pages/                  # Страницы приложения
│   │   ├── store/                  # Redux, slices, sagas и hooks
│   │   ├── styles/                 # SCSS и темы
│   │   ├── test/                   # Настройка тестов и общие фикстуры
│   │   ├── types/                  # Общие TypeScript-типы
│   │   └── utils/                  # Вспомогательные функции
│   ├── scripts/                    # Изолированный стек и сквозные проверки API/UI
│   ├── public/                     # Публичные статические файлы
│   ├── .env.example                # Пример настройки Vite и API
│   ├── vite.config.ts              # Dev-сервер, прокси и Vitest
│   ├── nginx.conf                  # Раздача SPA и проксирование API
│   ├── Dockerfile
│   ├── package.json
│   └── README.md                   # Подробности frontend и интеграционных проверок
├── mobile/
│   ├── app/src/                    # Android-клиент, варианты clean/mock и тесты
│   ├── gradle/                     # Gradle Wrapper
│   ├── docs/                       # Документация мобильного клиента
│   ├── build.gradle.kts
│   └── README.md
├── scripts/                        # Деплой и проверка установки
└── docs/                           # Общая документация и отчёты интеграции
```

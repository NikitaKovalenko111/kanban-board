# Инфраструктура backend

## Первый капитан

После создания БД выполните:

```powershell
make create-captain CAPTAIN_LOGIN=captain CAPTAIN_PASSWORD=StrongPassword123 CAPTAIN_NAME="Иван Иванов" CAPTAIN_GROUP=ТОП-203Б
```

Команда идемпотентна: существующий логин получает роль `CAPTAIN` и активируется.

## MinIO

MinIO собирается локально из исходников зафиксированного релиза и commit, поэтому авторизация в Docker Hub или Quay не требуется. Для первой сборки нужен доступ к GitHub и registry базового Go-образа. Запустите:

```powershell
docker compose -f docker-compose.infrastructure.yml build minio
docker compose -f docker-compose.infrastructure.yml up -d minio
```

Затем добавьте в `config.yaml`:

```yaml
minio:
  enabled: true
  endpoint: localhost:9000
  access_key: minioadmin
  secret_key: minioadmin
  bucket: kanban-board
  use_ssl: false
```

Bucket создаётся backend автоматически. При `enabled: false` используется совместимое локальное хранилище `data/`.

## Firebase Cloud Messaging

Положите service-account JSON вне репозитория и добавьте в `config.yaml`:

```yaml
fcm:
  enabled: true
  credentials_file: ./config/firebase-service-account.json
  project_id: your-firebase-project-id
```

Backend отправляет заголовок, текст и `kind`, а также `taskId`/`eventId` в data payload. Планировщик проверяет наступившие напоминания каждую минуту.

## Расписание

```yaml
schedule_sync:
  enabled: true
  source_url: https://example.org/schedule?group={group}
  interval: 6h
  http_timeout: 20s
```

`source_url` — URL HTML-страницы, где `{group}` заменяется URL-encoded номером группы. Парсер ожидает строки `table tbody tr` и столбцы: дата `ДД.ММ.ГГГГ`, начало, окончание, название, аудитория, преподаватель, вид занятия. Синхронизируются все группы из `student_groups`; при ошибке сохраняется кэш со статусом `CACHED`.

## Миграции

Применённые версии находятся в `schema_migrations`. Миграции выполняются один раз по версии при старте backend.

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

Backend отправляет заголовок, текст и `kind`, а также ровно один идентификатор цели: `taskId`, `eventId` или `lessonId`. Планировщик проверяет наступившие напоминания каждую минуту.

Полный логический payload FCM для задачи:

```json
{
  "notification": {
    "title": "Новый комментарий",
    "body": "Проверь результат задачи"
  },
  "data": {
    "kind": "COMMENT",
    "taskId": "7db10a98-9b1f-47db-8092-e7a98248a37c"
  }
}
```

Полный логический payload FCM для события:

```json
{
  "notification": {
    "title": "Защита проекта",
    "body": "Событие начнётся через 15 мин."
  },
  "data": {
    "kind": "EVENT",
    "eventId": "adb10a98-9b1f-47db-8092-e7a98248a37f"
  }
}
```

Полная запись уведомления получается через REST или SSE, а FCM используется для системного push в фоне.

## Расписание

```yaml
schedule_sync:
  enabled: true
  source_url: https://dev.uust-time.ru/api/v/852972
  semester_id: 241
  semester_start: "2026-09-01"
  interval: 6h
  http_timeout: 20s
```

Сервис получает список групп и расписание из JSON API ИСУ УУНиТ. `semester_id` — идентификатор семестра в API, `semester_start` — первый день семестра; сервис сам приводит его к понедельнику первой учебной недели. Эти значения нужно обновлять при смене семестра.

Синхронизируются все группы из `student_groups`. Для каждой записи API недели семестра преобразуются в конкретные даты часового пояса `Asia/Yekaterinburg`. При недоступности API или отсутствии группы сохраняется последний кэш со статусом `CACHED`.

API официального сайта не документирован публично, а версия содержится в URL. Перед эксплуатацией следует согласовать доступ с УУНиТ и учитывать, что адрес или формат ответа могут измениться.

## Миграции

Применённые версии находятся в `schema_migrations`. Миграции выполняются один раз по версии при старте backend.

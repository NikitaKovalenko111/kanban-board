# Kanban Board Backend API

Документ описывает HTTP API, которое реализовано в текущей версии backend.

## Общие правила

Основной префикс API:

```text
/api/v1
```

Запросы и ответы используют `application/json`, кроме загрузки и скачивания файлов. Идентификаторы сущностей передаются как UUID-строки. Дата и время в JSON передаются в формате RFC 3339, например `2026-11-01T12:30:00+05:00`.

Все маршруты `/api/v1/*`, кроме `/api/v1/auth/*`, требуют access-токен:

```http
Authorization: Bearer <accessToken>
```

При отсутствии или ошибке access-токена сервер возвращает `401`:

```json
{
  "message": "Valid access token is required",
  "error": "invalid_access_token"
}
```

Общий формат ошибки:

```json
{
  "message": "Resource not found",
  "error": "not_found"
}
```

Основные общие статусы:

| Статус | Значение |
|---|---|
| `400` | Некорректный JSON, параметр или файл |
| `401` | Ошибка авторизации или токена |
| `403` | Недостаточно прав |
| `404` | Сущность не найдена |
| `409` | Действие конфликтует с текущим состоянием |
| `500` | Внутренняя ошибка сервера |
| `504` | Истёк таймаут запроса |

## System

### Проверка состояния

```http
GET /system/health
```

Авторизация не требуется. Успешный ответ: `200 OK` без тела.

## Авторизация

Маршруты авторизации доступны с рекомендуемым префиксом `/api/v1/auth`. Для обратной совместимости также работает префикс `/auth`.

### Регистрация

```http
POST /api/v1/auth/register
```

```json
{
  "username": "student",
  "email": "student@example.com",
  "password": "strong-password",
  "studentGroup": "РИ-230912"
}
```

Поле `group_name` временно поддерживается как устаревший вариант `studentGroup`.

Ответ `201 Created`:

```json
{
  "userId": "c0a80101-0000-0000-0000-000000000001",
  "message": "User registered successfully"
}
```

Возможные конфликты: `username_taken`, `email_taken`.

### Вход

```http
POST /api/v1/auth/login
```

В `login` можно передать username или email.

```json
{
  "login": "student@example.com",
  "password": "strong-password"
}
```

Ответ `200 OK`:

```json
{
  "userId": "c0a80101-0000-0000-0000-000000000001",
  "accessToken": "<JWT>",
  "refreshToken": "<JWT>",
  "tokenType": "Bearer"
}
```

Неверные данные: `401 invalid_credentials`.

### Обновление токенов

```http
POST /api/v1/auth/refresh
```

```json
{
  "refreshToken": "<refreshToken>"
}
```

Ответ `200 OK` имеет тот же формат, что и ответ входа. Старый refresh-токен заменяется новой парой токенов. Поле `refresh_token` временно поддерживается для обратной совместимости.

### Выход

```http
POST /api/v1/auth/logout
```

```json
{
  "refreshToken": "<refreshToken>"
}
```

Успешный ответ: `204 No Content`. Соответствующая сессия отзывается.

## Пользователи

### Получить свой профиль

```http
GET /api/v1/users/me
```

Ответ `200 OK`:

```json
{
  "id": "user-uuid",
  "username": "student",
  "email": "student@example.com",
  "displayName": "Иван Иванов",
  "position": "Backend developer",
  "studentGroup": "РИ-230912",
  "imageUrl": "https://example.com/avatar.png",
  "active": true,
  "memberships": [
    {
      "projectId": "project-uuid",
      "userId": "user-uuid",
      "role": "MEMBER",
      "status": "ACTIVE",
      "joinedAt": "2026-11-01T12:30:00Z"
    }
  ]
}
```

### Изменить свой профиль

```http
PATCH /api/v1/users/me
```

```json
{
  "displayName": "Иван Иванов",
  "position": "Backend developer",
  "studentGroup": "РИ-230912",
  "imageUrl": "https://example.com/avatar.png"
}
```

`displayName` обязателен. Ответ `200 OK` содержит обновлённый профиль.

### Получить публичный профиль

```http
GET /api/v1/users/{userId}
```

Ответ соответствует профилю пользователя, но `email` возвращается пустым, а `memberships` не раскрываются.

## Проекты

Допустимые значения `joinPolicy`: `OPEN`, `INVITE_ONLY`, `CLOSED`.

| Метод | Маршрут | Назначение | Успешный ответ |
|---|---|---|---|
| `GET` | `/api/v1/projects` | Проекты текущего пользователя и доступные открытые проекты | `200`, массив проектов |
| `POST` | `/api/v1/projects` | Создать проект | `201`, `{"id":"project-uuid"}` |
| `GET` | `/api/v1/projects/{projectId}` | Получить проект | `200`, проект |
| `PATCH` | `/api/v1/projects/{projectId}` | Изменить проект; только `LEAD` | `200`, проект |
| `DELETE` | `/api/v1/projects/{projectId}` | Удалить проект; только `LEAD` | `204` |

Создание или изменение проекта:

```json
{
  "name": "Мобильное приложение",
  "description": "Проект второго спринта",
  "joinPolicy": "INVITE_ONLY",
  "archived": false
}
```

При создании `joinPolicy` по умолчанию равен `INVITE_ONLY`. Создатель становится `LEAD`; также автоматически создаются доска и колонки `WAITING`, `WORKING`, `CHECKING`, `DONE`.

Объект проекта:

```json
{
  "id": "project-uuid",
  "createdByUserId": "user-uuid",
  "name": "Мобильное приложение",
  "description": "Проект второго спринта",
  "joinPolicy": "INVITE_ONLY",
  "archived": false,
  "isMember": true,
  "createdAt": "2026-11-01T12:30:00Z",
  "updatedAt": "2026-11-01T12:30:00Z"
}
```

## Участники проекта

Роли: `LEAD`, `MEMBER`. Статусы: `ACTIVE`, `BLOCKED`, `LEFT`.

| Метод | Маршрут | Назначение | Права | Ответ |
|---|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/members` | Список участников | Участник проекта | `200`, массив |
| `POST` | `/api/v1/projects/{projectId}/members` | Добавить или повторно активировать участника | `LEAD` | `204` |
| `PATCH` | `/api/v1/projects/{projectId}/members/{memberId}` | Изменить роль и статус | `LEAD` | `204` |
| `DELETE` | `/api/v1/projects/{projectId}/members/{memberId}` | Исключить участника | `LEAD` | `204` |
| `POST` | `/api/v1/projects/{projectId}/members/me` | Самостоятельно вступить | Проект с `OPEN` | `204` |
| `DELETE` | `/api/v1/projects/{projectId}/members/me` | Покинуть проект | Участник | `204` |

Добавление участника:

```json
{
  "userId": "user-uuid",
  "role": "MEMBER"
}
```

Изменение участника:

```json
{
  "role": "MEMBER",
  "status": "BLOCKED"
}
```

Последний активный `LEAD` не может покинуть проект или быть исключён: сервер вернёт `409`.

## Доска и колонки

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/board` | Доска с колонками и задачами | `200`, объект доски |
| `POST` | `/api/v1/projects/{projectId}/columns` | Создать колонку; только `LEAD` | `201`, `{"id":"column-uuid"}` |
| `PATCH` | `/api/v1/columns/{columnId}` | Изменить колонку; только `LEAD` | `204` |
| `DELETE` | `/api/v1/columns/{columnId}` | Удалить колонку; только `LEAD` | `204` |

Тело создания или изменения колонки:

```json
{
  "code": "BLOCKED",
  "name": "Заблокировано",
  "position": 4
}
```

Ответ доски:

```json
{
  "boardId": "board-uuid",
  "projectId": "project-uuid",
  "name": "Kanban",
  "columns": [
    {"id":"column-uuid","code":"WAITING","name":"Ожидает","position":0}
  ],
  "tasks": []
}
```

## Задачи

Приоритеты: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. По умолчанию используется `MEDIUM`.

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/tasks` | Список задач проекта | `200`, массив задач |
| `POST` | `/api/v1/projects/{projectId}/tasks` | Создать задачу; только `LEAD` | `201`, `{"id":"task-uuid"}` |
| `GET` | `/api/v1/tasks/{taskId}` | Получить задачу | `200`, задача |
| `PATCH` | `/api/v1/tasks/{taskId}` | Изменить задачу; только `LEAD` | `200`, задача |
| `DELETE` | `/api/v1/tasks/{taskId}` | Удалить задачу; только `LEAD` | `204` |
| `POST` | `/api/v1/tasks/{taskId}/take` | Взять свободную задачу | `200`, задача |
| `PATCH` | `/api/v1/tasks/{taskId}/status` | Изменить статус | `200`, задача |
| `GET` | `/api/v1/tasks/{taskId}/history` | История изменений | `200`, массив записей |

Создание или изменение задачи:

```json
{
  "title": "Сделать экран входа",
  "description": "Добавить форму и обработку ошибок",
  "priority": "HIGH",
  "dueDate": "2026-11-10T18:00:00+05:00",
  "assigneeIds": ["user-uuid"]
}
```

Поле `assigneeIds` используется при создании. При изменении задачи исполнители меняются отдельными запросами.

Объект задачи:

```json
{
  "id": "task-uuid",
  "projectId": "project-uuid",
  "boardColumnId": "column-uuid",
  "status": "WORKING",
  "createdByUserId": "user-uuid",
  "title": "Сделать экран входа",
  "description": "Добавить форму и обработку ошибок",
  "priority": "HIGH",
  "position": 0,
  "dueDate": "2026-11-10T18:00:00+05:00",
  "assigneeIds": ["user-uuid"],
  "createdAt": "2026-11-01T12:30:00Z",
  "updatedAt": "2026-11-01T12:30:00Z"
}
```

Изменение статуса:

```http
PATCH /api/v1/tasks/{taskId}/status
```

```json
{
  "status": "CHECKING",
  "comment": "Готово к проверке"
}
```

Разрешённые переходы:

| Кто | Переход | Условие |
|---|---|---|
| Исполнитель | `WAITING → WORKING` | Пользователь назначен на задачу |
| Исполнитель | `WORKING → CHECKING` | Пользователь назначен на задачу |
| `LEAD` | `CHECKING → DONE` | Без дополнительного условия |
| `LEAD` | `CHECKING → WORKING` | Непустой `comment` |
| `LEAD` | `DONE → WORKING` | Непустой `comment` |

### Исполнители задачи

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/tasks/{taskId}/assignees` | UUID исполнителей | `200`, массив строк |
| `POST` | `/api/v1/tasks/{taskId}/assignees` | Назначить участника; только `LEAD` | `204` |
| `DELETE` | `/api/v1/tasks/{taskId}/assignees/{assigneeId}` | Удалить исполнителя; только `LEAD` | `204` |

Тело назначения:

```json
{
  "userId": "user-uuid"
}
```

## Комментарии

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/tasks/{taskId}/comments` | Комментарии задачи | `200`, массив |
| `POST` | `/api/v1/tasks/{taskId}/comments` | Добавить комментарий | `201`, `{"id":"comment-uuid"}` |
| `PATCH` | `/api/v1/comments/{commentId}` | Изменить свой комментарий | `204` |
| `DELETE` | `/api/v1/comments/{commentId}` | Удалить свой комментарий | `204` |

Тело создания или изменения:

```json
{
  "text": "Нужно уточнить требования"
}
```

Объект комментария содержит `id`, `taskId`, `authorId`, `authorName`, `text`, `createdAt` и необязательное `updatedAt`.

## Вложения

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/tasks/{taskId}/attachments` | Список метаданных файлов | `200`, массив |
| `POST` | `/api/v1/tasks/{taskId}/attachments` | Загрузить файл | `201`, `{"id":"attachment-uuid"}` |
| `GET` | `/api/v1/attachments/{attachmentId}/download` | Скачать файл | Бинарное содержимое |
| `DELETE` | `/api/v1/attachments/{attachmentId}` | Удалить загруженный собой файл | `204` |

Загрузка выполняется как `multipart/form-data`:

- `file` — обязательный файл;
- `attachmentType` — `SOURCE` или `RESULT`;
- максимальный размер файла — 10 МиБ;
- устаревшее поле `kind` поддерживается вместо `attachmentType`.

Пример:

```bash
curl -X POST http://localhost:8080/api/v1/tasks/$TASK_ID/attachments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "attachmentType=SOURCE" \
  -F "file=@requirements.pdf"
```

Метаданные вложения содержат `id`, `taskId`, `uploadedByUserId`, `attachmentType`, `fileName`, `mimeType`, `fileSize`, `createdAt`. Внутренний путь хранения не возвращается.

## Учебные группы и расписание

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/student-groups` | Список учебных групп | `200`, массив групп |
| `GET` | `/api/v1/schedule/lessons?group={code}&from={date}&to={date}` | Занятия группы за период | `200`, массив занятий |

`from` и `to` обязательны и используют формат `YYYY-MM-DD`. Дата `to` включается целиком.

Пример:

```http
GET /api/v1/schedule/lessons?group=РИ-230912&from=2026-11-01&to=2026-11-07
```

В текущей реализации модели расписания возвращаются с Go-названиями полей: `StudentGroupID`, `GroupCode`, `Name`, а для занятия — `LessonID`, `StudentGroupID`, `ExternalID`, `Title`, `Room`, `Teacher`, `LessonKind`, `StartsAt`, `EndsAt`, `SourceStatus`, `SourceUpdatedAt`, `CreatedAt`, `UpdatedAt`.

## Типы событий

Допустимые области типа события: `GLOBAL`, `PERSONAL`, `PROJECT`. Пользователь создаёт личные и проектные типы; `GLOBAL` предназначен для системных данных.

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/event-types` | Доступные типы событий | `200`, массив |
| `POST` | `/api/v1/event-types` | Создать тип | `201`, `{"id":"event-type-uuid"}` |
| `GET` | `/api/v1/event-types/{eventTypeId}` | Получить тип | `200`, объект |
| `PATCH` | `/api/v1/event-types/{eventTypeId}` | Изменить созданный собой тип | `204` |
| `DELETE` | `/api/v1/event-types/{eventTypeId}` | Удалить созданный собой тип | `204` |

Создание:

```json
{
  "title": "Дедлайн",
  "color": "#FF5722",
  "scope": "PROJECT",
  "projectId": "project-uuid",
  "defaultReminderMinutes": [1440, 60]
}
```

Если `scope` не передан, используется `PERSONAL`. Для изменения используются `title`, `color` и `defaultReminderMinutes`; область и проект текущим запросом не меняются.

## События

Видимость события: `PERSONAL`, `PROJECT`, `GROUP`.

| Метод | Маршрут | Назначение | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/events?from={date}&to={date}&projectId={id}&group={code}` | События за период | `200`, массив |
| `POST` | `/api/v1/events` | Создать событие | `201`, `{"id":"event-uuid"}` |
| `GET` | `/api/v1/events/{eventId}` | Получить событие | `200`, объект |
| `PATCH` | `/api/v1/events/{eventId}` | Изменить своё событие | `204` |
| `DELETE` | `/api/v1/events/{eventId}` | Удалить своё событие | `204` |

Для списка `from` и `to` обязательны в формате `YYYY-MM-DD`. `projectId` нужен для проектных событий, `group` — для событий учебной группы.

Создание события:

```json
{
  "eventTypeId": "event-type-uuid",
  "projectId": "project-uuid",
  "studentGroup": "",
  "title": "Демонстрация спринта",
  "description": "Показать готовую функциональность",
  "location": "Аудитория 101",
  "visibility": "PROJECT",
  "startsAt": "2026-11-10T16:00:00+05:00",
  "endsAt": "2026-11-10T17:00:00+05:00",
  "reminderMinutes": [1440, 60]
}
```

Правила:

- `PERSONAL`: `projectId` и `studentGroup` не передаются;
- `PROJECT`: обязателен `projectId`, пользователь должен состоять в проекте;
- `GROUP`: обязателен `studentGroup`, совпадающий с группой пользователя;
- `endsAt` должен быть позже `startsAt`;
- если `reminderMinutes` пуст, используются напоминания типа события.

При изменении используются поля `title`, `description`, `location`, `startsAt`, `endsAt`, `reminderMinutes`. Тип, видимость, проект и группа текущим запросом не меняются.

Объект события:

```json
{
  "id": "event-uuid",
  "ownerId": "user-uuid",
  "eventTypeId": "event-type-uuid",
  "projectId": "project-uuid",
  "title": "Демонстрация спринта",
  "description": "Показать готовую функциональность",
  "location": "Аудитория 101",
  "visibility": "PROJECT",
  "startsAt": "2026-11-10T16:00:00+05:00",
  "endsAt": "2026-11-10T17:00:00+05:00",
  "reminderMinutes": [1440, 60],
  "createdAt": "2026-11-01T12:30:00Z",
  "updatedAt": "2026-11-01T12:30:00Z"
}
```

## Пример последовательности работы

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/users/me
POST /api/v1/projects
POST /api/v1/projects/{projectId}/members
POST /api/v1/projects/{projectId}/tasks
POST /api/v1/tasks/{taskId}/take
PATCH /api/v1/tasks/{taskId}/status
```

Access-токен добавляется ко всем запросам после входа. После истечения access-токена клиент вызывает `/api/v1/auth/refresh` и сохраняет оба новых токена.

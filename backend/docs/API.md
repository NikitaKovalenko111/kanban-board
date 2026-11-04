# Kanban Board Backend API

Документ описывает API, согласованный с мобильным приложением. Базовый префикс — `/api/v1`. Все маршруты, кроме входа и обновления токенов, требуют заголовок:

Интерактивная документация после запуска сервера доступна по адресу `http://localhost:3005/api/v1/docs/` (также работает `/api/v1/swagger`). OpenAPI 3.0-файл доступен по адресу `/api/v1/docs/openapi.yaml` и хранится в `backend/docs/openapi.yaml`.

```http
Authorization: Bearer <accessToken>
```

Время передаётся в RFC 3339, идентификаторы — UUID. Поля `PATCH` являются необязательными: отсутствующее поле не меняется, а допустимый `null` очищает значение.

## Ошибки

```json
{
  "error": {
    "code": "VALIDATION",
    "message": "Некорректное тело запроса",
    "fields": {
      "title": "обязательное поле"
    }
  }
}
```

`fields` отсутствует, если ошибок отдельных полей нет. Основные коды: `VALIDATION`, `INVALID_CREDENTIALS`, `INVALID_ACCESS_TOKEN`, `INVALID_REFRESH_TOKEN`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.

## Авторизация

| Метод | Маршрут | Тело | Ответ |
|---|---|---|---|
| POST | `/auth/login` | `{ "login", "password" }` | `{ "accessToken", "refreshToken", "expiresIn", "user" }` |
| POST | `/auth/refresh` | `{ "refreshToken" }` | новая пара токенов и `expiresIn` |
| POST | `/auth/logout` | `{ "refreshToken" }` | `204` |
| POST | `/auth/change-password` | `{ "currentPassword", "newPassword" }` | `204` |

Refresh-токен при обновлении ротируется: старый после успешного запроса больше не действует. `expiresIn` — срок жизни access-токена в секундах. Самостоятельная регистрация отключена; обработчик регистрации оставлен в коде, но маршрут не зарегистрирован.

## Профиль и пользователи

Модель пользователя:

```json
{
  "id": "uuid",
  "fullName": "Иван Иванов",
  "username": "ivan.ivanov",
  "studentGroup": "РИ-230912",
  "imageUrl": null,
  "role": "MEMBER",
  "schedulePrivacy": "BUSY",
  "joinedAt": "2026-11-03T10:00:00+05:00",
  "active": true
}
```

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/me` | свой профиль |
| PATCH | `/me` | частично изменить `fullName`, `studentGroup`, `schedulePrivacy` |
| PUT | `/me/photo` | загрузить `jpg`, `png` или `webp`, multipart-поле `file` |
| DELETE | `/me/photo` | удалить фото |
| GET | `/team` | название и описание команды |
| GET | `/users?search=` | список пользователей |
| GET | `/users/{userId}` | профиль пользователя |
| POST | `/users` | создать пользователя; только капитан |
| PATCH | `/users/{userId}/status` | `{ "status": "ACTIVE" | "BLOCKED" }` |
| POST | `/users/{userId}/reset-password` | выдать одноразово показываемый временный пароль |
| PATCH | `/users/{userId}/role` | `{ "role": "CAPTAIN" | "MEMBER" }` |
| DELETE | `/users/{userId}` | удалить/обезличить пользователя |

Создание пользователя:

```json
{
  "fullName": "Иван Иванов",
  "username": "ivan.ivanov",
  "temporaryPassword": "temporary-password",
  "studentGroup": "РИ-230912",
  "role": "MEMBER"
}
```

Логин уникален без учёта регистра и содержит 3–32 латинских символа, цифры, `.`, `_` или `-`. Блокировка, удаление и сброс пароля отзывают refresh-сессии. Удаление возвращает `409`, пока у пользователя есть незакрытые задачи.

## Проекты и источники

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/projects` | все проекты |
| GET | `/projects/{projectId}` | проект с участниками и источниками |
| POST | `/projects` | создать проект и стандартную доску; только капитан |
| PATCH | `/projects/{projectId}` | частично изменить проект; `members` заменяет весь состав |
| DELETE | `/projects/{projectId}` | удалить проект, доску и задачи |
| POST | `/projects/{projectId}/sources/links` | добавить `{ "title", "url" }` |
| POST | `/projects/{projectId}/sources/files` | загрузить multipart-поле `file` |
| DELETE | `/projects/{projectId}/sources/{sourceId}` | удалить свой источник или удалить как капитан |

Название проекта имеет длину 1–80 символов и уникально без учёта регистра. Нельзя удалить из состава участника, у которого в проекте есть незакрытая задача.

## Колонки

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/projects/{projectId}/columns` | колонки по `position` |
| PUT | `/projects/{projectId}/columns` | заменить весь список; только капитан |

Тело `PUT`:

```json
[
  { "id": "uuid", "title": "Ожидает", "color": "#8A94A6" },
  { "id": null, "title": "Разработка", "color": "#6576DF" }
]
```

Нужно не менее четырёх колонок. Сервер вычисляет `position` и `kind`: первая — `BACKLOG`, последняя — `DONE`, предпоследняя — `REVIEW`, остальные — `IN_PROGRESS`. Колонку с задачами удалить нельзя.

## Задачи, комментарии и файлы

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/projects/{projectId}/tasks` | задачи проекта |
| POST | `/projects/{projectId}/tasks` | создать задачу; только капитан |
| GET | `/tasks/{taskId}` | задача с комментариями, вложениями и историей |
| PATCH | `/tasks/{taskId}` | частично изменить задачу; только капитан |
| DELETE | `/tasks/{taskId}` | удалить задачу; только капитан |
| POST | `/tasks/{taskId}/take` | взять свободную задачу из `BACKLOG` |
| POST | `/tasks/{taskId}/move` | `{ "columnId", "comment"? }` |
| POST | `/tasks/{taskId}/assign-to-me` | переназначить на капитана |
| GET | `/tasks/completed` | фильтры `from`, `to`, `projectId`, `assigneeId`, `search` |
| GET | `/tasks/assigned?userId=` | незакрытые задачи пользователя |
| GET | `/tasks/{taskId}/comments` | комментарии |
| POST | `/tasks/{taskId}/comments` | `{ "text" }`, 1–2000 символов |
| PATCH | `/comments/{commentId}` | изменить свой комментарий |
| DELETE | `/comments/{commentId}` | удалить свой комментарий |
| POST | `/tasks/{taskId}/attachments` | multipart `file`, `kind=SOURCE|RESULT` |
| DELETE | `/tasks/{taskId}/attachments/{attachmentId}` | удалить вложение до состояния `DONE` |
| GET | `/files/{fileId}` | скачать вложение или файл-источник |

При `PATCH` значения `assigneeId: null` и `dueAt: null` очищают исполнителя и дедлайн. При возврате из `REVIEW`/`DONE` обязателен непустой комментарий. Сервер записывает каждое перемещение в историю и проверяет матрицу прав мобильного приложения.

## Расписание и события

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/student-groups` | известные учебные группы |
| GET | `/schedule/lessons?group=&from=&to=` | пары и состояние источника |
| GET | `/events?personId=&groups=&from=&to=` | события расписания |
| GET | `/events/{eventId}` | событие с учётом приватности |
| POST | `/events` | создать событие |
| PATCH | `/events/{eventId}` | частично изменить; автор или капитан |
| DELETE | `/events/{eventId}` | удалить; автор или капитан |
| GET | `/event-types` | базовые и личные типы |
| POST | `/event-types` | создать личный тип |
| PATCH | `/event-types/{eventTypeId}` | изменить личный тип |
| DELETE | `/event-types/{eventTypeId}` | удалить личный тип |
| GET | `/me/reminders` | карта напоминаний `event:<id>`/`lesson:<id>` |
| PUT | `/me/reminders/{itemKey}` | `{ "minutes": [60, 15] }`; пустой список удаляет настройку |

Видимость события: `TEAM`, `GROUP`, `PERSONAL`. Для чужого личного события применяются настройки `NOTHING`, `BUSY`, `TITLE`, `TITLE_AND_DESCRIPTION`. Напоминание задаётся в пределах 1–40320 минут.

Ответ расписания:

```json
{
  "lessons": [],
  "updatedAt": "2026-11-03T10:00:00+05:00",
  "source": "ACTUAL"
}
```

`source` принимает `ACTUAL`, `CACHED` или `UNAVAILABLE`.

## Уведомления

| Метод | Маршрут | Назначение |
|---|---|---|
| GET | `/notifications?limit=100` | уведомления, новые сверху |
| GET | `/notifications/stream` | SSE-поток новых уведомлений и изменений прочитанности |
| POST | `/notifications/read-all` | отметить все прочитанными, `204` |
| POST | `/me/push-tokens` | `{ "token", "platform": "android" }`, `204` |
| DELETE | `/me/push-tokens/{token}` | удалить push-токен, `204` |

Сервер создаёт уведомления типов `COMMENT`, `STATUS`, `RETURNED`, `EVENT`. Автору действия уведомление не отправляется. Фоновая задача каждую минуту создаёт наступившие напоминания о событиях и занятиях.

`GET /notifications/stream` требует обычный `Authorization: Bearer <accessToken>` и отвечает с `Content-Type: text/event-stream`. Событие `notification` содержит объект уведомления, `refresh` сообщает об изменении списка или прочитанности, `ready` подтверждает подключение. Поле SSE `id` равно идентификатору уведомления; при переподключении клиент передаёт его в `Last-Event-ID`. Для браузера нужен `fetch`/SSE-клиент с поддержкой заголовка `Authorization`, поскольку стандартный `EventSource` не позволяет задать этот заголовок.

## Системный маршрут

```http
GET /api/v1/system/health
```

Маршрут не требует авторизации и при успехе отвечает `200`.

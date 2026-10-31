# Запросы Android-приложения к backend — Спринт 2

Этот файл адресован backend-разработчику. Он фиксирует, какие запросы нужны Android-клиенту для ТЗ Спринта 2. В `develop` на 26.10.2026 нет серверного исходного кода или OpenAPI, поэтому пути и JSON ниже — **контракт на согласование**, а не утверждение, что сервер их уже реализует. Retrofit-интерфейс клиента находится в `data/BackendApi.kt`.

## JWT и общие правила

Базовый URL в Android задаётся Gradle-свойством `kanbanApiBaseUrl`, например `https://dev.example.org/api/`. Без него приложение остаётся в демонстрационном режиме. В сетевом режиме `POST auth/login` возвращает `accessToken`; клиент хранит его **только в памяти процесса** и добавляет `Authorization: Bearer <JWT>` ко всем последующим запросам через OkHttp-интерцептор. При выходе токен удаляется. JWT **проверяет сервер**, клиент не доверяет ролям или ID из интерфейса. Нужны HTTPS, срок действия токена и единый ответ на истечение (`401`). Refresh-схему нужно согласовать; пока после истечения токена требуется повторный вход. Токены нельзя писать в логи или возвращать в публичных DTO.

Дата: `YYYY-MM-DD`. Время события/дедлайна: ISO 8601 с зоной, например `2026-10-26T18:30:00+03:00`. Пустые списки возвращать как `[]`, а не `404`. Для ошибок согласовать JSON вида `{"code":"FORBIDDEN","message":"Нет доступа"}` и коды `400/401/403/404/409/422`.

## Реестр запросов

Обозначения: **К** — запрос объявлен в `BackendApi.kt`; **П** — дополнительно нужен по ТЗ, реализация клиента зависит от утверждённого серверного OpenAPI.

### Авторизация и профили

| Статус | Метод и путь от базового URL | Запрос → ответ |
|---|---|---|
| К | `POST auth/login` | `{login,password}` → `{accessToken}`. Пароль не возвращать. |
| К | `GET users/me` | → `id,name,login,role,position,active,joinedAt,studentGroup,contact?`. |
| П | `POST auth/refresh` | Refresh token/ротация — только после согласования способа хранения и ответа. |
| П | `POST auth/logout` | Отзыв refresh token, если он появится; access token клиент удаляет локально. |
| П | `GET users/{userId}` | Публичный профиль, контакты, роль в команде, статистика задач. |
| П | `PATCH users/me` | Сохранить учебную группу 203/204 между устройствами. Тема может остаться локальной. |

### Команды и проекты

| Статус | Метод и путь | Запрос → ответ |
|---|---|---|
| К | `GET teams` | → `[{id,name,description,memberIds,isMember}]`. |
| К | `GET teams/{teamId}/members` | → участники с ID, ФИО, ролью, контактом и статусом доступа. |
| П | `GET teams/{teamId}` | Карточка команды и права текущего пользователя. |
| К | `POST teams/{teamId}/members/me` | Вступить в команду, если это разрешено правилами. |
| К | `DELETE teams/{teamId}/members/me` | Выйти из команды; запретить потерю последнего тимлида. |
| К | `PATCH teams/{teamId}` | Тимлид меняет название/описание. |
| К | `POST teams/{teamId}/members` | Тимлид создаёт/приглашает аккаунт. Способ выдачи временного пароля согласовать. |
| К | `PATCH teams/{teamId}/members/{userId}` | Тимлид меняет роль/блокирует участника. |
| К | `GET projects` | → **все доступные проекты** с `id,teamId,name,description,memberIds,isMember`; Android группирует «Мои» и «Остальные». |
| К | `PATCH projects/{projectId}` | Тимлид: `{name,description}` → обновлённый проект. |
| К | `POST projects/{projectId}/members` | Тимлид: `{userId}` → обновлённый состав. |
| К | `DELETE projects/{projectId}/members/{userId}` | Тимлид исключает участника → обновлённый состав. |

Уточнить, может ли один пользователь иметь разные роли в разных командах: рекомендуем роль в `Team_Member`, а не один глобальный `User.role`. Обычный участник видит проекты команды, но не получает права редактировать их через прямой HTTP-запрос.

### Доска, задачи, аудит и файлы

| Статус | Метод и путь | Запрос → ответ |
|---|---|---|
| К | `GET projects/{projectId}/board` | → `projectId,columns,tasks`; задача содержит `id,teamId,projectId,title,description,status,assigneeId,assigneeName,dueAt,completedAt`. |
| П | `GET tasks/{taskId}` | Полная карточка, исходники, результаты, комментарии. |
| К | `GET tasks/{taskId}/history` | → упорядоченные `id,action,actorId,actorName,occurredAt,comment`. Серверный `task_history` — источник аудита, не локальный список. |
| К | `POST projects/{projectId}/tasks` | Тимлид: `{title,description,assigneeId,dueAt}` → созданная задача. |
| К | `POST tasks/{taskId}/take` | Участник берёт свободную/назначенную задачу → обновлённая задача; чужая занятая → `403/409`. |
| К | `PATCH tasks/{taskId}/status` | `{status,comment?}` → обновлённая задача. При возврате на доработку комментарий обязателен. |
| К | `POST tasks/{taskId}/comments` | `{text}` → комментарий с `authorId,createdAt`. |
| П | `GET projects/{projectId}/tasks?status=DONE&completedFrom=...&completedTo=...` | История проекта с пагинацией и фильтром по периоду. |
| К | `POST tasks/{taskId}/attachments` | `multipart/form-data`: файл и назначение (`SOURCE`/`RESULT`); ответ с ID и метаданными. Клиент ограничивает размер 10 МБ. |
| П | `GET tasks/{taskId}/attachments` | Исходники и результаты с правами доступа. |
| К | `GET attachments/{attachmentId}/download` | Авторизованная загрузка; Android сохраняет поток через системный выбор файла. |

Серверная матрица прав: только назначенный исполнитель может перевести свою задачу «В работу» и сдать результат; Team Lead может создавать/назначать/редактировать/удалять задачи, принять результат или вернуть на доработку с замечанием. Участник может взять свободную задачу, если это допускает проект. Каждая мутация должна атомарно добавить запись `task_history`; иначе мобильная и веб-история разойдутся. Статусы или ID колонок нужно стандартизировать в OpenAPI: сейчас мобильный код использует `WAITING`, `WORKING`, `CHECKING`, `DONE`.

### Расписание, события, типы и напоминания

| Статус | Метод и путь | Запрос → ответ |
|---|---|---|
| К | `GET schedule/lessons?group=203&from=...&to=...` | Пары ИСУ: `startsAt,endsAt,title,room,teacher,kind` (`LECTURE/PRACTICE/LAB`) и метка времени обновления кеша/fallback. |
| К | `GET events?teamId=...&group=...&from=...&to=...` | Только доступные пользователю командные, групповые и личные события. |
| П | `GET events/{eventId}` | Полное описание/место по ID; чужое личное событие недоступно. |
| К | `POST events` | `title,description,location,startsAt,endsAt,visibility,teamId?,group?,typeId,reminderMinutes` → событие с серверным ID. |
| П | `PATCH events/{eventId}` / `DELETE events/{eventId}` | Редактирование/удаление при появлении управления в UI. |
| К | `GET event-types` | Базовые и пользовательские типы: `id,title,color,custom,defaultReminderMinutes`. |
| К | `POST event-types` | Создать тип с `title,color,defaultReminderMinutes`. |
| К | `PATCH event-types/{typeId}` | Изменить название, hex-цвет и интервалы созданного типа. |

`visibility`: `TEAM`, `GROUP`, `PERSONAL`. Для `PERSONAL` сервер выводит владельца из JWT, не из поля запроса. Для `GROUP` проверяет принадлежность к учебной группе, для `TEAM` — членство и права в команде. Кастомные интервалы хранятся целым количеством минут; допустимый диапазон и охват пользовательского типа нужно согласовать. Android сейчас планирует **локальные** уведомления через `AlarmManager`; если нужны серверные FCM push, потребуются отдельные регистрация устройства и правила дедупликации напоминаний.

Пример создания события:

```http
POST /api/events
Authorization: Bearer <JWT>
Content-Type: application/json

{"title":"Созвон","description":"Итоги спринта","location":"Discord", "startsAt":"2026-10-27T18:00:00+03:00", "endsAt":"2026-10-27T19:00:00+03:00", "visibility":"TEAM", "teamId":1, "group":null, "typeId":"meeting", "reminderMinutes":[60,30]}
```

## Что нужно от backend перед включением сетевого режима

1. OpenAPI со **всеми** полями ответов, ошибками и матрицей прав. Особенно подтвердить формат `GET projects/{id}/board`, роль в команде, `isMember` и JWT login response.
2. Адрес HTTPS dev-сервера, тестовые аккаунты Team Lead и Member, срок JWT. Не размещать секреты и токены в Git.
3. Готовые миграции `Team`/`Project`/`task_history`, Attachment API, кеш ИСУ для 203/204 и сценарий синхронизации событий между устройствами.
4. Согласованную схему refresh token либо решение о повторном входе после `401`.

Сборка с `-PkanbanApiBaseUrl=https://host.example/api/` включает сетевой режим. Без URL остаётся демонстрационный режим; он **не доказывает** работу JWT или синхронизации. До появления сервера сквозную проверку прав и ответов выполнить невозможно.

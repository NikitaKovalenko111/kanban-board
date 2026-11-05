# Kanban Board — frontend

React, TypeScript, Vite, Redux Toolkit, Redux Saga, React Router, SCSS, Axios.

## Реальный backend

По умолчанию используется обновлённый API `/api/v1`. Подключены профиль и роли, проекты и участники, Kanban, комментарии/история, файлы, события/пары, личные типы событий, приватность и уведомления. Backend не изменялся. Точные ограничения и проверенные ошибки сервера — в [отчёте](../docs/FRONTEND_BACKEND_INTEGRATION.md).

Из client:

```sh
npm ci
cp .env.example .env.local
npm run dev
```

PowerShell: `Copy-Item .env.example .env.local`.

`API_PROXY_TARGET` задаёт адрес запущенного backend (по умолчанию http://127.0.0.1:3005). Vite сохраняет префикс /api/v1; CORS менять не нужно. Production использует существующий Nginx. Старый VITE_API_URL=/api нормализуется в /api/v1; рекомендуется обновить .env.local явно.

Нужен существующий аккаунт. Самостоятельная регистрация на сервере отключена, аккаунты создаёт капитан через интерфейс участников. Первый капитан должен быть подготовлен администратором сервера; автоматического bootstrap API нет. Демо-пароли на реальном сервере не работают.

Токены хранятся в sessionStorage; пароль и токены не попадают в Redux. После перезагрузки выполняется ротация refresh и загрузка /me. Роли берутся из профиля CAPTAIN/MEMBER. После выхода очищаются данные аккаунта; поздние HTTP-ответы старой сессии отклоняются.

В текущем backend выход и смена пароля возвращают 404 из-за перекрытия роутеров auth. При выходе локальная сессия очищается, интерфейс сообщает, что серверный отзыв не подтверждён. Смена пароля не выдаёт ложного успеха. После исправления маршрутизации серверной командой клиент уже отправляет правильные защищённые запросы.

Файлы до сохранения формы находятся в памяти вкладки; после создания родительской записи отправляются multipart в контекст проекта/задачи. Предел backend — 10 МБ на файл, 5 МБ на аватар. При частичной ошибке создания/загрузки интерфейс сообщает, что запись уже создана: открыть её и повторить загрузку, а не создавать дубликат.

## Настройки и режимы

```dotenv
VITE_API_MODE=backend
VITE_API_URL=/api/v1
API_PROXY_TARGET=http://127.0.0.1:3005
```

Для отдельной демонстрации задайте VITE_API_MODE=mock и перезапустите Vite. Мок-аккаунты: alexey / lead123, maria / member123. Автоматического перехода на моки при сетевых ошибках нет. В mock-режиме бизнес-данные в памяти, файлы в IndexedDB, настройки в localStorage.

VITE_* встраиваются в bundle при сборке; не храните там секреты. Смена production-переменных требует новой сборки. Unit-тесты без явного режима используют моки.

У событий backend нет projectId: в серверном расписании проектный переключатель скрыт. Группы загружаются с сервера; поле группы появляется только для события GROUP. Типы событий личные, базовые типы доступны только для чтения. Напоминание в UI одно, от 1 до 40320 минут; ограничения фактического наследования/переопределения на сервере описаны в отчёте и показаны в форме.

## Проверки

```sh
npm test
npm run lint
npm run build
```

Тесты рядом с кодом: src/**/*.test.ts и src/**/*.test.tsx. Общие фикстуры — src/test. Контракты интеграции проверяют src/api/backendApi.test.ts, realApi.test.ts, config.test.ts и src/App.backend.test.tsx.

После git pull при изменении package-lock.json выполните npm ci.

## Сквозная проверка на временном стеке

scripts/backend-check.compose.yaml собирает неизменённый backend, отдельные PostgreSQL и MinIO в tmpfs. Основные данные не используются. API — 127.0.0.1:13005. PowerShell из client:

```powershell
$env:BACKEND_CHECK_PASSWORD = [guid]::NewGuid().ToString('N')
$env:BACKEND_CHECK_ACCESS_SECRET = [guid]::NewGuid().ToString('N')
$env:BACKEND_CHECK_REFRESH_SECRET = [guid]::NewGuid().ToString('N')
docker compose -p kanban-client-integration -f scripts/backend-check.compose.yaml up -d --build --wait
node scripts/backend-smoke.mjs
$env:API_PROXY_TARGET = 'http://127.0.0.1:13005'
npm run dev -- --host 127.0.0.1 --port 5175
```

Smoke-скрипт проверяет принадлежность контейнера тестовому проекту, создаёт тестового капитана через SQL **только в этой временной БД** (public register отключён), остальных пользователей — через API. Проверяются проекты, членство, задачи, файлы MinIO, доработка, архив, события, напоминания, уведомления и известный 404 auth/logout/change-password.

Тестовые реквизиты пишутся только в игнорируемый .backend-check/user.json, не в вывод. Дополнительный scripts/browser-check.mjs использует Chromium DevTools Protocol localhost:9235 и проверяет полный интерфейс под капитаном и участником: для него нужны отдельный Chromium с remote debugging и запущенный Vite :5175.

После проверок остановите Vite и в той же PowerShell-сессии:

```powershell
docker compose -p kanban-client-integration -f scripts/backend-check.compose.yaml down
```

PostgreSQL и MinIO этого стека используют tmpfs: после остановки контейнеров тестовые данные исчезают.

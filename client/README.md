# Kanban Board — клиент

React, TypeScript, Vite, Redux Toolkit, Redux Saga, React Router, SCSS, Axios.

По умолчанию используется существующий Go backend через `/api`. Сейчас сервер публикует только авторизацию и healthcheck. Подключены вход, восстановление/обновление токенов и выход. Остальные разделы показывают состояние недоступности, поскольку их HTTP API ещё нет. Полный перечень несовпадений: [отчёт в корневом docs](../docs/FRONTEND_BACKEND_INTEGRATION.md).

## Запуск с сервером

```sh
cd client
npm ci
cp .env.example .env.local
npm run dev
```

В PowerShell: `Copy-Item .env.example .env.local`.

`API_PROXY_TARGET` в .env.local — адрес уже запущенного backend, по умолчанию `http://127.0.0.1:3005`. Vite передаёт `/api/*` на него без префикса `/api`, поэтому менять CORS бэкенда не нужно. Production Nginx уже делает такое же проксирование к backend:3005.

Нужен существующий серверный аккаунт. Демо-пароли с backend не работают. Интерфейс самостоятельной регистрации не добавлен: backend требует email и группу, не поддерживает проверку роли капитана и не принимает поля текущей формы участника. Подробности — в отчёте.

Токены находятся в sessionStorage текущей вкладки. После перезагрузки сессия подтверждается через refresh. В Redux — только UUID и введённый логин, без токенов и фиктивной роли. Сетевая ошибка выхода очищает локальную сессию и сообщает, что серверный отзыв не подтверждён.

## Отдельный mock-режим

В .env.local задайте `VITE_API_MODE=mock` и перезапустите Vite. Аккаунты: `alexey / lead123`, `maria / member123`. Бизнес-данные моков находятся в памяти, файлы — в IndexedDB, mock-сессия, тема и выбор проекта — в localStorage. Ошибки backend никогда автоматически не включают моки.

`VITE_API_MODE=backend` — серверный режим (по умолчанию), `VITE_API_URL=/api` — базовый URL. VITE_* встраиваются в bundle при сборке; не храните в них секреты. После изменения production-переменных нужна новая сборка. Unit-тесты без явной настройки используют mock-режим.

## Проверки

```sh
npm test
npm run test:watch
npm run build
npm run lint
```

Тесты лежат рядом с кодом в src/**/*.test.ts и src/**/*.test.tsx, общие данные — в src/test. Интеграционные проверки: src/api/backendApi.test.ts, src/api/config.test.ts, src/App.backend.test.tsx. Они покрывают DTO, UUID, ошибки, ротацию, параллельные 401, отзыв сессии, отсутствие mock-данных и UI входа/выхода.

После git pull, если изменились зависимости, выполните npm ci.

## Сквозная проверка на отдельной БД

scripts/backend-check.compose.yaml собирает существующий backend без правок и запускает PostgreSQL в tmpfs. Основной том БД не используется; API доступен только локально на порту 13005. Из client в PowerShell:

```powershell
$env:BACKEND_CHECK_PASSWORD = [guid]::NewGuid().ToString('N')
$env:BACKEND_CHECK_ACCESS_SECRET = [guid]::NewGuid().ToString('N')
$env:BACKEND_CHECK_REFRESH_SECRET = [guid]::NewGuid().ToString('N')
docker compose -p kanban-client-integration -f scripts/backend-check.compose.yaml up -d --build --wait
node scripts/backend-smoke.mjs
$env:API_PROXY_TARGET = 'http://127.0.0.1:13005'
npm run dev
```

Smoke-скрипт создаёт тестовый аккаунт только по фиксированному адресу этого стека. Реквизиты для браузерной проверки попадают в игнорируемый .backend-check/user.json; токены в вывод не печатаются. Скрипт проверяет реальные register/login/refresh/logout/health.

После остановки Vite в той же PowerShell-сессии:

```powershell
docker compose -p kanban-client-integration -f scripts/backend-check.compose.yaml down
```

Остановка удаляет тестовую БД в tmpfs. Следующий запуск smoke-скрипта создаст новый тестовый аккаунт.

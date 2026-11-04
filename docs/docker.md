# Запуск в Docker

Требования: Docker Engine и Docker Compose с поддержкой `--wait`.

1. Скопировать `.env.example` в `.env` в корне проекта.
2. Задать в `.env` непустой `POSTGRES_PASSWORD` (например, случайную строку из букв и цифр). При необходимости изменить `HTTP_PORT`, по умолчанию 8080.
3. Из корня выполнить:

   ```sh
   docker compose config --quiet
   docker compose up -d --build --wait --wait-timeout 180
   docker compose ps
   curl --fail http://localhost:8080/health
   curl --fail http://localhost:8080/api/system/health
   ```

Открыть `http://localhost:8080`. Вложенные страницы React обслуживаются через SPA fallback.
Nginx передаёт `/api/*` на backend, удаляя префикс `/api`, и `/health` на `/system/health` backend.
Текущий клиент использует mock API; подключение его операций к реальному backend — отдельная задача.

## Устройство

- `client/Dockerfile`: сборка TypeScript/Vite через `npm ci`, затем раздача `dist` через Nginx.
- `backend/Dockerfile`: сборка Go-бинарника без CGO, запуск под непривилегированным пользователем.
- `compose.yaml`: frontend, backend и PostgreSQL 17. На хост публикуется только frontend.
- PostgreSQL хранит данные в именованном томе `postgres_data`. Backend запускается после готовности БД, frontend — после готовности backend.
- Backend получает настройки через существующие переменные окружения с именами в нижнем регистре. Таблицы и индексы создаёт сам backend при запуске.
- Docker healthcheck проверяет каждый сервис. `/system/health` backend подтверждает работу HTTP; постоянную доступность БД после старта этот маршрут не проверяет.
- `.env` исключён из Git, секреты и локальные артефакты исключены из Docker-контекстов.

## Обслуживание

```sh
docker compose logs --tail=100 backend frontend postgres
docker compose up -d --build --wait --wait-timeout 180
docker compose down
```

`down` сохраняет том БД. Опция `down -v` удаляет данные — не использовать её для обычного обновления.
Изменение пароля в `.env` не меняет пароль уже созданного пользователя в существующем томе PostgreSQL; для этого нужна отдельная смена пароля в БД.

На сервере `.env` создаётся в каталоге проекта до первого деплоя. В текущем CD команда Compose уже запускается из корня проекта и обнаружит `compose.yaml` автоматически.

## Результаты локальной проверки этапа 2

- `npm --prefix client run build`: успешно.
- `docker compose config --quiet`: успешно с тестовым паролем через окружение.
- Оба Docker-образа собраны; отдельный стек `kanban-stage2-check` запущен на порту 18080 с временным случайным паролем.
- `docker compose ... up --wait`: успешно, postgres, backend и frontend имеют статус healthy.
- HTTP GET `/`, `/profile`, `/health`, `/api/system/health`: все вернули 200.
- `nginx -t`: успешно; `id` в backend подтвердил пользователя `app`, uid 100.
- При первом запуске найден и исправлен неверный путь healthcheck: реальный маршрут backend — `/system/health`.
- `git diff --check`: успешно; `.env` и `.env.production` игнорируются Git.
- При установке npm сообщил о пяти уязвимостях высокой серьёзности; сборка также выводит предупреждения об устаревающем Sass `@import`. Зависимости в этом этапе не обновлялись.
- Реальный сервер и GitHub Actions в этом этапе не проверялись; браузерные сценарии и операции реального API не тестировались.

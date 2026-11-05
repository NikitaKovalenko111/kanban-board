# Запуск в Docker

Требования: Docker Engine и Docker Compose с поддержкой `--wait`.

1. Скопировать `.env.example` в `.env` в корне проекта.
2. Задать в `.env` непустой `POSTGRES_PASSWORD`, `MINIO_ROOT_USER` и `MINIO_ROOT_PASSWORD` (не менее 8 символов, рекомендуется длинный случайный пароль). При необходимости изменить `HTTP_PORT` (8080), `MINIO_BUCKET` (kanban-attachments) и `MINIO_CONSOLE_PORT` (9001).
3. Из корня выполнить:

   ```sh
   docker compose config --quiet
   docker compose up -d --build --wait --wait-timeout 180
   docker compose ps
   curl --fail http://localhost:8080/health
   curl --fail http://localhost:8080/api/system/health
   ```

Открыть `http://localhost:8080`. Вложенные страницы React обслуживаются через SPA fallback.
Nginx передаёт `/api/v1/*` на backend без изменения префикса. `/health` и совместимый deployment probe `/api/system/health` направляются на `/api/v1/system/health`. Для прежних `/api/auth/*` сохраняется удаление префикса `/api`.
Текущий клиент использует mock API; подключение его операций к реальному backend — отдельная задача.

## Устройство

- `client/Dockerfile`: сборка TypeScript/Vite через `npm ci`, затем раздача `dist` через Nginx.
- `backend/Dockerfile`: сборка Go-бинарника без CGO, запуск под непривилегированным пользователем.
- `compose.yaml`: frontend, backend, PostgreSQL 17 и MinIO. Наружу публикуется frontend; консоль MinIO доступна только на localhost.
- PostgreSQL хранит данные в `postgres_data`, MinIO — в отдельном томе `minio_data`. Backend ждёт healthy PostgreSQL и MinIO, frontend — healthy backend.
- Backend получает настройки через существующие переменные окружения с именами в нижнем регистре. Таблицы и индексы создаёт сам backend при запуске.
- Docker healthcheck проверяет каждый сервис. `/api/v1/system/health` backend подтверждает работу HTTP; постоянную доступность БД и S3 после старта этот маршрут не проверяет.
- `.env` исключён из Git, секреты и локальные артефакты исключены из Docker-контекстов.

## Обслуживание

```sh
docker compose logs --tail=100 backend frontend postgres
docker compose up -d --build --wait --wait-timeout 180
docker compose down
```

`down` сохраняет том БД. Опция `down -v` удаляет данные — не использовать её для обычного обновления.
Изменение пароля в `.env` не меняет пароль уже созданного пользователя в существующем томе PostgreSQL; для этого нужна отдельная смена пароля в БД.

## MinIO

Backend получает существующие переменные `minio_enabled=true`, `minio_endpoint=minio:9000`, ключи из `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`, `minio_bucket` и `minio_use_ssl=false`. Бакет создаётся самим backend при первом запуске; отдельного init-контейнера и публичной политики бакета нет. S3 API 9000 не публикуется на хост, загрузка и скачивание идут через файловый API backend. `use_ssl=false` относится к внутренней сети Compose.

Консоль: `http://127.0.0.1:9001` (или MINIO_CONSOLE_PORT). На удалённом сервере можно использовать SSH-туннель. В этой конфигурации backend использует ключи MinIO root; не передавайте их в браузер или frontend-переменные. Замена ключей требует перезапуска и MinIO, и backend.

Официальная [MinIO Community Edition распространяется как исходный код](https://github.com/minio/minio#source-only-distribution); старые образы minio/minio в Docker Hub/Quay недоступны. Использована публичная [community-сборка PGSTY](https://hub.docker.com/r/pgsty/minio), версия RELEASE.2026-08-04T00-00-00Z с закреплённым SHA256 digest. Для обновления нужно явно выбрать и проверить новую версию и digest; latest не используется.

`docker compose down` сохраняет оба тома. Не удаляйте minio_data при обычном обновлении: там содержимое файлов. Резервное копирование должно включать и PostgreSQL, и MinIO; данные о файле в БД без самого объекта недостаточны.

Ранее загруженные файлы из локального каталога `data` backend автоматически в MinIO не переносятся. При переходе существующей установки сохраните их и перенесите объекты с прежними storage_key до удаления старого контейнера. Изменение MINIO_BUCKET также не переносит содержимое между бакетами.

### Проверка MinIO — 5 ноября 2026

Проверено в ветке `devops-backend-minio-hotfix` на отдельном Compose-проекте `kanban-minio-check`, с временными секретами и отдельными томами, без изменения backend:

- `docker compose config --quiet` и сборка обоих приложений успешны.
- Все четыре сервиса healthy; backend автоматически создал `kanban-attachments`.
- Запись/чтение тестового объекта через S3 успешны; после `up --force-recreate --no-deps minio` содержимое сохранилось.
- Консоль MinIO вернула HTTP 200 через localhost-порт; API 9000 на хост не опубликован.
- Nginx `-t` успешен; `/`, `/profile`, `/health`, `/api/system/health`, `/api/v1/system/health` вернули HTTP 200.
- Все семь control-flow проверок `scripts/test-deploy.sh` прошли; синтаксис deployment-скриптов проверен.
- В CI добавлена проверка Compose с тестовыми переменными и deployment-скриптов. Удалённый запуск CI/CD в рамках этой задачи не выполнялся.

Тестовый стек и его отдельные тома после проверки удалены. Рабочие данные и production-окружение не использовались.

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

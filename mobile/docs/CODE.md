# Карта кода

Пакет: `com.kanbanboard.mobile`. Исходники — `app/src/main/java/com/kanbanboard/mobile/`, тесты — `app/src/test/java/...`.

## Слои

```
UI (feature/*)  →  ViewModel (StateFlow<UiState>)  →  репозиторий (интерфейс, core/data/Repositories.kt)
                                                        ├── демо: core/data/demo/*  (сейчас)
                                                        └── сеть: появится при подключении API
```

- **Однонаправленный поток данных.** Экран получает `UiState`, отправляет события в ViewModel; ViewModel вызывает репозиторий.
  Разовые сообщения (снекбары) идут через `Channel`.
- **Права — в одном месте:** `core/policy/*`. Их используют и экраны (что показать), и демо-сервер (что разрешить).
- **DI вручную:** `di/AppContainer.kt` создаёт все зависимости, экраны берут их через `LocalAppContainer`.

## Дерево

```
KanbanApplication.kt        создаёт AppContainer, запускает фоновые синхронизации
MainActivity.kt             edge-to-edge, тема, обои, корневой NavHost; RootState (вошёл ли, тема, обои)
di/
  AppContainer.kt           FileStore, DemoBackend, репозитории, ReminderSync, TaskPushSync
navigation/
  Routes.kt                 типобезопасные маршруты (@Serializable)
  AppNavHost.kt             граф экранов, анимации переходов (вперёд — слайд, назад — быстрое затухание 220 мс)
core/
  model/
    Serializers.kt          JSON-сериализаторы Instant, LocalDate, LocalTime (ISO-8601)
    Models.kt               пользователи, команда, проекты, колонки (ColumnKind.forPosition), задачи, вложения,
                            уведомления, SchedulePrivacy, черновики (TaskDraft, ColumnDraft, NewAccount, PickedFile)
    Schedule.kt             пары (Lesson), события, типы событий, LessonsSnapshot/LessonsOrigin, ReminderPresets
    AppError.kt             типизированные ошибки (Validation, Forbidden, NotFound, InvalidCredentials, …)
  policy/
    TaskPolicy.kt           права по задачам: взять, переместить, загрузить файл, quickMove для кнопок
    EventPolicy.kt          права по событиям и present() — что зритель видит о чужом событии по приватности
    ProjectPolicy.kt        права по проектам и источникам, проверка ссылок
  data/
    Repositories.kt         интерфейсы: Session, Team, Notification, Project, Board, Schedule
    demo/
      DemoBackend.kt        демо-сервер: состояние DemoState, mutate() под Mutex, все проверки прав, уведомления,
                            аккаунты (создание, блокировка, сброс и смена пароля, роли, удаление), задачи, колонки
      DemoProjects.kt       операции с проектами и источниками
      DemoSchedule.kt       события, типы, напоминания, eventsFor() с учётом приватности
      DemoSeed.kt           начальное состояние: команда, тимлид nikita и участник samir, базовые типы событий
      DemoRepositories.kt   реализации интерфейсов репозиториев поверх DemoBackend (+ удаление файлов с устройства)
      DemoStore.kt          сохранение DemoState в filesDir/demo/state.json и запоминание входа
    schedule/
      LessonsSource.kt      источник пар: IsuLessonsSource (сеть + JSON-кэш) и DemoLessonsSource; IsuConfig
      IsuScheduleParser.kt  разбор HTML-страницы расписания ИСУ (Jsoup)
    settings/
      SettingsRepository.kt DataStore: тема, стиль панели, анимация, обои, push, папка сохранения, активный проект
  files/
    FileStore.kt            файлы приложения (filesDir/store): импорт из Uri, демо-файлы, экспорт через SAF в «папку/проект/»
  notifications/
    Reminders.kt            AlarmManager-напоминания о парах и событиях (на 30 дней вперёд), ReminderReceiver
    TaskPush.kt             локальные push о задачах по новым уведомлениям (канал task_updates)
    PermissionUi.kt         запрос POST_NOTIFICATIONS в нужный момент
  designsystem/
    Theme.kt                цветовые схемы (светлая, тёмная, AMOLED), MaterialTheme.ext (success/warning/danger/…),
                            формы, KanbanTheme, цвета приоритетов и видов пар, AppPalette (24 цвета)
    Components.kt           общие компоненты на M3: Caption, Panel, Tag, AppChip, AppButton, AppTopBar,
                            SectionHeader, SpecRow, EmptyState, UserAvatar, ColorDot, Hairline
    Pickers.kt              M3-диалоги даты и времени
    Images.kt               rememberLocalImage — загрузка локальных изображений с даунсэмплингом
  time/
    Formats.kt              дд.мм.гггг, время, размер файла
feature/
  auth/LoginScreen.kt       вход
  main/MainScreen.kt        вкладки и нижняя панель (статичная NavigationBar или плавающий овал)
  board/                    доска: BoardViewModel, BoardScreen (вкладки колонок + HorizontalPager), TaskCard
  columns/ColumnsScreen.kt  редактор колонок и ColorPickerDialog
  task/                     карточка задачи (TaskDetail*) и редактор (TaskEditor*)
  schedule/                 лента (ScheduleScreen/ViewModel), событие (EventDetail/EventEditor), типы, напоминания
  projects/                 список, карточка и настройки проекта
  history/                  история выполненных задач
  profile/
    ProfileScreen.kt        главный экран профиля, profileViewModel()
    ProfileViewModel.kt     общий ViewModel для всех экранов профиля
    SettingsScreens.kt      «Настройки аккаунта» и «Настройки приложения»
    NotificationsScreen.kt  уведомления (открытие = прочитано)
    MembersScreen.kt        участники с поиском, управление аккаунтами (капитан)
  files/FileViewerScreen.kt просмотр файла (изображение, PDF, текст), открыть во внешнем приложении, сохранить
```

Ресурсы: `res/values/themes.xml` (окно и splash), `res/drawable/ic_launcher.xml`, `ic_notification.xml`,
`res/xml/file_paths.xml` (FileProvider для `filesDir/store`).

## Тесты (`app/src/test`)

| Файл | Что проверяет |
|---|---|
| `core/policy/TaskPolicyTest.kt` | матрица прав по задачам, QA-01, QA-02/BUG-08, вид колонки по позиции |
| `core/data/TestTeam.kt` | общая подготовка: чистый демо-сервер, второй участник и проект создаются как в приложении |
| `core/data/DemoBackendTest.kt` | seed без мок-данных, вход, QA-01, цикл проверки с возвратом, BUG-08, удаление файлов, колонки, уведомления |
| `core/data/DemoAccountsTest.kt` | создание аккаунтов, блокировка, сброс и смена пароля, роли, удаление аккаунта |
| `core/data/DemoProjectsTest.kt` | создание и удаление проектов, состав, проверка ссылок, удаление источников |
| `core/data/DemoScheduleTest.kt` | все 4 уровня приватности, групповые события, личные напоминания, время, удаление типа |
| `core/data/DemoStoreTest.kt` | сохранение и загрузка состояния и входа, повреждённый файл |
| `core/data/IsuScheduleParserTest.kt` | разбор синтетической страницы расписания |

Запуск: `./gradlew test`.

## Как добавить экран

1. Маршрут в `navigation/Routes.kt` (`@Serializable data class XxxRoute(...)`).
2. `composable<XxxRoute>` в `navigation/AppNavHost.kt`; навигацию вызывать только при `entry.isResumed()` (защита от двойных нажатий).
3. Экран в `feature/<раздел>/`: `XxxRouteScreen` (берёт ViewModel из `LocalAppContainer`) + приватный `XxxScreen(state, …)`.
4. Тексты — с заглавной буквы, без служебных подписей; компоненты — из `core/designsystem` или стандартные M3.

## Как подключить backend

1. Описать DTO и Retrofit/Ktor-клиент (kotlinx.serialization уже подключён).
2. Написать `Network*Repository` для каждого интерфейса из `core/data/Repositories.kt`.
   Правила, которые сейчас проверяет `DemoBackend`, должен проверять сервер; `core/policy` остаётся для UI.
3. В `di/AppContainer.kt` заменить демо-реализации на сетевые. Экраны менять не нужно.
4. Файлы: `FileStore.import()` уже даёт локальную копию — её нужно отправить в Attachment API, а `fileRef` заменить на id вложения
   с докачкой в `FileStore` при открытии.
5. Push о задачах: заменить `TaskPushSync` на FCM.

## Расписание ИСУ

Целевая архитектура: парсер ИСУ работает на backend, Android получает готовые пары через REST API. Контракт и распределение ответственности описаны в [ISU_BACKEND.md](ISU_BACKEND.md). Пункты ниже описывают только текущий демо-источник Android.

- `IsuConfig` берёт шаблон адреса и названия групп из `BuildConfig` (свойства Gradle `isuUrlTemplate`, `isuGroup203`, `isuGroup204`).
- `IsuLessonsSource.lessons()` сразу отдаёт кэш (`filesDir/isu/<группа>.json`), затем не чаще раза в 30 минут
  скачивает страницу (OkHttp), разбирает `IsuScheduleParser` и обновляет кэш. Источник данных — в `LessonsSnapshot.source`:
  `ISU`, `CACHE` (сеть недоступна) или `UNAVAILABLE` (нет ни сети, ни кэша — пар нет) — экран показывает плашку.
- Парсер не привязан к CSS-классам: ищет заголовки дней (день недели, дата `дд.мм.гггг`) и строки таблиц со временем
  `08:00-09:35` или номером пары, вид занятия, аудиторию и преподавателя «Фамилия И.О.».
- Проверка реальной разметки и тесты парсера переходят на backend. После подключения API прямой HTML-разбор в сетевом режиме Android не нужен.

## Демо-данные и сохранение

`DemoSeed.create()` даёт только команду, два аккаунта (`nikita` — капитан, `samir` — участник) и базовые типы событий.
`DemoStore` загружает сохранённое состояние при запуске (или берёт seed) и записывает его в JSON через 300 мс
после каждого изменения; там же хранится, кто вошёл. Модели помечены `@Serializable` — те же классы пригодятся
для DTO REST API.

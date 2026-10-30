# Kanban Board — клиент

React + TypeScript + Vite, Redux Toolkit и Redux Saga. Данные хранятся в памяти mock API; сессия и выбор проекта — в localStorage.

## Запуск

```bash
npm ci
npm run dev
```

## Проверки

```bash
npm test             # однократный запуск всех тестов
npm run test:watch   # перезапуск при изменениях
npm run build        # проверка типов и production-сборка
npm run lint
```

Тесты используют Vitest, jsdom и React Testing Library. Основное покрытие:

- `src/api/mockApi.test.ts`: вход, ошибки, создание и изменение задач, история, копирование данных, проекты, расписание и участники.
- `src/store/slices/state.test.ts`: основные переходы всех пяти срезов и сохранение пользовательских данных в localStorage.
- `src/store/sagas/tasksSaga.test.ts`: передача данных между API и store, обработка ошибки загрузки.
- `src/components/components.test.tsx`: пустая колонка, выбор карточки, создание задачи и отправка результата на проверку.

Проверки компонентов ограничены основными пользовательскими действиями: без снапшотов, CSS и подробного тестирования каждой страницы. Общие данные находятся в `src/test/fixtures.ts`, очистка DOM и localStorage — в `src/test/setup.ts`. API-тесты изолируют данные повторным импортом модуля и пропускают искусственную задержку с помощью fake timers. Тесты запускаются в frontend-job CI перед сборкой.

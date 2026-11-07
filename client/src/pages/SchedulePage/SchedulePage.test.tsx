import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import projects from '../../store/slices/projectsSlice'
import schedule, { createScheduleEventRequest, createScheduleEventSuccess, loadScheduleSuccess, scheduleFailure } from '../../store/slices/scheduleSlice'
import team from '../../store/slices/teamSlice'
import { project, scheduleEvent, user } from '../../test/fixtures'
import SchedulePage from './SchedulePage'

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }))
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
})
afterEach(() => vi.unstubAllGlobals())

function setup(withProjects = false) {
  const store = configureStore({
    reducer: { auth, projects, schedule, team },
    preloadedState: {
      auth: { user, loading: false, error: null },
      projects: { items: withProjects ? [project] : [], activeProjectId: project.id, selections: { schedule: withProjects ? project.id : 'all' }, loading: false, saving: false, error: null },
    },
  })
  const dispatch = vi.spyOn(store, 'dispatch')
  render(<Provider store={store}><MemoryRouter initialEntries={['/schedule?date=2026-10-23']}><SchedulePage /></MemoryRouter></Provider>)
  return { store, dispatch }
}

it('скрывает поля на телефоне и сохраняет фильтры после сворачивания', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }))
  setup(true)
  const toggle = screen.getByRole('button', { name: 'Фильтры и настройки' })
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  expect(screen.queryByRole('searchbox', { name: 'Поиск событий' })).not.toBeInTheDocument()
  expect(screen.queryByRole('group', { name: 'Категории событий' })).not.toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: 'Выбор дня недели' })).toBeVisible()
  await userEvent.click(toggle)
  expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await userEvent.type(screen.getByRole('searchbox', { name: 'Поиск событий' }), 'Встреча')
  await userEvent.click(screen.getByRole('checkbox', { name: 'Личные' }))
  expect(screen.getByLabelText('День')).toBeVisible()
  await userEvent.click(toggle)
  expect(screen.queryByRole('searchbox', { name: 'Поиск событий' })).not.toBeInTheDocument()
  expect(screen.getByLabelText('День')).not.toBeVisible()
  await userEvent.click(toggle)
  expect(screen.getByRole('searchbox', { name: 'Поиск событий' })).toHaveValue('Встреча')
  expect(screen.getByRole('checkbox', { name: 'Личные' })).not.toBeChecked()
})

it('оставляет поля расписания раскрытыми на десктопе', () => {
  setup(true)
  expect(screen.queryByRole('button', { name: 'Фильтры и настройки' })).not.toBeInTheDocument()
  expect(screen.getByRole('searchbox', { name: 'Поиск событий' })).toBeVisible()
  expect(screen.getByRole('group', { name: 'Категории событий' })).toBeVisible()
})

it('показывает командные и групповые события вместе, исключая личные и пары', async () => {
  const { store } = setup(true)
  act(() => { store.dispatch(loadScheduleSuccess([
    { ...scheduleEvent, id: 1, title: 'Событие команды', category: 'team' },
    { ...scheduleEvent, id: 2, title: 'Событие группы', category: 'group' },
    { ...scheduleEvent, id: 3, title: 'Личное событие', category: 'personal', ownerId: user.id, projectId: undefined },
    { ...scheduleEvent, id: 4, title: 'Пара по React', type: 'class', category: 'group' },
  ])) })
  expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(4)
  await userEvent.click(screen.getByRole('checkbox', { name: 'Личные' }))
  await userEvent.click(screen.getByRole('checkbox', { name: 'Пары' }))
  expect(screen.getByRole('heading', { name: 'Событие команды' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Событие группы' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Личное событие' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Пара по React' })).not.toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: 'Все' })).toBePartiallyChecked()
  await userEvent.click(screen.getByRole('checkbox', { name: 'Все' }))
  expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(4)
  await userEvent.click(screen.getByRole('checkbox', { name: 'Все' }))
  expect(screen.queryAllByRole('heading', { level: 3 })).toHaveLength(0)
  expect(screen.getByText('Нет событий')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('checkbox', { name: 'Пары' }))
  expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(1)
  expect(screen.getByRole('heading', { name: 'Пара по React' })).toBeInTheDocument()
})

it('показывает пары только в фильтре, сохраняя три категории создания', async () => {
  setup(true)
  expect(screen.getByRole('checkbox', { name: 'Пары' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Добавить событие' }))
  const category = screen.getByLabelText('Категория') as HTMLSelectElement
  expect([...category.options].map(option => option.value)).toEqual(['personal', 'team', 'group'])
  expect(within(category).queryByRole('option', { name: 'Пары' })).not.toBeInTheDocument()
})

it('сохраняет черновик события при ошибке и закрывает форму после успешного повтора', async () => {
  const { store } = setup(true)
  await userEvent.click(screen.getByRole('button', { name: 'Добавить событие' }))
  await userEvent.type(screen.getByLabelText('Название'), 'Важная встреча')
  await userEvent.type(screen.getByLabelText('Описание'), 'Обсудить задачи')
  await userEvent.click(screen.getByRole('button', { name: 'Добавить' }))
  expect(screen.getByRole('dialog', { name: 'Новое событие' })).toBeInTheDocument()
  act(() => { store.dispatch(scheduleFailure('Не удалось сохранить событие')) })
  expect(screen.getByRole('alert')).toHaveTextContent('Не удалось сохранить событие')
  expect(screen.getByLabelText('Название')).toHaveValue('Важная встреча')
  expect(screen.getByLabelText('Описание')).toHaveValue('Обсудить задачи')
  await userEvent.click(screen.getByRole('button', { name: 'Добавить' }))
  act(() => { store.dispatch(createScheduleEventSuccess({ ...scheduleEvent, title: 'Важная встреча' })) })
  expect(screen.queryByRole('dialog', { name: 'Новое событие' })).not.toBeInTheDocument()
})

it('позволяет создать личное событие, даже когда проектов нет', async () => {
  const { dispatch } = setup()
  await userEvent.click(screen.getByRole('button', { name: 'Добавить событие' }))
  expect(screen.getByRole('button', { name: 'Добавить' })).toBeDisabled()
  await userEvent.selectOptions(screen.getByLabelText('Категория'), 'personal')
  expect(screen.queryByLabelText('Проект')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Добавить' })).toBeEnabled()
  await userEvent.type(screen.getByLabelText('Название'), 'Личное событие')
  await userEvent.type(screen.getByLabelText('Описание'), 'Описание')
  await userEvent.click(screen.getByRole('button', { name: 'Добавить' }))
  const action = dispatch.mock.calls.map(([value]) => value).find(createScheduleEventRequest.match)
  expect(action?.payload).toMatchObject({ category: 'personal', title: 'Личное событие' })
  expect(action?.payload).not.toHaveProperty('projectId')
})

it('возвращает выбор проекта при переходе с личной категории на командную', async () => {
  setup(true)
  await userEvent.click(screen.getByRole('button', { name: 'Добавить событие' }))
  await userEvent.selectOptions(screen.getByLabelText('Категория'), 'personal')
  expect(screen.queryByLabelText('Проект')).not.toBeInTheDocument()
  await userEvent.selectOptions(screen.getByLabelText('Категория'), 'team')
  expect(screen.getByLabelText('Проект')).toHaveValue(project.id)
  expect(screen.getByLabelText('Проект')).toBeRequired()
})

it('показывает личное событие в выбранном проекте без подписи проекта', () => {
  const { store } = setup(true)
  act(() => { store.dispatch(loadScheduleSuccess([{ ...scheduleEvent, category: 'personal', projectId: undefined, ownerId: user.id }])) })
  expect(screen.getByRole('heading', { name: scheduleEvent.title, level: 3 })).toBeInTheDocument()
  expect(document.querySelector('.schedule-event__project')).toBeNull()
})

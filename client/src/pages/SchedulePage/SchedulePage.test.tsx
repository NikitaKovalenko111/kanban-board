import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import projects from '../../store/slices/projectsSlice'
import schedule, { createScheduleEventRequest, loadScheduleSuccess } from '../../store/slices/scheduleSlice'
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
  expect(screen.queryByRole('combobox', { name: 'Категория событий' })).not.toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: 'Выбор дня недели' })).toBeVisible()
  await userEvent.click(toggle)
  expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await userEvent.type(screen.getByRole('searchbox', { name: 'Поиск событий' }), 'Встреча')
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Категория событий' }), 'personal')
  expect(screen.getByLabelText('День')).toBeVisible()
  await userEvent.click(toggle)
  expect(screen.queryByRole('searchbox', { name: 'Поиск событий' })).not.toBeInTheDocument()
  expect(screen.getByLabelText('День')).not.toBeVisible()
  await userEvent.click(toggle)
  expect(screen.getByRole('searchbox', { name: 'Поиск событий' })).toHaveValue('Встреча')
  expect(screen.getByRole('combobox', { name: 'Категория событий' })).toHaveValue('personal')
})

it('оставляет поля расписания раскрытыми на десктопе', () => {
  setup(true)
  expect(screen.queryByRole('button', { name: 'Фильтры и настройки' })).not.toBeInTheDocument()
  expect(screen.getByRole('searchbox', { name: 'Поиск событий' })).toBeVisible()
  expect(screen.getByRole('combobox', { name: 'Категория событий' })).toBeVisible()
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

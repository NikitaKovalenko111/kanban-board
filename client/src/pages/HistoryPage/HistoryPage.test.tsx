import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import tasks from '../../store/slices/tasksSlice'
import projects from '../../store/slices/projectsSlice'
import { project, task, user } from '../../test/fixtures'
import WorkspaceNav from '../../components/WorkspaceNav/WorkspaceNav'
import HistoryPage from './HistoryPage'

afterEach(() => vi.unstubAllGlobals())

function setup(mobile: boolean) {
  const media = { matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn() }
  vi.stubGlobal('matchMedia', () => media)
  const store = configureStore({
    reducer: { auth, tasks, projects },
    preloadedState: {
      auth: { user, loading: false, error: null },
      projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
      tasks: { items: [
        { ...task, taskStatus: 'done' as const, completedAt: new Date().toISOString() },
        { ...task, taskId: 2, taskName: 'Задача коллеги', assignee: 'Коллега', taskStatus: 'done' as const, completedAt: new Date().toISOString() },
      ], loading: false, saving: false, error: null },
    },
  })
  render(<Provider store={store}><MemoryRouter initialEntries={['/history']}><WorkspaceNav /><HistoryPage /></MemoryRouter></Provider>)
  return media
}

it('раскрывает мобильные фильтры одной кнопкой и сохраняет выбранные значения', async () => {
  setup(true)
  const toggle = screen.getByRole('button', { name: 'Фильтры и настройки' })
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  await userEvent.click(toggle)
  await userEvent.selectOptions(screen.getByLabelText('Исполнитель'), 'Коллега')
  await userEvent.selectOptions(screen.getByLabelText('Период'), 'week')
  await userEvent.selectOptions(screen.getByLabelText('Сортировка'), 'oldest')
  expect(screen.getByRole('button', { name: /Задача коллеги/ })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: new RegExp(task.taskName) })).not.toBeInTheDocument()
  await userEvent.click(toggle)
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  await userEvent.click(toggle)
  expect(screen.getByLabelText('Исполнитель')).toHaveValue('Коллега')
  expect(screen.getByLabelText('Период')).toHaveValue('week')
  expect(screen.getByLabelText('Сортировка')).toHaveValue('oldest')
})

it('прячет мобильный поиск вместе с фильтрами и сохраняет запрос при сворачивании', async () => {
  const media = setup(true)
  const toggle = screen.getByRole('button', { name: 'Фильтры и настройки' })
  expect(screen.queryByRole('searchbox', { name: 'Поиск по истории' })).not.toBeInTheDocument()
  await userEvent.click(toggle)
  const filters = screen.getByRole('region', { name: 'Фильтры истории' })
  const search = within(filters).getByRole('searchbox', { name: 'Поиск по истории' })
  await userEvent.type(search, 'коллеги')
  expect(screen.getByRole('button', { name: /Задача коллеги/ })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: new RegExp(task.taskName) })).not.toBeInTheDocument()
  await userEvent.click(toggle)
  expect(screen.queryByRole('searchbox', { name: 'Поиск по истории' })).not.toBeInTheDocument()
  await userEvent.click(toggle)
  expect(screen.getByRole('searchbox', { name: 'Поиск по истории' })).toHaveValue('коллеги')
  act(() => { media.matches = false; media.addEventListener.mock.calls.forEach(([, listener]) => listener()) })
  expect(screen.getAllByRole('searchbox', { name: 'Поиск по истории' })).toHaveLength(1)
  expect(screen.getByRole('searchbox', { name: 'Поиск по истории' })).toHaveValue('коллеги')
})

it('показывает фильтры на десктопе без дополнительной кнопки', () => {
  setup(false)
  expect(screen.queryByRole('button', { name: 'Фильтры и настройки' })).not.toBeInTheDocument()
  expect(screen.getAllByRole('combobox')).toHaveLength(3)
  expect(screen.getByRole('searchbox', { name: 'Поиск по истории' })).toBeVisible()
})

it('показывает свёрнутые фильтры при переходе на широкий экран', () => {
  const media = setup(true)
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  act(() => { media.matches = false; media.addEventListener.mock.calls.forEach(([, listener]) => listener()) })
  expect(screen.getAllByRole('combobox')).toHaveLength(3)
})

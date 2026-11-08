import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'
import backendAuth from './store/slices/backendAuthSlice'
import auth, { loginSuccess, logout } from './store/slices/authSlice'
import tasks from './store/slices/tasksSlice'
import projects from './store/slices/projectsSlice'
import schedule from './store/slices/scheduleSlice'
import team from './store/slices/teamSlice'
import { project, task, user } from './test/fixtures'

afterEach(() => vi.unstubAllGlobals())

it('закрывает мобильное меню после выхода и повторного входа', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }))
  const store = configureStore({ reducer: { auth, backendAuth, tasks, projects, schedule, team }, preloadedState: {
    auth: { user, loading: false, error: null },
    projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
    tasks: { items: [task], loading: false, saving: false, error: null },
  } })
  render(<Provider store={store}><MemoryRouter><App /></MemoryRouter></Provider>)
  await userEvent.click(screen.getByRole('button', { name: 'Открыть меню' }))
  expect(document.querySelector('.sidebar')).not.toHaveAttribute('inert')
  act(() => { store.dispatch(logout()) })
  act(() => { store.dispatch(loginSuccess({ ...user, id: 2, role: 'member' })) })
  expect(document.querySelector('.sidebar')).toHaveAttribute('inert')
  expect(document.querySelector('.sidebar')).not.toHaveClass('is-open')
})

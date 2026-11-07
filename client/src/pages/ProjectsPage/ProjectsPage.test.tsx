import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import projects, { createProjectRequest } from '../../store/slices/projectsSlice'
import { project, user } from '../../test/fixtures'
import ProjectsPage from './ProjectsPage'

it('не закрывает форму и показывает ошибку для названия проекта из пробелов', async () => {
  const store = configureStore({ reducer: { auth, projects }, preloadedState: {
    auth: { user, loading: false, error: null },
    projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
  } })
  const dispatch = vi.spyOn(store, 'dispatch')
  render(<Provider store={store}><MemoryRouter><ProjectsPage /></MemoryRouter></Provider>)
  await userEvent.click(screen.getByRole('button', { name: 'Новый проект' }))
  await userEvent.type(screen.getByLabelText('Название'), '   ')
  await userEvent.click(screen.getByRole('button', { name: 'Создать проект' }))
  expect(screen.getByRole('alert')).toHaveTextContent('Укажите название проекта')
  expect(screen.getByRole('dialog')).toBeInTheDocument()
  expect(dispatch.mock.calls.some(([action]) => createProjectRequest.match(action))).toBe(false)
})

import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import projects from '../../store/slices/projectsSlice'
import { project, user } from '../../test/fixtures'
import ProjectsPage from './ProjectsPage'

vi.mock('../../api/config', () => ({ isBackendMode: true, apiBaseUrl: '/api/v1' }))
function setup(access: boolean, sources = false) {
  const store = configureStore({ reducer: { auth, projects }, preloadedState: {
    auth: { user, loading: false, error: null },
    projects: { items: [{ ...project, canViewBoard: access }], activeProjectId: project.id, selections: { projects: project.id }, loading: false, saving: false, error: null },
  } })
  render(<Provider store={store}><MemoryRouter initialEntries={[sources ? '/projects?tab=sources' : '/projects']}><ProjectsPage /></MemoryRouter></Provider>)
  return store
}
it('показывает недоступный проект без действий над его доской и источниками', () => {
  setup(false)
  expect(screen.getByText('Нет доступа. Обратитесь к тимлиду.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Открыть Kanban-доску' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Управление проектом' })).not.toBeInTheDocument()
})
it('не показывает загрузку файлов и ссылок при прямом открытии недоступных источников', () => {
  setup(false, true)
  expect(screen.getByText('Нет доступа к источникам проекта. Обратитесь к тимлиду.')).toBeInTheDocument()
  expect(screen.queryByText('Добавить файлы')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Добавить ссылку' })).not.toBeInTheDocument()
})
it('отправляет HTTP-ссылку проекта из отдельного диалога', async () => {
  const store = setup(true, true)
  const dispatch = vi.spyOn(store, 'dispatch')
  await userEvent.click(screen.getByRole('button', { name: 'Добавить ссылку' }))
  await userEvent.type(screen.getByRole('textbox', { name: 'Название' }), 'Документация')
  await userEvent.type(screen.getByRole('textbox', { name: 'Ссылка' }), 'https://example.com/docs')
  await userEvent.click(within(screen.getByRole('dialog', { name: 'Добавить ссылку' })).getByRole('button', { name: 'Добавить ссылку' }))
  expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'projects/addProjectSourcesRequest', payload: expect.objectContaining({ files: [expect.objectContaining({ name: 'Документация', url: 'https://example.com/docs' })] }) }))
})

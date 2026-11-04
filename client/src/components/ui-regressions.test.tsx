import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockApi } from '../api/mockApi'
import auth from '../store/slices/authSlice'
import tasks from '../store/slices/tasksSlice'
import projects from '../store/slices/projectsSlice'
import schedule from '../store/slices/scheduleSlice'
import team from '../store/slices/teamSlice'
import { task, project, user } from '../test/fixtures'
import Sidebar from './Sidebar/Sidebar'
import WorkspaceNav from './WorkspaceNav/WorkspaceNav'
import AddTaskModal from './AddTaskModal/AddTaskModal'

const makeStore = (member = false) => configureStore({
  reducer: { auth, tasks, projects, schedule, team },
  preloadedState: {
    auth: { user: { ...user, role: member ? 'member' as const : 'lead' as const }, loading: false, error: null },
    projects: { items: [project], activeProjectId: project.id, selections: { board: project.id, schedule: project.id }, loading: false, saving: false, error: null },
    tasks: { items: [{ ...task, comments: [{ id: 'new-comment', author: 'Коллега', text: 'Проверьте файл', createdAt: '2026-11-02T10:00:00' }] }], loading: false, saving: false, error: null },
  },
})
const Location = () => <output data-testid="location">{useLocation().pathname}</output>

describe('регрессии навигации и ролей', () => {
  it('отмечает уведомления прочитанными при открытии без дополнительных кнопок', async () => {
    vi.spyOn(mockApi, 'getSchedule').mockResolvedValue([])
    const store = makeStore()
    render(<Provider store={store}><MemoryRouter><Sidebar open={false} onClose={vi.fn()} /></MemoryRouter></Provider>)
    const button = screen.getByRole('button', { name: /Уведомления/ })
    expect(within(button).getByText('1')).toBeInTheDocument()
    await userEvent.click(button)
    expect(within(button).queryByText('1')).not.toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Уведомления' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Прочитать все' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Прочитано' })).not.toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(`notification-reads-${user.id}`)!)).toContain('new-comment')
    await waitFor(() => expect(mockApi.getSchedule).toHaveBeenCalled())
  })

  it('открывает собственный профиль нажатием на имя и аватар', async () => {
    vi.spyOn(mockApi, 'getSchedule').mockResolvedValue([])
    render(<Provider store={makeStore()}><MemoryRouter><Sidebar open={false} onClose={vi.fn()} /><Location /></MemoryRouter></Provider>)
    await userEvent.click(screen.getByRole('link', { name: new RegExp(user.name) }))
    expect(screen.getByTestId('location')).toHaveTextContent('/profile')
  })

  it('выбор всех проектов не уводит с расписания и не меняет проект доски', async () => {
    const store = makeStore()
    render(<Provider store={store}><MemoryRouter initialEntries={['/schedule']}><WorkspaceNav /><Location /></MemoryRouter></Provider>)
    await userEvent.click(screen.getByRole('button', { name: project.name }))
    await userEvent.click(screen.getByRole('option', { name: 'Все проекты' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/schedule')
    expect(store.getState().projects.selections).toMatchObject({ board: project.id, schedule: 'all' })
  })

  it('не показывает выбор проекта в профиле', () => {
    render(<Provider store={makeStore()}><MemoryRouter initialEntries={['/profile']}><WorkspaceNav /></MemoryRouter></Provider>)
    expect(screen.queryByRole('button', { name: project.name })).not.toBeInTheDocument()
  })

  it('блокирует взятие чужой задачи участником', () => {
    render(<Provider store={makeStore(true)}><AddTaskModal task={{ ...task, taskId: 999, assignee: 'Другой участник' }} onClose={vi.fn()} /></Provider>)
    expect(screen.getByRole('button', { name: 'Назначена другому участнику' })).toBeDisabled()
    expect(screen.queryByLabelText('Исполнитель')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сохранить исполнителя' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отправить на проверку' })).not.toBeInTheDocument()
  })

  it('капитан явно переназначает чужую ожидающую задачу на себя', async () => {
    const store = makeStore()
    const dispatch = vi.spyOn(store, 'dispatch')
    render(<Provider store={store}><AddTaskModal task={{ ...task, taskId: 999, assignee: 'Другой участник' }} onClose={vi.fn()} /></Provider>)
    await userEvent.click(screen.getByRole('button', { name: 'Переназначить на себя и взять в работу' }))
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'tasks/updateTaskStatusRequest', payload: expect.objectContaining({ taskId: 999, status: 'working', assignee: user.name }) }))
  })

  it('капитан не получает форму сдачи чужой задачи в работе', () => {
    render(<Provider store={makeStore()}><AddTaskModal task={{ ...task, taskId: 999, taskStatus: 'working', assignee: 'Другой участник' }} onClose={vi.fn()} /></Provider>)
    expect(screen.getByText('Задача назначена другому участнику')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Прикрепить результат/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отправить на проверку' })).not.toBeInTheDocument()
  })
})

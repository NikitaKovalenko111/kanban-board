import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { render, screen, waitFor } from '@testing-library/react'
import { act } from 'react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockApi } from '../api/mockApi'
import auth from '../store/slices/authSlice'
import tasks, { assignTaskRequest, createTaskRequest, updateTaskStatusRequest } from '../store/slices/tasksSlice'
import projects from '../store/slices/projectsSlice'
import team from '../store/slices/teamSlice'
import { loadTeamSuccess } from '../store/slices/teamSlice'
import { project, task, user } from '../test/fixtures'
import type { Task as TaskType } from '../types'
import BoardElement from './BoardElement/BoardElement'
import AddTaskModal from './AddTaskModal/AddTaskModal'

function setupStore() {
  return configureStore({
    reducer: { auth, tasks, projects, team },
    preloadedState: {
      auth: { user, loading: false, error: null },
      team: { settings: { name: '', description: '' }, members: [{ ...user, active: true, joinedAt: '2026-01-01' }, { ...user, id: 2, name: 'Коллега', login: 'colleague', active: true, joinedAt: '2026-01-01' }], loading: false, saving: false, error: null },
      projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
    },
  })
}
function renderModal(currentTask: TaskType | null = null) {
  const store = setupStore()
  const dispatch = vi.spyOn(store, 'dispatch')
  render(<Provider store={store}><AddTaskModal task={currentTask} onClose={vi.fn()} /></Provider>)
  act(() => { store.dispatch(loadTeamSuccess({ settings: store.getState().team.settings, members: store.getState().team.members })) })
  return dispatch
}

describe('основные компоненты доски', () => {
  it('не создаёт задачу с названием из пробелов', async () => {
    const dispatch = renderModal()
    await userEvent.type(screen.getByLabelText('Название задачи'), '   ')
    await userEvent.type(screen.getByLabelText('Описание / ТЗ'), 'Описание')
    await userEvent.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Укажите название и описание задачи')
    expect(dispatch.mock.calls.some(([action]) => createTaskRequest.match(action))).toBe(false)
  })
  it.each(['working', 'checking', 'done'] as const)('показывает приоритет без редактирования в статусе %s', (taskStatus) => {
    renderModal({ ...task, taskStatus, priority: 'high' })
    expect(screen.queryByRole('combobox', { name: 'Приоритет' })).not.toBeInTheDocument()
    expect(screen.getByText('Высокий')).toBeInTheDocument()
  })
  it('позволяет капитану менять приоритет ожидающей задачи', async () => {
    const dispatch = renderModal({ ...task, taskStatus: 'waiting' })
    await userEvent.click(screen.getByRole('combobox', { name: 'Приоритет' }))
    await userEvent.click(screen.getByRole('option', { name: 'Высокий' }))
    expect(dispatch).toHaveBeenCalledWith({ type: 'tasks/updateTaskPriorityRequest', payload: { taskId: task.taskId, priority: 'high' } })
  })
  it('передаёт выбранного исполнителя при создании задачи', async () => {
    const dispatch = renderModal()
    await userEvent.type(screen.getByLabelText('Название задачи'), 'Задача')
    await userEvent.type(screen.getByLabelText('Описание / ТЗ'), 'ТЗ')
    await userEvent.selectOptions(screen.getByLabelText('Исполнитель'), '2')
    await userEvent.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(dispatch).toHaveBeenCalledWith(createTaskRequest(expect.objectContaining({ assigneeId: 2 })))
  })

  it('капитан меняет исполнителя существующей задачи', async () => {
    const dispatch = renderModal({ ...task, assigneeId: user.id })
    expect(screen.getByRole('button', { name: 'Сохранить исполнителя' })).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Исполнитель'), '2')
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить исполнителя' }))
    expect(dispatch).toHaveBeenCalledWith(assignTaskRequest({ taskId: task.taskId, assigneeId: 2 }))
  })
  it('ищет комментарии и восстанавливает весь список после очистки', async () => {
    renderModal({ ...task, comments: [
      { id: 'a', author: 'Коллега', text: 'Проверьте отступы', createdAt: '2026-11-02T10:00:00' },
      { id: 'b', author: 'Коллега', text: 'Обновите цвета', createdAt: '2026-11-02T11:00:00' },
    ] })
    await userEvent.type(screen.getByRole('searchbox', { name: 'Поиск комментариев' }), 'цвета')
    expect(screen.queryByText('Проверьте отступы')).not.toBeInTheDocument()
    expect(screen.getByText('Обновите цвета')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Очистить поиск' }))
    expect(screen.getByText('Проверьте отступы')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Общий разбор' })).toHaveAttribute('tabindex', '0')
  })
  it('показывает пустую колонку и открывает историю задач', async () => {
    const onHistory = vi.fn()
    render(<BoardElement name="Выполнено" accent="#123456" tasks={[]} onTaskClick={vi.fn()} onHistory={onHistory} />)
    expect(screen.getByText('Здесь пока пусто')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Вся история/ }))
    expect(onHistory).toHaveBeenCalledOnce()
  })

  it('показывает карточку с проектом и передаёт выбранную задачу', async () => {
    const onTaskClick = vi.fn()
    render(<Provider store={setupStore()}><BoardElement name="Ожидание" accent="#123456" tasks={[task]} onTaskClick={onTaskClick} showProject /></Provider>)
    expect(screen.getByText(project.name)).toBeInTheDocument()
    expect(screen.getByText(task.taskDesc)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Подготовить макет/ }))
    expect(onTaskClick).toHaveBeenCalledWith(task)
  })

  it('отправляет заполненную форму создания задачи в store', async () => {
    const dispatch = renderModal()
    const actor = userEvent.setup()
    await actor.type(screen.getByLabelText('Название задачи'), 'Новая задача')
    await actor.type(screen.getByLabelText('Описание / ТЗ'), 'Описание')
    await actor.click(screen.getByRole('combobox', { name: 'Приоритет' }))
    await actor.click(screen.getByRole('option', { name: 'Высокий' }))
    await actor.click(screen.getByRole('button', { name: 'Добавить ссылку' }))
    await actor.type(screen.getByLabelText('Ссылка'), 'https://example.com/spec')
    await actor.type(screen.getByLabelText('Название ссылки'), 'ТЗ по ссылке')
    await actor.click(screen.getByRole('button', { name: 'Прикрепить ссылку' }))
    await actor.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(dispatch).toHaveBeenCalledWith(createTaskRequest({ projectId: project.id, taskName: 'Новая задача', taskDesc: 'Описание', priority: 'high', taskDeadline: undefined, attachments: [expect.objectContaining({ url: 'https://example.com/spec', name: 'ТЗ по ссылке' })] }))
  })

  it('разрешает отправку на проверку после прикрепления результата', async () => {
    vi.spyOn(mockApi, 'uploadAttachment').mockResolvedValue({ id: 'uploaded-result', name: 'result.txt', size: 6 })
    const dispatch = renderModal({ ...task, taskStatus: 'working' })
    const submit = screen.getByRole('button', { name: 'Отправить на проверку' })
    expect(submit).toBeDisabled()
    const actor = userEvent.setup()
    await actor.upload(screen.getByLabelText(/Прикрепить результат/), new File(['result'], 'result.txt', { type: 'text/plain' }))
    await waitFor(() => expect(submit).toBeEnabled())
    await actor.click(submit)
    expect(dispatch).toHaveBeenCalledWith(updateTaskStatusRequest({ taskId: task.taskId, status: 'checking', author: user.name, resultAttachments: [expect.objectContaining({ name: 'result.txt', size: 6 })] }))
  })
})

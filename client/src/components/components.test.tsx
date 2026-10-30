import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import auth from '../store/slices/authSlice'
import tasks, { createTaskRequest, updateTaskStatusRequest } from '../store/slices/tasksSlice'
import projects from '../store/slices/projectsSlice'
import { project, task, user } from '../test/fixtures'
import type { Task as TaskType } from '../types'
import BoardElement from './BoardElement/BoardElement'
import AddTaskModal from './AddTaskModal/AddTaskModal'

function setupStore() {
  return configureStore({
    reducer: { auth, tasks, projects },
    preloadedState: {
      auth: { user, loading: false, error: null },
      projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
    },
  })
}
function renderModal(currentTask: TaskType | null = null) {
  const store = setupStore()
  const dispatch = vi.spyOn(store, 'dispatch')
  render(<Provider store={store}><AddTaskModal task={currentTask} onClose={vi.fn()} /></Provider>)
  return dispatch
}

describe('основные компоненты доски', () => {
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
    await actor.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(dispatch).toHaveBeenCalledWith(createTaskRequest({ projectId: project.id, taskName: 'Новая задача', taskDesc: 'Описание', taskDeadline: undefined, attachments: [] }))
  })

  it('разрешает отправку на проверку после прикрепления результата', async () => {
    const dispatch = renderModal({ ...task, taskStatus: 'working' })
    const submit = screen.getByRole('button', { name: 'Отправить на проверку' })
    expect(submit).toBeDisabled()
    const actor = userEvent.setup()
    await actor.upload(screen.getByLabelText(/Прикрепить результат/), new File(['result'], 'result.txt', { type: 'text/plain' }))
    expect(submit).toBeEnabled()
    await actor.click(submit)
    expect(dispatch).toHaveBeenCalledWith(updateTaskStatusRequest({ taskId: task.taskId, status: 'checking', author: user.name, resultAttachments: [expect.objectContaining({ name: 'result.txt', size: 6 })] }))
  })
})

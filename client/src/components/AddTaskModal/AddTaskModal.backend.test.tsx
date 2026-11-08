import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import tasks, { assignTaskRequest } from '../../store/slices/tasksSlice'
import projects from '../../store/slices/projectsSlice'
import team, { loadTeamSuccess } from '../../store/slices/teamSlice'
import { project, task, user } from '../../test/fixtures'
import AddTaskModal from './AddTaskModal'

vi.mock('../../api/config', async importOriginal => ({ ...await importOriginal<typeof import('../../api/config')>(), isBackendMode: true }))

const captainId = '89aa283b-e289-4584-af55-10d331cb979f'
const memberId = 'a0c95905-d70c-47b9-a8e7-89f5c47a83b0'

function setup() {
  const captain = { ...user, id: captainId }
  const members = [captain, { ...captain, id: memberId, name: 'Коллега', login: 'member' }].map(member => ({ ...member, active: true, joinedAt: '2026-01-01' }))
  const store = configureStore({
    reducer: { auth, tasks, projects, team },
    preloadedState: {
      auth: { user: captain, loading: false, error: null },
      projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
      team: { settings: { name: '', description: '' }, members, loading: false, saving: false, error: null },
    },
  })
  const dispatch = vi.spyOn(store, 'dispatch')
  render(<Provider store={store}><AddTaskModal task={{ ...task, assigneeId: captainId }} onClose={vi.fn()} /></Provider>)
  act(() => { store.dispatch(loadTeamSuccess({ settings: store.getState().team.settings, members })) })
  return dispatch
}

describe('назначение исполнителя через backend', () => {
  it('сохраняет строковый UUID при смене исполнителя существующей задачи', async () => {
    const dispatch = setup()
    await userEvent.selectOptions(screen.getByLabelText('Исполнитель'), memberId)
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить исполнителя' }))
    expect(dispatch).toHaveBeenCalledWith(assignTaskRequest({ taskId: task.taskId, assigneeId: memberId }))
  })

  it('отправляет null при снятии исполнителя с ожидающей задачи', async () => {
    const dispatch = setup()
    await userEvent.selectOptions(screen.getByLabelText('Исполнитель'), '')
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить исполнителя' }))
    expect(dispatch).toHaveBeenCalledWith(assignTaskRequest({ taskId: task.taskId, assigneeId: null }))
  })
})

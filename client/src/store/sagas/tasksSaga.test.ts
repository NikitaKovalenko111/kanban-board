import { configureStore } from '@reduxjs/toolkit'
import createSagaMiddleware from 'redux-saga'
import { describe, expect, it, vi } from 'vitest'
import { mockApi } from '../../api/mockApi'
import { task } from '../../test/fixtures'
import reducer, { loadTasksRequest, createTaskRequest, updateTaskStatusRequest } from '../slices/tasksSlice'
import { tasksSaga } from './tasksSaga'

function setup() {
  const middleware = createSagaMiddleware()
  const store = configureStore({ reducer, middleware: getDefault => getDefault({ thunk: false }).concat(middleware) })
  const saga = middleware.run(tasksSaga)
  return { store, saga }
}

describe('tasksSaga', () => {
  it('передаёт ответы API в состояние при загрузке, создании и смене статуса', async () => {
    vi.spyOn(mockApi, 'getTasks').mockResolvedValue([task])
    const create = vi.spyOn(mockApi, 'createTask').mockResolvedValue({ ...task, taskId: 2 })
    const updated = { ...task, taskStatus: 'checking' as const }
    const update = vi.spyOn(mockApi, 'updateTaskStatus').mockResolvedValue(updated)
    const { store, saga } = setup()
    try {
      store.dispatch(loadTasksRequest())
      await vi.waitFor(() => expect(store.getState().items).toEqual([task]))
      store.dispatch(createTaskRequest(task))
      await vi.waitFor(() => expect(store.getState().items).toHaveLength(2))
      expect(create).toHaveBeenCalledWith(task)
      const files = [{ id: 'r', name: 'result.zip', size: 10 }]
      store.dispatch(updateTaskStatusRequest({ taskId: task.taskId, status: 'checking', author: 'Автор', resultAttachments: files }))
      await vi.waitFor(() => expect(store.getState().items[1]).toEqual(updated))
      expect(update).toHaveBeenCalledWith(task.taskId, 'checking', 'Автор', undefined, files)
      expect(store.getState().saving).toBe(false)
    } finally { saga.cancel() }
  })

  it('сохраняет ошибку API и завершает загрузку', async () => {
    vi.spyOn(mockApi, 'getTasks').mockRejectedValue(new Error('Нет соединения'))
    const { store, saga } = setup()
    try {
      store.dispatch(loadTasksRequest())
      await vi.waitFor(() => expect(store.getState()).toMatchObject({ error: 'Нет соединения', loading: false, saving: false }))
    } finally { saga.cancel() }
  })
})

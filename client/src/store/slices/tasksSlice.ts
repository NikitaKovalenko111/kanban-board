import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { NewTask, Task, TaskStatusUpdate } from '../../types'

interface TasksState { items: Task[]; loading: boolean; saving: boolean; error: string | null }
const initialState: TasksState = { items: [], loading: false, saving: false, error: null }

const tasksSlice = createSlice({
  name: 'tasks', initialState,
  reducers: {
    loadTasksRequest(state) { state.loading = true; state.error = null },
    loadTasksSuccess(state, action: PayloadAction<Task[]>) { state.items = action.payload; state.loading = false },
    createTaskRequest(state, _action: PayloadAction<NewTask>) { state.saving = true; state.error = null },
    createTaskSuccess(state, action: PayloadAction<Task>) { state.items.unshift(action.payload); state.saving = false },
    updateTaskStatusRequest(state, _action: PayloadAction<TaskStatusUpdate>) { state.saving = true },
    updateTaskSuccess(state, action: PayloadAction<Task>) {
      const index = state.items.findIndex((task) => task.taskId === action.payload.taskId)
      if (index >= 0) state.items[index] = action.payload
      state.saving = false
    },
    tasksFailure(state, action: PayloadAction<string>) { state.loading = false; state.saving = false; state.error = action.payload },
  },
})

export const { loadTasksRequest, loadTasksSuccess, createTaskRequest, createTaskSuccess, updateTaskStatusRequest, updateTaskSuccess, tasksFailure } = tasksSlice.actions
export default tasksSlice.reducer

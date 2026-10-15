import { call, put, takeLatest } from 'redux-saga/effects'
import type { PayloadAction } from '@reduxjs/toolkit'
import { mockApi } from '../../api/mockApi'
import type { NewTask, Task, TaskStatusUpdate } from '../../types'
import { createTaskRequest, createTaskSuccess, loadTasksRequest, loadTasksSuccess, tasksFailure, updateTaskStatusRequest, updateTaskSuccess } from '../slices/tasksSlice'

function* loadTasksWorker() {
  try { const tasks: Task[] = yield call(mockApi.getTasks); yield put(loadTasksSuccess(tasks)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось загрузить задачи')) }
}
function* createTaskWorker(action: PayloadAction<NewTask>) {
  try { const task: Task = yield call(mockApi.createTask, action.payload); yield put(createTaskSuccess(task)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось создать задачу')) }
}
function* updateTaskWorker(action: PayloadAction<TaskStatusUpdate>) {
  try { const { taskId, status, author, revisionComment, resultAttachments } = action.payload; const task: Task = yield call(mockApi.updateTaskStatus, taskId, status, author, revisionComment, resultAttachments); yield put(updateTaskSuccess(task)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось обновить задачу')) }
}
export function* tasksSaga() {
  yield takeLatest(loadTasksRequest, loadTasksWorker)
  yield takeLatest(createTaskRequest, createTaskWorker)
  yield takeLatest(updateTaskStatusRequest, updateTaskWorker)
}

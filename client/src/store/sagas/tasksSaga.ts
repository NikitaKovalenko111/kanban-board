import { call, put, takeLatest } from 'redux-saga/effects'
import type { PayloadAction } from '@reduxjs/toolkit'
import { mockApi } from '../../api/mockApi'
import type { AddTaskCommentPayload, NewTask, Task, TaskStatusUpdate } from '../../types'
import { addTaskCommentRequest, createTaskRequest, createTaskSuccess, loadTasksRequest, loadTasksSuccess, tasksFailure, updateTaskStatusRequest, updateTaskSuccess } from '../slices/tasksSlice'

function* loadTasksWorker() {
  try { const tasks: Task[] = yield call(mockApi.getTasks); yield put(loadTasksSuccess(tasks)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось загрузить задачи')) }
}
function* createTaskWorker(action: PayloadAction<NewTask>) {
  try { const task: Task = yield call(mockApi.createTask, action.payload); yield put(createTaskSuccess(task)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось создать задачу')) }
}
function* updateTaskWorker(action: PayloadAction<TaskStatusUpdate>) {
  try { const { taskId, status, author, revisionComment, resultAttachments, assignee } = action.payload; const task: Task = yield call(mockApi.updateTaskStatus, taskId, status, author, revisionComment, resultAttachments, assignee); yield put(updateTaskSuccess(task)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось обновить задачу')) }
}
function* addCommentWorker(action: PayloadAction<AddTaskCommentPayload>) {
  try { const task: Task = yield call(mockApi.addTaskComment, action.payload); yield put(updateTaskSuccess(task)) }
  catch (error) { yield put(tasksFailure(error instanceof Error ? error.message : 'Не удалось сохранить комментарий')) }
}
export function* tasksSaga() {
  yield takeLatest(loadTasksRequest, loadTasksWorker)
  yield takeLatest(createTaskRequest, createTaskWorker)
  yield takeLatest(updateTaskStatusRequest, updateTaskWorker)
  yield takeLatest(addTaskCommentRequest, addCommentWorker)
}

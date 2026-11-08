import { isBackendMode } from '../../api/config'
import { scheduleSourceMessage } from '../../api/realApi'
import type { EntityId, ScheduleQuery } from '../../types'
import type { PayloadAction } from '@reduxjs/toolkit'
import { call, put, takeLatest } from 'redux-saga/effects'
import { api } from '../../api'
import type { EventReminders, NewScheduleEvent, ScheduleEvent } from '../../types'
import { createScheduleEventRequest, createScheduleEventSuccess, loadScheduleRequest, loadScheduleSuccess, updateEventRemindersRequest, updateScheduleEventSuccess, scheduleSourceInfo, scheduleFailure } from '../slices/scheduleSlice'

function* loadScheduleWorker(action: PayloadAction<string | ScheduleQuery>) {
  try { const events: ScheduleEvent[] = yield call(api.getSchedule, typeof action.payload === 'string' ? action.payload : action.payload.projectId, typeof action.payload === 'string' ? undefined : action.payload); yield put(loadScheduleSuccess(events)); yield put(scheduleSourceInfo(isBackendMode ? scheduleSourceMessage : '')) }
  catch (error) { yield put(scheduleFailure(error instanceof Error ? error.message : 'Не удалось загрузить расписание')) }
}
function* createEventWorker(action: PayloadAction<NewScheduleEvent>) {
  try { const event: ScheduleEvent = yield call(api.createScheduleEvent, action.payload); yield put(createScheduleEventSuccess(event)) }
  catch (error) { yield put(scheduleFailure(error instanceof Error ? error.message : 'Не удалось создать событие')) }
}
function* updateRemindersWorker(action: PayloadAction<EventReminders & { id: EntityId }>) {
  try { const { id, ...reminders } = action.payload; const event: ScheduleEvent = yield call(api.updateEventReminders, id, reminders); yield put(updateScheduleEventSuccess(event)) }
  catch (error) { yield put(scheduleFailure(error instanceof Error ? error.message : 'Не удалось сохранить напоминания')) }
}
export function* scheduleSaga() {
  yield takeLatest(loadScheduleRequest, loadScheduleWorker)
  yield takeLatest(createScheduleEventRequest, createEventWorker)
  yield takeLatest(updateEventRemindersRequest, updateRemindersWorker)
}

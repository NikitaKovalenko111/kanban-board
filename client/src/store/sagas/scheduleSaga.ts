import type { PayloadAction } from '@reduxjs/toolkit'
import { call, put, takeLatest } from 'redux-saga/effects'
import { mockApi } from '../../api/mockApi'
import type { NewScheduleEvent, ScheduleEvent } from '../../types'
import { createScheduleEventRequest, createScheduleEventSuccess, loadScheduleRequest, loadScheduleSuccess, scheduleFailure } from '../slices/scheduleSlice'

function* loadScheduleWorker(action: PayloadAction<string>) {
  try { const events: ScheduleEvent[] = yield call(mockApi.getSchedule, action.payload); yield put(loadScheduleSuccess(events)) }
  catch (error) { yield put(scheduleFailure(error instanceof Error ? error.message : 'Не удалось загрузить расписание')) }
}
function* createEventWorker(action: PayloadAction<NewScheduleEvent>) {
  try { const event: ScheduleEvent = yield call(mockApi.createScheduleEvent, action.payload); yield put(createScheduleEventSuccess(event)) }
  catch (error) { yield put(scheduleFailure(error instanceof Error ? error.message : 'Не удалось создать событие')) }
}
export function* scheduleSaga() {
  yield takeLatest(loadScheduleRequest, loadScheduleWorker)
  yield takeLatest(createScheduleEventRequest, createEventWorker)
}

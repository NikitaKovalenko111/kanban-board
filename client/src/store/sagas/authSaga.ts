import type { PayloadAction } from '@reduxjs/toolkit'
import { call, put, takeLatest } from 'redux-saga/effects'
import { mockApi } from '../../api/mockApi'
import type { LoginCredentials, User } from '../../types'
import { loginFailure, loginRequest, loginSuccess } from '../slices/authSlice'

function* loginWorker(action: PayloadAction<LoginCredentials>) {
  try { const user: User = yield call(mockApi.login, action.payload); yield put(loginSuccess(user)) }
  catch (error) { yield put(loginFailure(error instanceof Error ? error.message : 'Не удалось войти')) }
}

export function* authSaga() { yield takeLatest(loginRequest, loginWorker) }

import type { PayloadAction } from '@reduxjs/toolkit'
import { call, put, takeLeading } from 'redux-saga/effects'
import { api } from '../../api'
import { isBackendMode } from '../../api/config'
import { backendApi } from '../../api/backendApi'
import type { BackendIdentity } from '../../api/backendSession'
import { backendLogoutRequest, backendSessionAuthenticated, backendSessionCleared, restoreBackendSessionRequest } from '../slices/backendAuthSlice'
import type { LoginCredentials, User } from '../../types'
import { loginFailure, loginRequest, loginSuccess, loginFinished } from '../slices/authSlice'

function* loginWorker(action: PayloadAction<LoginCredentials>) {
  try {
    if (isBackendMode) {
      const identity: BackendIdentity = yield call(backendApi.login, action.payload)
      yield put(backendSessionAuthenticated(identity))
      yield put(loginFinished())
    } else { const user: User = yield call(api.login, action.payload); yield put(loginSuccess(user)) }
  }
  catch (error) { yield put(loginFailure(error instanceof Error ? error.message : 'Не удалось войти')) }
}

function* restoreWorker() {
  if (!isBackendMode) { yield put(backendSessionCleared()); return }
  try {
    const identity: BackendIdentity | null = yield call(backendApi.restore)
    yield put(identity ? backendSessionAuthenticated(identity) : backendSessionCleared())
  } catch (error) { yield put(backendSessionCleared()); yield put(loginFailure(error instanceof Error ? error.message : 'Не удалось восстановить сессию')) }
}
function* logoutWorker() {
  try { if (isBackendMode) yield call(backendApi.logout); yield put(backendSessionCleared()) }
  catch (error) { yield put(backendSessionCleared()); yield put(loginFailure(error instanceof Error ? error.message : 'Не удалось завершить серверную сессию')) }
}
export function* authSaga() {
  yield takeLeading(loginRequest, loginWorker)
  yield takeLeading(restoreBackendSessionRequest, restoreWorker)
  yield takeLeading(backendLogoutRequest, logoutWorker)
}

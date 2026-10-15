import type { PayloadAction } from '@reduxjs/toolkit'
import { call, put, takeLatest } from 'redux-saga/effects'
import { mockApi } from '../../api/mockApi'
import type { NewTeamMember, TeamMember, TeamSettings } from '../../types'
import { createMemberRequest, createMemberSuccess, loadTeamRequest, loadTeamSuccess, teamFailure, toggleMemberRequest, updateMemberSuccess, updateTeamRequest, updateTeamSuccess } from '../slices/teamSlice'

function* loadTeamWorker() {
  try { const team: { settings: TeamSettings; members: TeamMember[] } = yield call(mockApi.getTeam); yield put(loadTeamSuccess(team)) }
  catch (error) { yield put(teamFailure(error instanceof Error ? error.message : 'Не удалось загрузить команду')) }
}
function* updateTeamWorker(action: PayloadAction<TeamSettings>) {
  try { const settings: TeamSettings = yield call(mockApi.updateTeam, action.payload); yield put(updateTeamSuccess(settings)) }
  catch (error) { yield put(teamFailure(error instanceof Error ? error.message : 'Не удалось сохранить настройки')) }
}
function* createMemberWorker(action: PayloadAction<NewTeamMember>) {
  try { const member: TeamMember = yield call(mockApi.createMember, action.payload); yield put(createMemberSuccess(member)) }
  catch (error) { yield put(teamFailure(error instanceof Error ? error.message : 'Не удалось создать аккаунт')) }
}
function* toggleMemberWorker(action: PayloadAction<{ id: number; active: boolean }>) {
  try { const member: TeamMember = yield call(mockApi.toggleMember, action.payload.id, action.payload.active); yield put(updateMemberSuccess(member)) }
  catch (error) { yield put(teamFailure(error instanceof Error ? error.message : 'Не удалось изменить доступ')) }
}
export function* teamSaga() {
  yield takeLatest(loadTeamRequest, loadTeamWorker)
  yield takeLatest(updateTeamRequest, updateTeamWorker)
  yield takeLatest(createMemberRequest, createMemberWorker)
  yield takeLatest(toggleMemberRequest, toggleMemberWorker)
}

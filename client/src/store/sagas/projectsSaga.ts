import { call, put, takeLatest } from 'redux-saga/effects'
import { select } from 'redux-saga/effects'
import { loginSuccess } from '../slices/authSlice'
import { loadTeamRequest } from '../slices/teamSlice'
import { updateProjectRequest, updateProjectSuccess, deleteProjectSourceRequest } from '../slices/projectsSlice'
import type { TeamMember, User } from '../../types'
import { api } from '../../api'
import { isBackendMode } from '../../api/config'
import { realApi } from '../../api/realApi'
import type { AddProjectSourcesPayload, NewProject, Project } from '../../types'
import { addProjectSourcesRequest, addProjectSourcesSuccess, createProjectRequest, createProjectSuccess, loadProjectsRequest, loadProjectsSuccess, projectsFailure } from '../slices/projectsSlice'

function* loadProjectsWorker() {
  try { const projects: Project[] = yield call(api.getProjects); yield put(loadProjectsSuccess(projects)) }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось загрузить проекты')) }
}

function* createProjectWorker(action: ReturnType<typeof createProjectRequest>) {
  try {
    const project: Project = yield call(api.createProject, action.payload as NewProject)
    yield put(createProjectSuccess(project))
    if (isBackendMode) {
      yield put(loadTeamRequest())
      // Project membership is returned by the server; do not invent a local role.
      try { const user: User = yield call(realApi.getCurrentUser); yield put(loginSuccess(user)) }
      catch { /* A profile refresh failure must not turn a created project into a failed creation. */ }
    }
  }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось создать проект')) }
}

function* addProjectSourcesWorker(action: ReturnType<typeof addProjectSourcesRequest>) {
  try { const project: Project = yield call(api.addProjectSources, action.payload as AddProjectSourcesPayload); yield put(addProjectSourcesSuccess(project)) }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось добавить файлы')) }
}

export function* projectsSaga() {
  yield takeLatest(deleteProjectSourceRequest, function* (action: ReturnType<typeof deleteProjectSourceRequest>) {
    try {
      const project: Project = yield call(api.deleteProjectSource, action.payload.projectId, action.payload.sourceId)
      yield put(addProjectSourcesSuccess(project))
    } catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось удалить источник')) }
  })
  yield takeLatest(updateProjectRequest, function* (action: ReturnType<typeof updateProjectRequest>) {
    try {
      const result: { project: Project; members: TeamMember[] } = yield call(api.updateProject, action.payload)
      yield put(updateProjectSuccess(result.project))
      yield put(loadTeamRequest())
      const currentUser: User = yield select((state) => state.auth.user)
      const updatedUser = result.members.find((member) => member.id === currentUser.id)
      if (updatedUser) yield put(loginSuccess(updatedUser))
    } catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось сохранить проект')) }
  })
  yield takeLatest(loadProjectsRequest, loadProjectsWorker)
  yield takeLatest(createProjectRequest, createProjectWorker)
  yield takeLatest(addProjectSourcesRequest, addProjectSourcesWorker)
}

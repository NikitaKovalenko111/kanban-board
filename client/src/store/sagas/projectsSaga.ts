import { call, put, takeLatest } from 'redux-saga/effects'
import { mockApi } from '../../api/mockApi'
import type { AddProjectSourcesPayload, NewProject, Project } from '../../types'
import { addProjectSourcesRequest, addProjectSourcesSuccess, createProjectRequest, createProjectSuccess, loadProjectsRequest, loadProjectsSuccess, projectsFailure } from '../slices/projectsSlice'

function* loadProjectsWorker() {
  try { const projects: Project[] = yield call(mockApi.getProjects); yield put(loadProjectsSuccess(projects)) }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось загрузить проекты')) }
}

function* createProjectWorker(action: ReturnType<typeof createProjectRequest>) {
  try { const project: Project = yield call(mockApi.createProject, action.payload as NewProject); yield put(createProjectSuccess(project)) }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось создать проект')) }
}

function* addProjectSourcesWorker(action: ReturnType<typeof addProjectSourcesRequest>) {
  try { const project: Project = yield call(mockApi.addProjectSources, action.payload as AddProjectSourcesPayload); yield put(addProjectSourcesSuccess(project)) }
  catch (error) { yield put(projectsFailure(error instanceof Error ? error.message : 'Не удалось добавить файлы')) }
}

export function* projectsSaga() {
  yield takeLatest(loadProjectsRequest, loadProjectsWorker)
  yield takeLatest(createProjectRequest, createProjectWorker)
  yield takeLatest(addProjectSourcesRequest, addProjectSourcesWorker)
}

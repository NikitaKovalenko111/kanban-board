import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AddProjectSourcesPayload, NewProject, Project } from '../../types'

interface ProjectsState { items: Project[]; activeProjectId: string; loading: boolean; saving: boolean; error: string | null }
const initialState: ProjectsState = { items: [], activeProjectId: localStorage.getItem('active-project') ?? 'ai-hackathon', loading: false, saving: false, error: null }

const projectsSlice = createSlice({
  name: 'projects', initialState,
  reducers: {
    loadProjectsRequest(state) { state.loading = true; state.error = null },
    loadProjectsSuccess(state, action: PayloadAction<Project[]>) {
      state.items = action.payload
      if (state.activeProjectId !== 'all' && !action.payload.some((project) => project.id === state.activeProjectId)) state.activeProjectId = action.payload[0]?.id ?? ''
      state.loading = false
    },
    createProjectRequest(state, _action: PayloadAction<NewProject>) { state.saving = true; state.error = null },
    createProjectSuccess(state, action: PayloadAction<Project>) {
      state.items.push(action.payload)
      state.activeProjectId = action.payload.id
      state.saving = false
      localStorage.setItem('active-project', action.payload.id)
    },
    addProjectSourcesRequest(state, _action: PayloadAction<AddProjectSourcesPayload>) { state.saving = true; state.error = null },
    addProjectSourcesSuccess(state, action: PayloadAction<Project>) {
      state.items = state.items.map((project) => project.id === action.payload.id ? action.payload : project)
      state.saving = false
    },
    setActiveProject(state, action: PayloadAction<string>) { state.activeProjectId = action.payload; localStorage.setItem('active-project', action.payload) },
    projectsFailure(state, action: PayloadAction<string>) { state.loading = false; state.saving = false; state.error = action.payload },
  },
})

export const { loadProjectsRequest, loadProjectsSuccess, createProjectRequest, createProjectSuccess, addProjectSourcesRequest, addProjectSourcesSuccess, setActiveProject, projectsFailure } = projectsSlice.actions
export default projectsSlice.reducer

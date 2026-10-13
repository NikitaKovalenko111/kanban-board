import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { NewTeamMember, TeamMember, TeamSettings } from '../../types'

interface TeamState { settings: TeamSettings; members: TeamMember[]; loading: boolean; saving: boolean; error: string | null }
const initialState: TeamState = { settings: { name: '', description: '' }, members: [], loading: false, saving: false, error: null }

const teamSlice = createSlice({
  name: 'team', initialState,
  reducers: {
    loadTeamRequest(state) { state.loading = true; state.error = null },
    loadTeamSuccess(state, action: PayloadAction<{ settings: TeamSettings; members: TeamMember[] }>) { state.settings = action.payload.settings; state.members = action.payload.members; state.loading = false },
    updateTeamRequest(state, _action: PayloadAction<TeamSettings>) { state.saving = true; state.error = null },
    updateTeamSuccess(state, action: PayloadAction<TeamSettings>) { state.settings = action.payload; state.saving = false },
    createMemberRequest(state, _action: PayloadAction<NewTeamMember>) { state.saving = true; state.error = null },
    createMemberSuccess(state, action: PayloadAction<TeamMember>) { state.members.push(action.payload); state.saving = false },
    toggleMemberRequest(state, _action: PayloadAction<{ id: number; active: boolean }>) { state.saving = true; state.error = null },
    updateMemberSuccess(state, action: PayloadAction<TeamMember>) { const index = state.members.findIndex((member) => member.id === action.payload.id); if (index >= 0) state.members[index] = action.payload; state.saving = false },
    teamFailure(state, action: PayloadAction<string>) { state.loading = false; state.saving = false; state.error = action.payload },
  },
})

export const { loadTeamRequest, loadTeamSuccess, updateTeamRequest, updateTeamSuccess, createMemberRequest, createMemberSuccess, toggleMemberRequest, updateMemberSuccess, teamFailure } = teamSlice.actions
export default teamSlice.reducer

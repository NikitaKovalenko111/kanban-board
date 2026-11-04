import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { BackendIdentity } from '../../api/backendSession'
import { isBackendMode } from '../../api/config'

interface BackendAuthState { identity: BackendIdentity | null; restoring: boolean; signingOut: boolean }
const initialState: BackendAuthState = { identity: null, restoring: isBackendMode, signingOut: false }

const slice = createSlice({
  name: 'backendAuth', initialState,
  reducers: {
    restoreBackendSessionRequest(state) { state.restoring = true },
    backendSessionAuthenticated(state, action: PayloadAction<BackendIdentity>) { state.identity = action.payload; state.restoring = false; state.signingOut = false },
    backendSessionCleared(state) { state.identity = null; state.restoring = false; state.signingOut = false },
    backendLogoutRequest(state) { state.signingOut = true },
  },
})
export const { restoreBackendSessionRequest, backendSessionAuthenticated, backendSessionCleared, backendLogoutRequest } = slice.actions
export default slice.reducer

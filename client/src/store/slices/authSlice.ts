import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { LoginCredentials, User } from '../../types'

interface AuthState { user: User | null; loading: boolean; error: string | null }
const savedUser = localStorage.getItem('mock-session')
const initialState: AuthState = { user: savedUser ? JSON.parse(savedUser) as User : null, loading: false, error: null }

const authSlice = createSlice({
  name: 'auth', initialState,
  reducers: {
    loginRequest(state, _action: PayloadAction<LoginCredentials>) { state.loading = true; state.error = null },
    loginSuccess(state, action: PayloadAction<User>) {
      const savedAvatar = localStorage.getItem(`mock-avatar-${action.payload.login}`)
      state.user = { ...action.payload, avatarUrl: savedAvatar ?? action.payload.avatarUrl }
      state.loading = false
      localStorage.setItem('mock-session', JSON.stringify(state.user))
    },
    loginFailure(state, action: PayloadAction<string>) { state.loading = false; state.error = action.payload },
    clearLoginError(state) { state.error = null },
    updateAvatar(state, action: PayloadAction<string | undefined>) {
      if (!state.user) return
      state.user.avatarUrl = action.payload
      if (action.payload) localStorage.setItem(`mock-avatar-${state.user.login}`, action.payload)
      else localStorage.removeItem(`mock-avatar-${state.user.login}`)
      localStorage.setItem('mock-session', JSON.stringify(state.user))
    },
    logout(state) { state.user = null; state.error = null; localStorage.removeItem('mock-session') },
  },
})

export const { loginRequest, loginSuccess, loginFailure, clearLoginError, updateAvatar, logout } = authSlice.actions
export default authSlice.reducer

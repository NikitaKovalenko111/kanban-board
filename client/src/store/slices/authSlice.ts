import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { User, UserRole } from '../../types'

interface AuthState { user: User | null }
const users: Record<UserRole, User> = {
  lead: { id: 1, name: 'Алексей Морозов', login: 'alexey', role: 'lead', position: 'Тимлид' },
  member: { id: 2, name: 'Мария Волкова', login: 'maria', role: 'member', position: 'Дизайнер' },
}
const savedRole = localStorage.getItem('mock-role') as UserRole | null

const authSlice = createSlice({
  name: 'auth', initialState: { user: savedRole ? users[savedRole] : null } as AuthState,
  reducers: {
    login(state, action: PayloadAction<UserRole>) { state.user = users[action.payload]; localStorage.setItem('mock-role', action.payload) },
    logout(state) { state.user = null; localStorage.removeItem('mock-role') },
  },
})

export const { login, logout } = authSlice.actions
export default authSlice.reducer

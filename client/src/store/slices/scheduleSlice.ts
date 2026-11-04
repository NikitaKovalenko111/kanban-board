import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { EventReminders, NewScheduleEvent, ScheduleEvent } from '../../types'

interface ScheduleState { items: ScheduleEvent[]; loading: boolean; saving: boolean; error: string | null }
const initialState: ScheduleState = { items: [], loading: false, saving: false, error: null }

const scheduleSlice = createSlice({
  name: 'schedule', initialState,
  reducers: {
    loadScheduleRequest(state, _action: PayloadAction<string>) { state.loading = true; state.error = null; state.items = [] },
    loadScheduleSuccess(state, action: PayloadAction<ScheduleEvent[]>) { state.items = action.payload; state.loading = false },
    createScheduleEventRequest(state, _action: PayloadAction<NewScheduleEvent>) { state.saving = true; state.error = null },
    createScheduleEventSuccess(state, action: PayloadAction<ScheduleEvent>) { state.items.push(action.payload); state.saving = false },
    updateEventRemindersRequest(state, _action: PayloadAction<EventReminders & { id: number }>) { state.saving = true; state.error = null },
    updateScheduleEventSuccess(state, action: PayloadAction<ScheduleEvent>) { const index = state.items.findIndex((item) => item.id === action.payload.id); if (index >= 0) state.items[index] = action.payload; state.saving = false },
    scheduleFailure(state, action: PayloadAction<string>) { state.loading = false; state.saving = false; state.error = action.payload },
  },
})

export const { loadScheduleRequest, loadScheduleSuccess, createScheduleEventRequest, createScheduleEventSuccess, updateEventRemindersRequest, updateScheduleEventSuccess, scheduleFailure } = scheduleSlice.actions
export default scheduleSlice.reducer

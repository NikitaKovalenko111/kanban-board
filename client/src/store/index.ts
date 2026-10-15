import { configureStore } from '@reduxjs/toolkit'
import createSagaMiddleware from 'redux-saga'
import { all } from 'redux-saga/effects'
import authReducer from './slices/authSlice'
import tasksReducer from './slices/tasksSlice'
import { tasksSaga } from './sagas/tasksSaga'
import teamReducer from './slices/teamSlice'
import { authSaga } from './sagas/authSaga'
import { teamSaga } from './sagas/teamSaga'

const sagaMiddleware = createSagaMiddleware()
export const store = configureStore({
  reducer: { auth: authReducer, tasks: tasksReducer, team: teamReducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware({ thunk: false }).concat(sagaMiddleware),
})
function* rootSaga() { yield all([authSaga(), tasksSaga(), teamSaga()]) }
sagaMiddleware.run(rootSaga)
export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

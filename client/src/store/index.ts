import { configureStore } from '@reduxjs/toolkit'
import createSagaMiddleware from 'redux-saga'
import { all } from 'redux-saga/effects'
import authReducer from './slices/authSlice'
import tasksReducer from './slices/tasksSlice'
import { tasksSaga } from './sagas/tasksSaga'

const sagaMiddleware = createSagaMiddleware()
export const store = configureStore({
  reducer: { auth: authReducer, tasks: tasksReducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware({ thunk: false }).concat(sagaMiddleware),
})
function* rootSaga() { yield all([tasksSaga()]) }
sagaMiddleware.run(rootSaga)
export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

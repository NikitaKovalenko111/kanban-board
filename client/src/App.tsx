import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import Header from './components/Header/Header'
import WorkspaceNav from './components/WorkspaceNav/WorkspaceNav'
import Sidebar from './components/Sidebar/Sidebar'
import BoardPage from './pages/BoardPage/BoardPage'
import LoginPage from './pages/LoginPage/LoginPage'
import ProfilePage from './pages/ProfilePage/ProfilePage'
import HistoryPage from './pages/HistoryPage/HistoryPage'
import TeamPage from './pages/TeamPage/TeamPage'
import SchedulePage from './pages/SchedulePage/SchedulePage'
import ProjectsPage from './pages/ProjectsPage/ProjectsPage'
import SettingsPage from './pages/SettingsPage/SettingsPage'
import ProjectManagementPage from './pages/ProjectsPage/ProjectManagementPage'
import { useAppDispatch, useAppSelector } from './store/hooks'
import { isBackendMode } from './api/config'
import { backendSessionExpiredEvent } from './api/backendApi'
import { backendSessionCleared, restoreBackendSessionRequest } from './store/slices/backendAuthSlice'
import { loginFailure, logout } from './store/slices/authSlice'

import './styles/style.scss'

function MemberRedirect() { const { userId } = useParams(); return <Navigate to={`/team?member=${userId}`} replace /> }

function App() {
  const user = useAppSelector((state) => state.auth.user)
  const { identity, restoring } = useAppSelector((state) => state.backendAuth)
  const dispatch = useAppDispatch()
  useEffect(() => {
    if (!isBackendMode) return
    const expired = () => { dispatch(logout()); dispatch(backendSessionCleared()); dispatch(loginFailure('Сессия истекла. Войдите снова')) }
    window.addEventListener(backendSessionExpiredEvent, expired)
    dispatch(restoreBackendSessionRequest())
    return () => window.removeEventListener(backendSessionExpiredEvent, expired)
  }, [dispatch])
  useEffect(() => { document.documentElement.dataset.theme = localStorage.getItem('theme') ?? 'light' }, [])
  const [menuOpen, setMenuOpen] = useState(false)

  if (isBackendMode) {
    if (restoring) return <main className="loader" role="status">Проверяем сессию…</main>
    if (!identity || !user) return <Routes><Route path="*" element={<LoginPage />} /></Routes>
  }

  if (!user) {
    return <Routes><Route path="*" element={<LoginPage />} /></Routes>
  }

  return (
    <div className="app-shell">
      <Header onMenu={() => setMenuOpen(true)} />
      <WorkspaceNav />
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <Routes>
        <Route path="/" element={<BoardPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/members" element={<Navigate to="/team" replace />} />
        <Route path="/users/:userId" element={<MemberRedirect />} />
        <Route path="/team" element={<TeamPage />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/projects/:projectId/manage" element={<ProjectManagementPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}

export default App

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
import { useAppSelector } from './store/hooks'

import './styles/style.scss'

function MemberRedirect() { const { userId } = useParams(); return <Navigate to={`/team?member=${userId}`} replace /> }

function App() {
  const user = useAppSelector((state) => state.auth.user)
  useEffect(() => { document.documentElement.dataset.theme = localStorage.getItem('theme') ?? 'light' }, [])
  const [menuOpen, setMenuOpen] = useState(false)

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

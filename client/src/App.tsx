import { useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Header from './components/Header/Header'
import WorkspaceNav from './components/WorkspaceNav/WorkspaceNav'
import Sidebar from './components/Sidebar/Sidebar'
import BoardPage from './pages/BoardPage/BoardPage'
import LoginPage from './pages/LoginPage/LoginPage'
import ProfilePage from './pages/ProfilePage/ProfilePage'
import HistoryPage from './pages/HistoryPage/HistoryPage'
import TeamPage from './pages/TeamPage/TeamPage'
import MembersPage from './pages/MembersPage/MembersPage'
import MemberProfilePage from './pages/MembersPage/MemberProfilePage'
import SchedulePage from './pages/SchedulePage/SchedulePage'
import ProjectsPage from './pages/ProjectsPage/ProjectsPage'
import { useAppSelector } from './store/hooks'

import './styles/style.scss'

function App() {
  const user = useAppSelector((state) => state.auth.user)
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
        <Route path="/members" element={<MembersPage />} />
        <Route path="/users/:userId" element={<MemberProfilePage />} />
        <Route path="/team" element={user.role === 'lead' ? <TeamPage /> : <Navigate to="/" replace />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}

export default App

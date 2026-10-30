import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTeamRequest } from '../../store/slices/teamSlice'

const MembersPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const currentUser = useAppSelector((state) => state.auth.user)!
  const { members, loading } = useAppSelector((state) => state.team)
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const [query, setQuery] = useState('')

  useEffect(() => { if (!members.length) dispatch(loadTeamRequest()) }, [dispatch, members.length])
  const visibleMembers = useMemo(() => members.filter((member) => `${member.name} ${member.login} ${Object.values(member.projectRoles).join(' ')}`.toLowerCase().includes(query.toLowerCase())), [members, query])

  return <main className="members-page"><div className="container"><div className="page-heading"><div><span className="eyebrow">Пользователи</span><h1>Участники</h1></div><span className="page-heading__count">{members.filter((member) => member.active).length} активных</span></div><label className="search members-page__search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Найти участника" /></label>{loading ? <div className="loader">Загружаем участников…</div> : <section className="members-grid">{visibleMembers.map((member) => { const profileUser = member.id === currentUser.id ? currentUser : member; const projectRole = activeProjectId === 'all' ? (member.role === 'lead' ? 'Капитан' : 'Участник проектов') : member.projectRoles[activeProjectId] ?? 'Не участвует в проекте'; return <button className="member-card" key={member.id} onClick={() => navigate(`/users/${member.id}`)}><UserAvatar user={profileUser} size="large" /><span className="member-card__copy"><strong>{member.name}</strong><small>{projectRole}</small><small>@{member.login}</small></span><span className={`status-badge ${member.active ? 'is-active' : ''}`}>{member.active ? 'Активен' : 'Нет доступа'}</span><b>→</b></button> })}{!visibleMembers.length && <div className="empty-state">Участники не найдены</div>}</section>}</div></main>
}

export default MembersPage

import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import FormModal from '../../components/FormModal/FormModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createMemberRequest, loadTeamRequest, toggleMemberRequest } from '../../store/slices/teamSlice'
import type { UserRole } from '../../types'

const createPassword = () => `team-${Math.random().toString(36).slice(2, 8)}`

const TeamPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { members, loading, saving, error } = useAppSelector((state) => state.team)
  const currentUser = useAppSelector((state) => state.auth.user)!
  const projects = useAppSelector((state) => state.projects.items)
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [login, setLogin] = useState('')
  const [projectId, setProjectId] = useState(activeProjectId === 'all' ? '' : activeProjectId)
  const [projectRole, setProjectRole] = useState('')
  const [password, setPassword] = useState(createPassword)
  const [role, setRole] = useState<UserRole>('member')

  useEffect(() => { if (!members.length) dispatch(loadTeamRequest()) }, [dispatch, members.length])
  useEffect(() => { if (!projectId && projects.length) setProjectId(projects[0].id) }, [projectId, projects])
  const visibleMembers = useMemo(() => members.filter((member) => `${member.name} ${member.login} ${Object.values(member.projectRoles).join(' ')}`.toLowerCase().includes(search.toLowerCase())), [members, search])

  const createMember = (event: FormEvent) => {
    event.preventDefault()
    dispatch(createMemberRequest({ name, login, password, position: projectRole, projectRoles: projectId ? { [projectId]: projectRole } : {}, role }))
    setName(''); setLogin(''); setProjectRole(''); setPassword(createPassword()); setRole('member'); setShowCreate(false)
  }

  return <main className="team-page"><div className="container"><div className="page-heading"><div><span className="eyebrow">Для капитана</span><h1>Управление доступом</h1></div><button className="button" onClick={() => setShowCreate(true)}>＋ Новый участник</button></div>{error && <div className="error-banner">{error}</div>}<section className="team-stats"><div><strong>{members.length}</strong><span>Всего участников</span></div><div><strong>{members.filter((member) => member.active).length}</strong><span>Активных</span></div><div><strong>{members.filter((member) => member.role === 'lead').length}</strong><span>Капитанов</span></div></section><div className="team-layout"><div className="team-layout__main"><section className="panel"><div className="panel__heading"><div><h2>Участники</h2><p>Аккаунты и доступ к приложению</p></div><label className="search">⌕<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Найти участника" /></label></div>{loading ? <div className="loader">Загружаем участников…</div> : <div className="member-list">{visibleMembers.map((member) => { const visibleRole = activeProjectId === 'all' ? (member.role === 'lead' ? 'Капитан' : 'Участник') : member.projectRoles[activeProjectId] ?? 'Не участвует'; return <article className="member-row" key={member.id}><button className="member-row__profile" onClick={() => navigate(`/users/${member.id}`)}><span className="avatar">{member.name.split(' ').map((part) => part[0]).join('')}</span><span className="member-row__person"><strong>{member.name}</strong><small>@{member.login} · {visibleRole}</small></span></button><span className={`status-badge ${member.active ? 'is-active' : ''}`}>{member.active ? 'Активен' : 'Заблокирован'}</span><span className="role-badge member-row__role">{member.role === 'lead' ? 'Капитан' : 'Участник'}</span><button className="text-button" disabled={member.id === currentUser.id || saving} onClick={() => dispatch(toggleMemberRequest({ id: member.id, active: !member.active }))}>{member.active ? 'Заблокировать' : 'Восстановить'}</button></article> })}</div>}</section></div></div></div>{showCreate && <FormModal title="Новый участник" onClose={() => setShowCreate(false)}><form className="management-form" onSubmit={createMember}><label>Имя и фамилия<input value={name} onChange={(event) => setName(event.target.value)} required /></label><div className="form-grid"><label>Логин<input value={login} onChange={(event) => setLogin(event.target.value.toLowerCase())} pattern="[a-z0-9._-]+" required /></label><label>Статус<select value={role} onChange={(event) => setRole(event.target.value as UserRole)}><option value="member">Участник</option><option value="lead">Капитан</option></select></label></div><label>Проект<select value={projectId} onChange={(event) => setProjectId(event.target.value)} required>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label><label>Роль в проекте<input value={projectRole} onChange={(event) => setProjectRole(event.target.value)} placeholder="Например, Backend-разработчик" required /></label><label>Временный пароль<div className="generated-password"><input value={password} onChange={(event) => setPassword(event.target.value)} minLength={6} required /><button type="button" onClick={() => setPassword(createPassword())}>Обновить</button></div></label><button className="button" disabled={saving}>Создать аккаунт</button></form></FormModal>}</main>
}

export default TeamPage

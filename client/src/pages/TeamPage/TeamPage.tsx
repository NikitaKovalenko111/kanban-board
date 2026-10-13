import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createMemberRequest, loadTeamRequest, toggleMemberRequest, updateTeamRequest } from '../../store/slices/teamSlice'
import type { UserRole } from '../../types'

const createPassword = () => `team-${Math.random().toString(36).slice(2, 8)}`

const TeamPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { settings, members, loading, saving, error } = useAppSelector((state) => state.team)
  const currentUser = useAppSelector((state) => state.auth.user)!
  const [teamName, setTeamName] = useState('')
  const [description, setDescription] = useState('')
  const [search, setSearch] = useState('')
  const [name, setName] = useState('')
  const [login, setLogin] = useState('')
  const [position, setPosition] = useState('')
  const [password, setPassword] = useState(createPassword)
  const [role, setRole] = useState<UserRole>('member')

  useEffect(() => { if (!members.length) dispatch(loadTeamRequest()) }, [dispatch, members.length])
  useEffect(() => { setTeamName(settings.name); setDescription(settings.description) }, [settings])
  const visibleMembers = useMemo(() => members.filter((member) => `${member.name} ${member.login} ${member.position}`.toLowerCase().includes(search.toLowerCase())), [members, search])

  const saveSettings = (event: FormEvent) => { event.preventDefault(); dispatch(updateTeamRequest({ name: teamName, description })) }
  const createMember = (event: FormEvent) => {
    event.preventDefault()
    dispatch(createMemberRequest({ name, login, password, position, role }))
    setName(''); setLogin(''); setPosition(''); setPassword(createPassword()); setRole('member')
  }

  return <main className="team-page"><div className="container"><div className="page-heading"><div><span className="eyebrow">Для тимлида</span><h1>Управление командой</h1></div></div>{error && <div className="error-banner">{error}</div>}<section className="team-stats"><div><strong>{members.length}</strong><span>Всего участников</span></div><div><strong>{members.filter((member) => member.active).length}</strong><span>Активных</span></div><div><strong>{members.filter((member) => member.role === 'lead').length}</strong><span>Тимлидов</span></div></section><div className="team-layout"><div className="team-layout__main"><section className="panel"><div className="panel__heading"><div><h2>Участники</h2><p>Аккаунты и доступ к рабочей доске</p></div><label className="search">⌕<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Найти участника" /></label></div>{loading ? <div className="loader">Загружаем команду…</div> : <div className="member-list">{visibleMembers.map((member) => <article className="member-row" key={member.id}><button className="member-row__profile" onClick={() => navigate(`/users/${member.id}`)}><span className="avatar">{member.name.split(' ').map((part) => part[0]).join('')}</span><span className="member-row__person"><strong>{member.name}</strong><small>@{member.login} · {member.position}</small></span></button><span className={`status-badge ${member.active ? 'is-active' : ''}`}>{member.active ? 'Активен' : 'Заблокирован'}</span><span className="role-badge member-row__role">{member.role === 'lead' ? 'Тимлид' : 'Участник'}</span><button className="text-button" disabled={member.id === currentUser.id || saving} onClick={() => dispatch(toggleMemberRequest({ id: member.id, active: !member.active }))}>{member.active ? 'Заблокировать' : 'Восстановить'}</button></article>)}</div>}</section></div><aside className="team-layout__side"><section className="panel"><h2>Настройки команды</h2><form className="management-form" onSubmit={saveSettings}><label>Название<input value={teamName} onChange={(event) => setTeamName(event.target.value)} required /></label><label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} /></label><button className="button" disabled={saving}>Сохранить</button></form></section><section className="panel"><h2>Новый аккаунт</h2><form className="management-form" onSubmit={createMember}><label>Имя и фамилия<input value={name} onChange={(event) => setName(event.target.value)} required /></label><div className="form-grid"><label>Логин<input value={login} onChange={(event) => setLogin(event.target.value.toLowerCase())} pattern="[a-z0-9._-]+" required /></label><label>Роль<select value={role} onChange={(event) => setRole(event.target.value as UserRole)}><option value="member">Участник</option><option value="lead">Тимлид</option></select></label></div><label>Должность<input value={position} onChange={(event) => setPosition(event.target.value)} required /></label><label>Временный пароль<div className="generated-password"><input value={password} onChange={(event) => setPassword(event.target.value)} minLength={6} required /><button type="button" onClick={() => setPassword(createPassword())}>Обновить</button></div></label><button className="button" disabled={saving}>Создать аккаунт</button></form></section></aside></div></div></main>
}

export default TeamPage

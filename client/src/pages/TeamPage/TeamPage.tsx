import { isBackendMode } from '../../api/config'
import Icon from '../../components/Icon/Icon'
import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import FormModal from '../../components/FormModal/FormModal'
import SearchField from '../../components/SearchField/SearchField'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { api } from '../../api'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { useProjectSelection } from '../../store/useProjectSelection'
import { createMemberRequest, loadTeamRequest, toggleMemberRequest } from '../../store/slices/teamSlice'
import type { EntityId, UserRole } from '../../types'

const createPassword = () => 'team-' + crypto.randomUUID().slice(0, 12)

const TeamPage = () => {
  const dispatch = useAppDispatch()
  const [params, setParams] = useSearchParams()
  const { members, loading, saving, error } = useAppSelector((state) => state.team)
  const currentUser = useAppSelector((state) => state.auth.user)!
  const projects = useAppSelector((state) => state.projects.items)
  const activeProjectId = useProjectSelection('members')
  const canManage = currentUser.role === 'lead'
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [name, setName] = useState('')
  const [studentGroup, setStudentGroup] = useState('')
  const [login, setLogin] = useState('')
  const [projectId, setProjectId] = useState('')
  const [projectRole, setProjectRole] = useState('')
  const [password, setPassword] = useState(createPassword)
  const [role, setRole] = useState<UserRole>('member')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [actionError, setActionError] = useState('')
  const [resetting, setResetting] = useState(false)
  const selected = members.find((member) => String(member.id) === params.get('member'))

  useEffect(() => { dispatch(loadTeamRequest()) }, [dispatch])
  useEffect(() => {
    if (submitted && !saving && !error) {
      setShowCreate(false); setSubmitted(false)
      setName(''); setLogin(''); setProjectRole(''); setPassword(createPassword()); setRole('member')
    }
  }, [submitted, saving, error])
  const visibleMembers = members.filter((member) => (activeProjectId === 'all' || !!member.projectRoles[activeProjectId]) && `${member.name} ${member.login} ${Object.values(member.projectRoles).join(' ')}`.toLowerCase().includes(search.toLowerCase()))

  const createMember = (event: FormEvent) => {
    event.preventDefault()
    if (!canManage) return
    dispatch(createMemberRequest({ name: name.trim(), login, password, position: '', studentGroup, projectRoles: projectId ? { [projectId]: projectRole.trim() || 'Участник' } : {}, role }))
    setSubmitted(true)
  }
  const openMember = (id: EntityId) => {
    setTemporaryPassword(''); setActionError('')
    setParams({ member: String(id) })
  }
  const resetPassword = async () => {
    if (!selected) return
    setResetting(true); setActionError('')
    try { setTemporaryPassword((await api.resetPassword(selected.id)).password) }
    catch (reason) { setActionError(reason instanceof Error ? reason.message : 'Не удалось сбросить пароль') }
    finally { setResetting(false) }
  }

  return <main className="team-page"><div className="container">
    <div className="page-heading"><h1>Участники и доступ</h1>{canManage && <button className="button" onClick={() => { setProjectId(activeProjectId === 'all' ? '' : activeProjectId); setShowCreate(true) }}><Icon name="plus" /> Добавить</button>}</div>
    <SearchField value={search} onChange={setSearch} placeholder="Найти участника" />
    {error && <div className="error-banner" role="alert">{error}</div>}
    {loading ? <div className="loader">Загружаем участников…</div> : <section className="members-grid">
      {visibleMembers.map((member) => <button className="member-card" key={member.id} onClick={() => openMember(member.id)}>
        <UserAvatar user={member.id === currentUser.id ? currentUser : member} size="large" />
        <span className="member-card__copy"><strong>{member.name}</strong><small>@{member.login}</small><small>{activeProjectId === 'all' ? (member.role === 'lead' ? 'Капитан' : 'Участник') : member.projectRoles[activeProjectId]}</small></span>
        <span className={`status-badge ${member.active ? 'is-active' : ''}`}>{member.active ? 'Активен' : 'Заблокирован'}</span>
      </button>)}
      {!visibleMembers.length && <div className="empty-state">Участники не найдены</div>}
    </section>}
  </div>
  {selected && <FormModal title={selected.name} onClose={() => setParams({})}>
    <div className="member-dialog">
      <UserAvatar user={selected.id === currentUser.id ? currentUser : selected} size="large" />
      <p>@{selected.login}</p><p>{selected.role === 'lead' ? 'Капитан' : 'Участник'} · {selected.active ? 'Активен' : 'Заблокирован'}</p>
      <dl>{projects.filter((project) => selected.projectRoles[project.id]).map((project) => <div key={project.id}><dt>{project.name}</dt><dd>{selected.projectRoles[project.id]}</dd></div>)}</dl>
      {(error || actionError) && <div className="error-banner" role="alert">{actionError || error}</div>}
      {canManage && selected.id !== currentUser.id && <div className="task-details__actions">
        <button className="button button--secondary" disabled={saving || resetting} onClick={() => dispatch(toggleMemberRequest({ id: selected.id, active: !selected.active }))}>{selected.active ? 'Заблокировать' : 'Восстановить доступ'}</button>
        <button className="button" disabled={saving || resetting} onClick={() => void resetPassword()}>Сбросить пароль</button>
      </div>}
      {temporaryPassword && <label className="temporary-password">Новый временный пароль<output>{temporaryPassword}</output></label>}
    </div>
  </FormModal>}
  {showCreate && canManage && <FormModal title="Новый участник" onClose={() => setShowCreate(false)}>
    <form className="management-form" onSubmit={createMember}>
      <label>Имя и фамилия<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
      <label>Логин<input autoComplete="off" value={login} onChange={(event) => setLogin(event.target.value.toLowerCase())} pattern="[a-z0-9._-]+" required /></label>
      <label>Статус<select value={role} onChange={(event) => setRole(event.target.value as UserRole)}><option value="member">Участник</option><option value="lead">Капитан</option></select></label>
      {!isBackendMode && <label>Проект<select value={projectId} onChange={(event) => setProjectId(event.target.value)}><option value="">Без проекта</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>}
      {isBackendMode && <label>Учебная группа<input value={studentGroup} onChange={(event) => setStudentGroup(event.target.value)} required /></label>}
      {!isBackendMode && projectId && <label>Роль в проекте<input value={projectRole} onChange={(event) => setProjectRole(event.target.value)} required /></label>}
      <label>Временный пароль<div className="generated-password"><input value={password} onChange={(event) => setPassword(event.target.value)} minLength={isBackendMode ? 8 : 6} required /><button type="button" onClick={() => setPassword(createPassword())}>Обновить</button></div></label>
      {error && <div className="error-banner" role="alert">{error}</div>}
      <button className="button" disabled={saving}>Создать аккаунт</button>
    </form>
  </FormModal>}</main>
}

export default TeamPage

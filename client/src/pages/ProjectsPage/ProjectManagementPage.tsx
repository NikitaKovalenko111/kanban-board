import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { mockApi } from '../../api/mockApi'
import FormModal from '../../components/FormModal/FormModal'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadProjectsRequest, updateProjectRequest } from '../../store/slices/projectsSlice'
import type { TeamMember } from '../../types'

const ProjectManagementPage = () => {
  const { projectId } = useParams()
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const { items, loading, saving, error } = useAppSelector((state) => state.projects)
  const project = items.find((item) => item.id === projectId)
  const [members, setMembers] = useState<TeamMember[]>([])
  const [memberError, setMemberError] = useState('')
  const [membersLoading, setMembersLoading] = useState(true)
  const [dialog, setDialog] = useState<'details' | 'members' | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [color, setColor] = useState('#6577df')
  const [roles, setRoles] = useState<Record<number, string>>({})
  const [submitted, setSubmitted] = useState(false)
  const canManage = user.role === 'lead'

  useEffect(() => { if (!items.length) dispatch(loadProjectsRequest()) }, [dispatch, items.length])
  useEffect(() => {
    let cancelled = false
    setMembersLoading(true)
    mockApi.getTeam().then((result) => { if (!cancelled) { setMembers(result.members); setMemberError('') } })
      .catch((reason: unknown) => { if (!cancelled) setMemberError(reason instanceof Error ? reason.message : 'Не удалось загрузить участников') })
      .finally(() => { if (!cancelled) setMembersLoading(false) })
    return () => { cancelled = true }
  }, [project])
  useEffect(() => { if (submitted && !saving && !error) { setDialog(null); setSubmitted(false) } }, [submitted, saving, error])

  const open = (next: 'details' | 'members') => {
    if (!project) return
    setName(project.name); setDescription(project.description); setColor(project.color)
    setRoles(Object.fromEntries(members.filter((member) => member.projectRoles[project.id]).map((member) => [member.id, member.projectRoles[project.id]])))
    setDialog(next)
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!project || !canManage) return
    dispatch(updateProjectRequest({ id: project.id, name, description, color, members: Object.entries(roles).map(([id, role]) => ({ userId: Number(id), role })) }))
    setSubmitted(true)
  }
  if (loading) return <div className="loader">Загружаем проект…</div>
  if (!project) return <main className="projects-page"><div className="container"><Link className="back-link" to="/projects">← К проектам</Link><h1>Проект не найден</h1></div></main>

  const projectMembers = members.filter((member) => member.projectRoles[project.id])
  return <main className="projects-page"><div className="container">
    <Link className="back-link" to="/projects"><span>←</span>К проектам</Link>
    <div className="page-heading"><div><span className="eyebrow">Управление проектом</span><h1>{project.name}</h1><p>{project.description || 'Без описания'}</p></div>{canManage && <button className="button" onClick={() => open('details')}>Изменить проект</button>}</div>
    {error && <div className="error-banner">{error}</div>}
    {memberError && <div className="error-banner">{memberError}</div>}
    <section className="panel">
      <div className="panel__heading"><div><h2>Участники проекта</h2><p>Состав и функциональные роли в этом проекте</p></div>{canManage && <button className="button" disabled={membersLoading || !!memberError} onClick={() => open('members')}>Настроить участников</button>}</div>
      <div className="project-members">{membersLoading ? <div className="loader">Загружаем участников…</div> : projectMembers.map((member) => <article key={member.id}><UserAvatar user={member} /><div><Link to={`/users/${member.id}`}>{member.name}</Link><small>{member.projectRoles[project.id]}</small></div>{!member.active && <span className="status-badge">Заблокирован</span>}</article>)}{!membersLoading && !projectMembers.length && <div className="empty-state">Добавьте участников проекта</div>}</div>
    </section>
  </div>{dialog && canManage && <FormModal title={dialog === 'details' ? 'Настройки проекта' : 'Участники и роли'} onClose={() => { if (!saving) setDialog(null) }}>
    <form className="management-form" onSubmit={submit}>
      {dialog === 'details' ? <><label>Название<input value={name} onChange={(event) => setName(event.target.value)} required /></label><label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} /></label><label>Цвет<input className="event-color-input" type="color" value={color} onChange={(event) => setColor(event.target.value)} /></label></> : <div className="project-members-editor">{members.map((member) => <article key={member.id}>
        <label className="project-members-editor__check"><input type="checkbox" checked={member.id in roles} onChange={(event) => setRoles((current) => { const next = { ...current }; if (event.target.checked) next[member.id] = 'Участник проекта'; else delete next[member.id]; return next })} />{member.name}</label>
        {member.id in roles && <label>Роль в проекте<input value={roles[member.id]} onChange={(event) => setRoles({ ...roles, [member.id]: event.target.value })} placeholder="Например, Backend-разработчик" required /></label>}
      </article>)}</div>}
      {error && <div className="error-banner">{error}</div>}
      <button className="button" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить изменения'}</button>
    </form>
  </FormModal>}</main>
}

export default ProjectManagementPage

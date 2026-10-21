import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { addProjectSourcesRequest, createProjectRequest, loadProjectsRequest, setActiveProject } from '../../store/slices/projectsSlice'

const projectColors = ['#6577df', '#8b72e8', '#4e8bd8', '#42a682', '#e58a58', '#d75f82']
const formatSize = (size: number) => size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} МБ` : `${Math.ceil(size / 1024)} КБ`

const ProjectsPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const user = useAppSelector((state) => state.auth.user)!
  const { items, activeProjectId, loading, saving, error } = useAppSelector((state) => state.projects)
  const tasks = useAppSelector((state) => state.tasks.items)
  const activeProject = items.find((project) => project.id === activeProjectId)
  const [tab, setTab] = useState<'projects' | 'sources'>('projects')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [color, setColor] = useState(projectColors[0])

  useEffect(() => { if (!items.length) dispatch(loadProjectsRequest()) }, [dispatch, items.length])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    dispatch(createProjectRequest({ name, description, color }))
    setName('')
    setDescription('')
    setColor(projectColors[0])
  }

  const openProject = (projectId: string) => {
    dispatch(setActiveProject(projectId))
    navigate('/')
  }

  const openSources = (projectId: string) => {
    dispatch(setActiveProject(projectId))
    setTab('sources')
  }

  const uploadSources = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (!files?.length || !activeProject) return
    dispatch(addProjectSourcesRequest({
      projectId: activeProject.id,
      author: user.name,
      files: Array.from(files).map((file, index) => ({ id: `source-${Date.now()}-${index}`, name: file.name, size: file.size })),
    }))
    event.target.value = ''
  }

  return <main className="projects-page"><div className="container">
    <div className="page-heading"><div><span className="eyebrow">Рабочие пространства</span><h1>Проекты команды</h1><p>Доски, расписание и общие файлы команды собраны по проектам.</p></div></div>
    <div className="project-page-tabs" role="tablist" aria-label="Раздел проекта">
      <button role="tab" aria-selected={tab === 'projects'} className={tab === 'projects' ? 'is-active' : ''} onClick={() => setTab('projects')}>Проекты <span>{items.length}</span></button>
      <button role="tab" aria-selected={tab === 'sources'} className={tab === 'sources' ? 'is-active' : ''} onClick={() => setTab('sources')}>Источники <span>{activeProject?.sources.length ?? 0}</span></button>
    </div>
    {error && <div className="error-banner">{error}</div>}
    {tab === 'projects' && <div className={`projects-layout ${user.role !== 'lead' ? 'projects-layout--single' : ''}`}>
      <section className="panel projects-panel">
        <div className="panel__heading"><div><h2>Все проекты</h2><p>{items.length} рабочих пространств</p></div></div>
        {loading ? <div className="loader">Загружаем проекты…</div> : <div className="projects-grid">{items.map((project) => {
          const projectTasks = tasks.filter((task) => task.projectId === project.id)
          const completedTasks = projectTasks.filter((task) => task.taskStatus === 'done').length
          const isActive = project.id === activeProjectId
          return <article className={`project-card ${isActive ? 'is-active' : ''}`} key={project.id}>
            <div className="project-card__top"><span className="project-card__mark" style={{ background: project.color }} />{isActive && <span className="project-card__active">Активный</span>}</div>
            <h3>{project.name}</h3><p>{project.description || 'Без описания'}</p>
            <div className="project-card__stats"><span><strong>{projectTasks.length}</strong> задач</span><span><strong>{completedTasks}</strong> выполнено</span><span><strong>{project.sources.length}</strong> файлов</span></div>
            <div className="project-card__actions"><button type="button" className={isActive ? 'button button--muted' : 'button'} onClick={() => openProject(project.id)}>{isActive ? 'Открыть доску' : 'Выбрать проект'}</button><button type="button" className="project-card__sources" onClick={() => openSources(project.id)}>Источники</button></div>
          </article>
        })}</div>}
      </section>

      {user.role === 'lead' && <aside className="panel project-create">
        <div className="project-create__heading"><span>＋</span><div><h2>Новый проект</h2><p>Он сразу появится в переключателе.</p></div></div>
        <form className="management-form" onSubmit={submit}>
          <label>Название<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, Осенний хакатон" required /></label>
          <label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Коротко о цели проекта" /></label>
          <fieldset className="color-picker"><legend>Цвет проекта</legend><div>{projectColors.map((value) => <label key={value} style={{ background: value }} className={color === value ? 'is-selected' : ''}><input type="radio" name="project-color" value={value} checked={color === value} onChange={() => setColor(value)} /><span>Выбрать цвет</span></label>)}</div></fieldset>
          <button className="button" disabled={saving}>{saving ? 'Создаём…' : 'Создать проект'}</button>
        </form>
      </aside>}
    </div>}

    {tab === 'sources' && <section className="panel project-sources">
      <div className="project-sources__heading"><div className="project-sources__title"><span style={{ background: activeProject?.color }} /><div><small>Активный проект</small><h2>{activeProject?.name ?? 'Проект не выбран'}</h2></div></div>{activeProject && <label className={`source-upload ${saving ? 'is-disabled' : ''}`}>＋ Добавить файлы<input type="file" multiple disabled={saving} onChange={uploadSources} /></label>}</div>
      {!activeProject ? <div className="empty-state">Сначала выберите проект</div> : activeProject.sources.length ? <div className="source-list">{activeProject.sources.map((source) => <article className="source-row" key={source.id}><span className="source-row__icon">▱</span><span className="source-row__name"><strong>{source.name}</strong><small>Добавил: {source.uploadedBy} · {new Date(source.uploadedAt).toLocaleDateString('ru-RU')}</small></span><span className="source-row__size">{formatSize(source.size)}</span></article>)}</div> : <div className="sources-empty"><span>▱</span><h3>Источников пока нет</h3><p>Добавьте документы, архивы, макеты или другие общие файлы проекта.</p></div>}
    </section>}
  </div></main>
}

export default ProjectsPage

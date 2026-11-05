import { isBackendMode } from '../../api/config'
import Icon from '../../components/Icon/Icon'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import FormModal from '../../components/FormModal/FormModal'
import SearchField from '../../components/SearchField/SearchField'
import { uploadFiles, downloadFile } from '../../api/attachments'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { useProjectSelection } from '../../store/useProjectSelection'
import { addProjectSourcesRequest, createProjectRequest, deleteProjectSourceRequest, loadProjectsRequest, setProjectForTab } from '../../store/slices/projectsSlice'

const projectColors = ['#6577df', '#8b72e8', '#4e8bd8', '#42a682', '#e58a58', '#d75f82']
const formatSize = (size: number) => size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} МБ` : `${Math.ceil(size / 1024)} КБ`

const ProjectsPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const user = useAppSelector((state) => state.auth.user)!
  const { items, loading, saving, error } = useAppSelector((state) => state.projects)
  const activeProjectId = useProjectSelection('projects')
  const activeProject = items.find((project) => project.id === activeProjectId)
  const [showCreate, setShowCreate] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [color, setColor] = useState(projectColors[0])
  const [newFiles, setNewFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [fileError, setFileError] = useState('')
  const [query, setQuery] = useState('')
  const [sourceQuery, setSourceQuery] = useState('')
  const [sourceToDelete, setSourceToDelete] = useState<{ projectId: string; sourceId: string; name: string } | null>(null)
  const [deleting, setDeleting] = useState(false)
  const tab = params.get('tab') === 'sources' ? 'sources' : 'projects'
  useEffect(() => { if (!items.length) dispatch(loadProjectsRequest()) }, [dispatch, items.length])
  useEffect(() => {
    if (deleting && !saving) {
      if (!error) setSourceToDelete(null)
      setDeleting(false)
    }
  }, [deleting, saving, error])
  useEffect(() => {
    if (submitted && !saving && !error) {
      setShowCreate(false); setSubmitted(false); setName(''); setDescription(''); setNewFiles([]); setColor(projectColors[0])
    }
  }, [submitted, saving, error])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setUploading(true); setFileError('')
    try {
      const sources = await uploadFiles(newFiles)
      dispatch(createProjectRequest({ name: name.trim(), description: description.trim(), color, sources }))
      setSubmitted(true)
    } catch (reason) { setFileError(reason instanceof Error ? reason.message : 'Не удалось загрузить файлы') }
    finally { setUploading(false) }
  }
  const openProject = (id: string) => { dispatch(setProjectForTab({ tab: 'board', projectId: id })); navigate('/') }
  const openSources = (id: string) => { dispatch(setProjectForTab({ tab: 'projects', projectId: id })); setParams({ tab: 'sources' }) }
  const uploadSources = async (event: ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (!selected.length || !activeProject) return
    setUploading(true); setFileError('')
    try { dispatch(addProjectSourcesRequest({ projectId: activeProject.id, author: user.name, files: await uploadFiles(selected) })) }
    catch (reason) { setFileError(reason instanceof Error ? reason.message : 'Не удалось загрузить файлы') }
    finally { setUploading(false) }
  }
  const visibleProjects = items.filter((project) => `${project.name} ${project.description}`.toLowerCase().includes(query.toLowerCase()))
  const sourceProjects = items.filter((project) => activeProjectId === 'all' || project.id === activeProjectId)
  const sources = sourceProjects.flatMap((project) => project.sources.map((file) => ({ ...file, projectId: project.id, projectName: project.name }))).filter((file) => file.name.toLowerCase().includes(sourceQuery.toLowerCase()))

  return <main className="projects-page"><div className="container">
    <div className="page-heading"><h1>Проекты</h1>{user.role === 'lead' && <button className="button" onClick={() => { setFileError(''); setShowCreate(true) }}><Icon name="plus" /> Новый проект</button>}</div>
    <div className="project-page-tabs" role="tablist" aria-label="Раздел проекта">
      <button role="tab" aria-selected={tab === 'projects'} className={tab === 'projects' ? 'is-active' : ''} onClick={() => setParams({})}>Проекты</button>
      <button role="tab" aria-selected={tab === 'sources'} className={tab === 'sources' ? 'is-active' : ''} onClick={() => setParams({ tab: 'sources' })}>Источники</button>
    </div>
    {(error || fileError) && <div className="error-banner" role="alert">{fileError || error}</div>}
    {tab === 'projects' && <>
      <SearchField value={query} onChange={setQuery} placeholder="Поиск проектов" />
      {loading ? <div className="loader">Загружаем проекты…</div> : <div className="projects-grid compact-projects">{visibleProjects.map((project) => <article className="project-card" key={project.id}>
        <div className="project-card__top"><span className="project-card__mark" style={{ background: project.color }} /><h3>{project.name}</h3>
          <details className="project-actions"><summary aria-label={`Действия проекта ${project.name}`}>⋯</summary><div><button onClick={() => openSources(project.id)}>Источники</button><Link to={`/projects/${project.id}/manage`}>{user.role === 'lead' ? 'Управление проектом' : 'Участники проекта'}</Link></div></details>
        </div>
        {project.description && <p>{project.description}</p>}
        <button className="button" onClick={() => openProject(project.id)}>Открыть Kanban-доску</button>
      </article>)}</div>}
      {!loading && !visibleProjects.length && <div className="empty-state">Проекты не найдены{query && <button className="text-button" onClick={() => setQuery('')}>Очистить поиск</button>}</div>}
    </>}
    {tab === 'sources' && <section className="panel project-sources">
      <div className="project-sources__heading"><div className="project-sources__title">{activeProject && <span style={{ background: activeProject.color }} />}<h2>{activeProject?.name ?? 'Все источники'}</h2></div>
        {activeProject && <label className="source-upload"><Icon name="plus" /> Добавить файлы<input type="file" multiple disabled={saving || uploading} onChange={(event) => void uploadSources(event)} /></label>}
      </div>
      <div className="source-filters"><SearchField value={sourceQuery} onChange={setSourceQuery} placeholder="Поиск источников" /></div>
      {uploading && <p role="status">Загрузка файлов…</p>}
      <div className="source-list">{sources.map((source) => <article className="source-row" key={source.id}><button className="source-row__download" onClick={() => { setFileError(''); void downloadFile(source).catch((reason: unknown) => setFileError(reason instanceof Error ? reason.message : 'Не удалось скачать файл')) }}>
        <svg className="attachment-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m8 12 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8" /></svg>
        <span className="source-row__name"><strong>{source.name}</strong><small>{source.projectName} · {source.uploadedBy}</small></span><span className="source-row__size">{source.url ? 'Ссылка' : formatSize(source.size)} <Icon name={source.url ? 'arrow-right' : 'arrow-down'} /></span>
      </button>{(user.role === 'lead' || (source.uploadedById !== undefined ? source.uploadedById === user.id : source.uploadedBy === user.name)) && <button className="icon-button source-row__delete" disabled={saving || uploading} title="Удалить источник" aria-label={`Удалить источник ${source.name}`} onClick={() => setSourceToDelete({ projectId: source.projectId, sourceId: source.id, name: source.name })}><Icon name="trash" /></button>}</article>)}</div>
      {!sources.length && <div className="empty-state">Источники не найдены</div>}
    </section>}
  </div>
  {sourceToDelete && <FormModal title="Удалить источник?" onClose={() => { if (!saving) setSourceToDelete(null) }}>
    <p className="delete-source-name">{sourceToDelete.name}</p>
    {error && <div className="error-banner" role="alert">{error}</div>}
    <div className="task-details__actions"><button className="button button--secondary" disabled={saving} onClick={() => setSourceToDelete(null)}>Отмена</button><button className="button button--danger" disabled={saving} onClick={() => { dispatch(deleteProjectSourceRequest(sourceToDelete)); setDeleting(true) }}>{saving ? 'Удаляем…' : 'Удалить источник'}</button></div>
  </FormModal>}
  {showCreate && <FormModal title="Новый проект" onClose={() => { if (!uploading && !saving) setShowCreate(false) }}>
    <form className="management-form" onSubmit={(event) => void submit(event)}>
      <label>Название<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
      <label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      {!isBackendMode && <fieldset className="color-picker"><legend>Цвет проекта</legend><div>{projectColors.map((value) => <label key={value} style={{ background: value }} className={color === value ? 'is-selected' : ''}><input type="radio" name="project-color" value={value} checked={color === value} onChange={() => setColor(value)} /><span>Выбрать цвет</span></label>)}</div></fieldset>}
      <label className="file-upload">Прикрепить источники<input type="file" multiple onChange={(event) => {
        const files = Array.from(event.target.files ?? [])
        event.target.value = ''
        if (files.some((file) => file.size > 25 * 1024 * 1024)) { setFileError('Размер каждого файла не должен превышать 25 МБ'); return }
        setFileError(''); setNewFiles((previous) => [...previous, ...files])
      }} /></label>
      {newFiles.map((file, index) => <div className="selected-file" key={index}><span>{file.name} · {formatSize(file.size)}</span><button type="button" className="text-button" aria-label={`Удалить файл ${file.name}`} onClick={() => setNewFiles((previous) => previous.filter((_, fileIndex) => fileIndex !== index))}>Удалить</button></div>)}
      {(fileError || error) && <div className="error-banner" role="alert">{fileError || error}</div>}
      <button className="button" disabled={saving || uploading}>{uploading ? 'Загрузка файлов…' : saving ? 'Создаём…' : 'Создать проект'}</button>
    </form>
  </FormModal>}</main>
}
export default ProjectsPage

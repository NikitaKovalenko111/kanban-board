import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadProjectsRequest, setActiveProject } from '../../store/slices/projectsSlice'

const WorkspaceNav = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { items, activeProjectId, loading } = useAppSelector((state) => state.projects)
  const user = useAppSelector((state) => state.auth.user)!
  const activeProject = items.find((project) => project.id === activeProjectId)
  const allProjectsSelected = activeProjectId === 'all'
  const [projectsOpen, setProjectsOpen] = useState(false)
  const switcherRef = useRef<HTMLDivElement>(null)

  useEffect(() => { if (!items.length) dispatch(loadProjectsRequest()) }, [dispatch, items.length])
  useEffect(() => {
    const close = (event: MouseEvent) => { if (!switcherRef.current?.contains(event.target as Node)) setProjectsOpen(false) }
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') setProjectsOpen(false) }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', closeOnEscape)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', closeOnEscape) }
  }, [])

  const selectProject = (projectId: string) => {
    dispatch(setActiveProject(projectId))
    setProjectsOpen(false)
    if (projectId === 'all') navigate('/')
  }

  return <div className="workspace-nav">
    <div className="container workspace-nav__container">
      <div className={`project-switcher ${projectsOpen ? 'is-open' : ''}`} ref={switcherRef}>
        <button className="project-switcher__trigger" type="button" onClick={() => setProjectsOpen((value) => !value)} disabled={loading} aria-haspopup="listbox" aria-expanded={projectsOpen}>
          <span className={`project-switcher__dot ${allProjectsSelected ? 'project-switcher__dot--all' : ''}`} style={{ background: allProjectsSelected ? undefined : activeProject?.color ?? '#aab1be' }} />
          <span className="project-switcher__copy"><small>{allProjectsSelected ? 'Общий обзор' : 'Проект команды'}</small><strong>{loading ? 'Загрузка…' : allProjectsSelected ? 'Все проекты' : activeProject?.name ?? 'Выберите проект'}</strong></span>
          <svg className="project-switcher__chevron" viewBox="0 0 20 20" aria-hidden="true"><path d="m6 8 4 4 4-4" /></svg>
        </button>
        {projectsOpen && <div className="project-menu" role="listbox" aria-label="Выбор проекта">
          <div className="project-menu__heading"><span>Проекты команды</span><small>{items.length}</small></div>
          <div className="project-menu__list">
            <button type="button" role="option" aria-selected={allProjectsSelected} className={`project-menu__all ${allProjectsSelected ? 'is-active' : ''}`} onClick={() => selectProject('all')}>
              <span className="project-menu__color project-menu__color--all" />
              <span><strong>Все проекты</strong><small>Задачи со всех досок</small></span>
              {allProjectsSelected && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 10 3 3 7-7" /></svg>}
            </button>
            {items.map((project) => <button type="button" role="option" aria-selected={project.id === activeProjectId} className={project.id === activeProjectId ? 'is-active' : ''} key={project.id} onClick={() => selectProject(project.id)}>
              <span className="project-menu__color" style={{ background: project.color }} />
              <span><strong>{project.name}</strong><small>{project.description || 'Без описания'}</small></span>
              {project.id === activeProjectId && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 10 3 3 7-7" /></svg>}
            </button>)}
          </div>
          <Link className="project-menu__manage" to="/projects" onClick={() => setProjectsOpen(false)}><span>{user.role === 'lead' ? '＋' : '→'}</span> {user.role === 'lead' ? 'Управление проектами' : 'Все проекты'}</Link>
        </div>}
      </div>
      {user.role === 'lead' && activeProject && <Link className="project-nav-manage" to={`/projects/${activeProject.id}/manage`}>Управление проектом</Link>}
    </div>
  </div>
}

export default WorkspaceNav

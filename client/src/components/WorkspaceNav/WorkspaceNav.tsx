import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
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

      <nav className="workspace-tabs" aria-label="Раздел проекта">
        <NavLink to="/" end className="workspace-tab">
          <span className="workspace-tab__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M4 5.5h4.5v13H4zM10.25 5.5h4.5v8h-4.5zM16.5 5.5H21v10.5h-4.5z" /></svg>
          </span>
          <span className="workspace-tab__copy"><strong>Доска задач</strong><small>Работа по статусам</small></span>
        </NavLink>
        <NavLink to="/schedule" className="workspace-tab">
          <span className="workspace-tab__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M7 3v3M17 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z" /><path d="m9 14 2 2 4-4" /></svg>
          </span>
          <span className="workspace-tab__copy"><strong>Расписание</strong><small>События и дедлайны</small></span>
        </NavLink>
      </nav>
    </div>
  </div>
}

export default WorkspaceNav

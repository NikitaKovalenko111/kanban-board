import Icon from '../Icon/Icon'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadProjectsRequest, setProjectForTab } from '../../store/slices/projectsSlice'

import SearchField from '../SearchField/SearchField'
import { projectTab, useProjectSelection } from '../../store/useProjectSelection'

const WorkspaceNav = () => {
  const dispatch = useAppDispatch()
  const { pathname } = useLocation()
  const [params, setParams] = useSearchParams()
  const tab = projectTab(pathname)
  const activeProjectId = useProjectSelection(tab ?? '')
  const showSwitcher = !!tab && (pathname !== '/projects' || params.get('tab') === 'sources')
  const showSearch = pathname === '/' || pathname === '/history'
  const { items, loading } = useAppSelector((state) => state.projects)
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
    dispatch(setProjectForTab({ tab: tab!, projectId }))
    setProjectsOpen(false)

  }

  if (!showSwitcher && !showSearch) return null
  return <div className="workspace-nav">
    <div className="container workspace-nav__container">
      {showSwitcher && <div className="workspace-nav__project"><div className={`project-switcher ${projectsOpen ? 'is-open' : ''}`} ref={switcherRef}>
        <button className="project-switcher__trigger" type="button" onClick={() => setProjectsOpen((value) => !value)} disabled={loading} aria-haspopup="listbox" aria-expanded={projectsOpen}>
          <span className={`project-switcher__dot ${allProjectsSelected ? 'project-switcher__dot--all' : ''}`} style={{ background: allProjectsSelected ? undefined : activeProject?.color ?? '#aab1be' }} />
          <span className="project-switcher__copy"><strong>{loading ? 'Загрузка…' : allProjectsSelected ? 'Все проекты' : activeProject?.name ?? 'Выберите проект'}</strong></span>
          <svg className="project-switcher__chevron" viewBox="0 0 20 20" aria-hidden="true"><path d="m6 8 4 4 4-4" /></svg>
        </button>
        {projectsOpen && <div className="project-menu" role="listbox" aria-label="Выбор проекта">
          <div className="project-menu__heading"><span>Проекты команды</span><small>{items.length}</small></div>
          <div className="project-menu__list">
            <button type="button" role="option" aria-selected={allProjectsSelected} className={`project-menu__all ${allProjectsSelected ? 'is-active' : ''}`} onClick={() => selectProject('all')}>
              <span className="project-menu__color project-menu__color--all" />
              <span><strong>Все проекты</strong></span>
              {allProjectsSelected && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 10 3 3 7-7" /></svg>}
            </button>
            {items.map((project) => <button type="button" role="option" aria-selected={project.id === activeProjectId} className={project.id === activeProjectId ? 'is-active' : ''} key={project.id} onClick={() => selectProject(project.id)}>
              <span className="project-menu__color" style={{ background: project.color }} />
              <span><strong>{project.name}</strong><small>{project.description || 'Без описания'}</small></span>
              {project.id === activeProjectId && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 10 3 3 7-7" /></svg>}
            </button>)}
          </div>
          <Link className="project-menu__manage" to="/projects" onClick={() => setProjectsOpen(false)}><Icon name={user.role === 'lead' ? 'plus' : 'arrow-right'} /> {user.role === 'lead' ? 'Управление проектами' : 'Все проекты'}</Link>
        </div>}
      </div>
      {user.role === 'lead' && activeProject && pathname === '/' && <Link className="project-nav-manage" to={`/projects/${activeProject.id}/manage`} title="Управление проектом" aria-label="Управление проектом"><Icon name="settings" /></Link>}
      </div>}
      {showSearch && <SearchField value={params.get('q') ?? ''} onChange={(value) => setParams((previous) => { const next = new URLSearchParams(previous); if (value) next.set('q', value); else next.delete('q'); return next }, { replace: true })} placeholder={pathname === '/' ? 'Поиск по задачам' : 'Поиск по истории'} />}
    </div>
  </div>
}

export default WorkspaceNav

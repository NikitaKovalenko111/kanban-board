import { useAppSelector } from './hooks'

export const projectTab = (path: string): string | null => {
  if (path === '/') return 'board'
  if (path === '/schedule') return 'schedule'
  if (path === '/history') return 'history'
  if (path === '/team' || path === '/members') return 'members'
  if (path === '/projects') return 'projects'
  return null
}
export const useProjectSelection = (tab: string) => useAppSelector((state) => state.projects.selections?.[tab] ?? (tab === 'board' ? state.projects.activeProjectId : 'all'))

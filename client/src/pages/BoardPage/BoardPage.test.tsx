import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import tasks from '../../store/slices/tasksSlice'
import projects from '../../store/slices/projectsSlice'
import { project, task, user } from '../../test/fixtures'
import BoardPage from './BoardPage'

it('на телефоне переключает колонки свайпами, отличая их от вертикальной прокрутки', () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  try {
    const store = configureStore({ reducer: { auth, tasks, projects }, preloadedState: {
      auth: { user, loading: false, error: null },
      tasks: { items: [task], loading: false, saving: false, error: null },
      projects: { items: [project], activeProjectId: project.id, loading: false, saving: false, error: null },
    } })
    const { container } = render(<Provider store={store}><MemoryRouter><BoardPage /></MemoryRouter></Provider>)
    const slider = container.querySelector('.board-slider')!
    const track = container.querySelector('.board-page__track')! as HTMLElement
    const swipe = (x: number, y: number) => {
      fireEvent.touchStart(slider, { touches: [{ clientX: 250, clientY: 200 }] })
      fireEvent.touchEnd(slider, { changedTouches: [{ clientX: x, clientY: y }] })
    }
    swipe(50, 205)
    expect(track.style.getPropertyValue('--slide-index')).toBe('1')
    fireEvent.click(screen.getByRole('button', { name: /Подготовить макет/ }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    swipe(240, 450)
    expect(track.style.getPropertyValue('--slide-index')).toBe('1')
    swipe(390, 205)
    expect(track.style.getPropertyValue('--slide-index')).toBe('0')
    swipe(390, 205)
    expect(track.style.getPropertyValue('--slide-index')).toBe('0')
  } finally { vi.unstubAllGlobals() }
})

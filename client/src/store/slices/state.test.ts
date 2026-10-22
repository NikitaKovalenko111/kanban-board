import { describe, expect, it } from 'vitest'
import auth, * as a from './authSlice'
import tasks, * as t from './tasksSlice'
import projects, * as p from './projectsSlice'
import schedule, * as s from './scheduleSlice'
import team, * as m from './teamSlice'
import { task, user, project } from '../../test/fixtures'

describe('состояние приложения', () => {
  it('сохраняет сессию и аватар, очищает сессию при выходе', () => {
    localStorage.setItem(`mock-avatar-${user.login}`, 'data:image/png;base64,test')
    let state = auth(undefined, a.loginRequest({ login: user.login, password: 'secret' }))
    expect(state).toMatchObject({ loading: true, error: null })
    state = auth(state, a.loginSuccess(user))
    expect(state.user?.avatarUrl).toBe('data:image/png;base64,test')
    expect(JSON.parse(localStorage.getItem('mock-session')!)).toEqual(state.user)
    state = auth(state, a.updateAvatar(undefined))
    expect(localStorage.getItem(`mock-avatar-${user.login}`)).toBeNull()
    expect(JSON.parse(localStorage.getItem('mock-session')!)).not.toHaveProperty('avatarUrl')
    state = auth(state, a.logout())
    expect(state.user).toBeNull()
    expect(localStorage.getItem('mock-session')).toBeNull()
  })

  it('завершает неудачный вход и очищает ошибку при повторной попытке', () => {
    const failed = auth(undefined, a.loginFailure('Ошибка входа'))
    expect(failed).toMatchObject({ loading: false, error: 'Ошибка входа' })
    expect(auth(failed, a.loginRequest({ login: '', password: '' }))).toMatchObject({ loading: true, error: null })
  })

  it('загружает, добавляет и обновляет только нужную задачу', () => {
    let state = tasks(undefined, t.loadTasksRequest())
    expect(state.loading).toBe(true)
    state = tasks(state, t.loadTasksSuccess([task]))
    expect(state.loading).toBe(false)
    const second = { ...task, taskId: 2 }
    state = tasks(state, t.createTaskRequest(second))
    expect(state.saving).toBe(true)
    state = tasks(state, t.createTaskSuccess(second))
    const updated = { ...task, taskStatus: 'working' as const }
    state = tasks(state, t.updateTaskSuccess(updated))
    expect(state.items).toEqual([second, updated])
    expect(state.saving).toBe(false)
    expect(tasks(state, t.updateTaskSuccess({ ...task, taskId: -1 })).items).toEqual(state.items)
  })

  it('снимает флаги загрузки и сохранения при ошибке задач', () => {
    const loading = tasks(undefined, t.loadTasksRequest())
    const saving = tasks(loading, t.createTaskRequest(task))
    expect(tasks(saving, t.tasksFailure('Ошибка'))).toMatchObject({ loading: false, saving: false, error: 'Ошибка' })
  })

  it('выбирает доступный проект, сохраняет выбор и поддерживает режим all', () => {
    let state = projects(undefined, p.loadProjectsSuccess([project]))
    expect(state.activeProjectId).toBe(project.id)
    state = projects(state, p.setActiveProject('all'))
    expect(localStorage.getItem('active-project')).toBe('all')
    expect(projects(state, p.loadProjectsSuccess([project])).activeProjectId).toBe('all')
    state = projects(state, p.createProjectSuccess({ ...project, id: 'new' }))
    expect(state.activeProjectId).toBe('new')
    expect(localStorage.getItem('active-project')).toBe('new')
    expect(projects(state, p.loadProjectsSuccess([])).activeProjectId).toBe('')
  })

  it('очищает старое расписание при переключении проекта и добавляет событие', () => {
    const event = { id: 1, projectId: project.id, title: 'Встреча', description: '', startsAt: '2026-10-23T10:00:00', type: 'meeting' as const, author: user.name }
    let state = schedule(undefined, s.loadScheduleSuccess([event]))
    state = schedule(state, s.loadScheduleRequest('other'))
    expect(state).toMatchObject({ items: [], loading: true })
    state = schedule(state, s.loadScheduleSuccess([]))
    state = schedule(state, s.createScheduleEventRequest(event))
    expect(state.saving).toBe(true)
    expect(schedule(state, s.createScheduleEventSuccess(event))).toMatchObject({ items: [event], saving: false, loading: false })
  })

  it('обновляет настройки команды и статус выбранного участника', () => {
    const member = { ...user, active: true, joinedAt: '2026-10-01' }
    const settings = { name: 'Команда', description: 'Описание' }
    let state = team(undefined, m.loadTeamSuccess({ settings, members: [member] }))
    state = team(state, m.createMemberSuccess({ ...member, id: 2 }))
    state = team(state, m.updateMemberSuccess({ ...member, active: false }))
    expect(state.members.map(member => member.active)).toEqual([false, true])
    state = team(state, m.updateTeamSuccess({ ...settings, name: 'Новое имя' }))
    expect(state.settings.name).toBe('Новое имя')
    expect(team(state, m.teamFailure('Ошибка'))).toMatchObject({ loading: false, saving: false, error: 'Ошибка' })
  })
})

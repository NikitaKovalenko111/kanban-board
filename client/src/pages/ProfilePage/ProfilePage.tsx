import { isBackendMode } from '../../api/config'
import { realApi } from '../../api/realApi'
import { loginSuccess } from '../../store/slices/authSlice'
import { updateMemberSuccess } from '../../store/slices/teamSlice'
import Icon from '../../components/Icon/Icon'
import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import type { Task } from '../../types'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { isAssignedTo } from '../../utils/taskPermissions'
import { updateAvatar } from '../../store/slices/authSlice'

const ProfilePage = () => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const tasks = useAppSelector((state) => state.tasks.items)
  const projects = useAppSelector((state) => state.projects.items)
  const inputRef = useRef<HTMLInputElement>(null)
  const [avatarError, setAvatarError] = useState('')
  const [assignedTasks, setAssignedTasks] = useState<Task[]>([])
  const [taskError, setTaskError] = useState('')
  useEffect(() => {
    if (!isBackendMode) return
    let active = true
    void realApi.getMyTasks().then(items => { if (active) setAssignedTasks(items) }).catch(reason => { if (active) setTaskError(reason.message) })
    return () => { active = false }
  }, [user.id, tasks])

  const [avatarSaving, setAvatarSaving] = useState(false)
  const persistAvatar = async (url?: string) => {
    if (!isBackendMode) { dispatch(updateAvatar(url)); return }
    setAvatarSaving(true); setAvatarError('')
    try {
      const file = url ? new File([await (await fetch(url)).blob()], 'avatar.jpg', { type: 'image/jpeg' }) : undefined
      const profile = await realApi.updateAvatar(file)
      dispatch(loginSuccess(profile)); dispatch(updateMemberSuccess(profile))
    } catch (reason) { setAvatarError((reason as Error).message) } finally { setAvatarSaving(false) }
  }
  const changeAvatar = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!file.type.startsWith('image/')) { setAvatarError('Выберите изображение'); return }
    if (file.size > 5 * 1024 * 1024) { setAvatarError('Размер изображения не должен превышать 5 МБ'); return }
    const reader = new FileReader()
    reader.onload = () => {
      const image = new Image()
      image.onload = () => {
        const size = Math.min(image.width, image.height)
        const canvas = document.createElement('canvas')
        canvas.width = 320; canvas.height = 320
        const context = canvas.getContext('2d')
        if (!context) { setAvatarError('Не удалось обработать изображение'); return }
        context.drawImage(image, (image.width - size) / 2, (image.height - size) / 2, size, size, 0, 0, 320, 320)
        void persistAvatar(canvas.toDataURL('image/jpeg', .86))
        setAvatarError('')
      }
      image.onerror = () => setAvatarError('Не удалось открыть изображение')
      image.src = reader.result as string
    }
    reader.onerror = () => setAvatarError('Не удалось прочитать файл')
    reader.readAsDataURL(file)
  }

  const ownTasks = isBackendMode ? assignedTasks : tasks.filter((task) => isAssignedTo(task, user))
  return <main className="profile-page"><div className="container"><div className="profile-page__heading"><h1>Мой профиль</h1></div><section className="profile-card"><div className="profile-card__hero"><div className="avatar-editor"><UserAvatar user={user} size="xl" /><button type="button" disabled={avatarSaving} onClick={() => inputRef.current?.click()} aria-label="Изменить аватар">✎</button><input ref={inputRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={changeAvatar} /></div><div className="profile-card__identity"><h2>{user.name}</h2><div className="avatar-actions"><button type="button" onClick={() => inputRef.current?.click()}>Изменить фото</button>{user.avatarUrl && <button type="button" className="is-danger" disabled={avatarSaving} onClick={() => void persistAvatar()}><Icon name="trash" />Удалить фото</button>}</div>{avatarError && <small className="avatar-error">{avatarError}</small>}</div></div><div className="profile-card__info"><label>Логин<strong>@{user.login}</strong></label><label>Роль в команде<strong>{user.role === 'lead' ? 'Тимлид команды' : 'Участник'}</strong></label></div></section><section className="project-roles"><div className="panel__heading"><div><h2>Роли в проектах</h2></div></div><div>{projects.map((project) => <article key={project.id}><i style={{ background: project.color }} /><span><strong>{project.name}</strong><small>{user.projectRoles?.[project.id] ?? 'Не участвует'}</small></span></article>)}</div></section>{taskError && <p className="error-banner" role="alert">{taskError}</p>}<section className="profile-stats"><div><span>{ownTasks.filter((task) => task.taskStatus === 'working').length}</span><p>Мои задачи в работе</p></div><div><span>{ownTasks.filter((task) => task.taskStatus === 'done').length}</span><p>Мои завершённые задачи</p></div><div><span>{ownTasks.length}</span><p>Всего моих задач</p></div></section></div></main>
}
export default ProfilePage

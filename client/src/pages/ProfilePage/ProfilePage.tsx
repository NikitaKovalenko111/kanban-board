import { useRef, useState, type ChangeEvent } from 'react'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { updateAvatar } from '../../store/slices/authSlice'

const ProfilePage = () => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const tasks = useAppSelector((state) => state.tasks.items)
  const projects = useAppSelector((state) => state.projects.items)
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const inputRef = useRef<HTMLInputElement>(null)
  const [avatarError, setAvatarError] = useState('')

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
        dispatch(updateAvatar(canvas.toDataURL('image/jpeg', .86)))
        setAvatarError('')
      }
      image.onerror = () => setAvatarError('Не удалось открыть изображение')
      image.src = reader.result as string
    }
    reader.onerror = () => setAvatarError('Не удалось прочитать файл')
    reader.readAsDataURL(file)
  }

  const activeRole = activeProjectId === 'all' ? null : user.projectRoles?.[activeProjectId]
  return <main className="profile-page"><div className="container"><div className="profile-page__heading"><span className="eyebrow">Личный кабинет</span><h1>Мой профиль</h1></div><section className="profile-card"><div className="profile-card__hero"><div className="avatar-editor"><UserAvatar user={user} size="xl" /><button type="button" onClick={() => inputRef.current?.click()} aria-label="Изменить аватар">✎</button><input ref={inputRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={changeAvatar} /></div><div className="profile-card__identity"><h2>{user.name}</h2><p>{activeRole ?? (user.role === 'lead' ? 'Капитан' : 'Участник проектов')}</p>{user.role === 'lead' && <span className="role-badge">Капитан</span>}<div className="avatar-actions"><button type="button" onClick={() => inputRef.current?.click()}>Изменить фото</button>{user.avatarUrl && <button type="button" className="is-danger" onClick={() => dispatch(updateAvatar(undefined))}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></svg>Удалить фото</button>}</div>{avatarError && <small className="avatar-error">{avatarError}</small>}</div></div><div className="profile-card__info"><label>Логин<strong>@{user.login}</strong></label><label>Статус<strong>{user.role === 'lead' ? 'Капитан' : 'Участник'}</strong></label></div></section><section className="project-roles"><div className="panel__heading"><div><h2>Роли в проектах</h2><p>Функциональная роль может отличаться в каждом проекте</p></div></div><div>{projects.map((project) => <article key={project.id}><i style={{ background: project.color }} /><span><strong>{project.name}</strong><small>{user.projectRoles?.[project.id] ?? 'Не участвует'}</small></span></article>)}</div></section><section className="profile-stats"><div><span>{tasks.filter((task) => task.taskStatus === 'working').length}</span><p>В работе</p></div><div><span>{tasks.filter((task) => task.taskStatus === 'done').length}</span><p>Завершено</p></div><div><span>{tasks.length}</span><p>Всего задач</p></div></section></div></main>
}
export default ProfilePage

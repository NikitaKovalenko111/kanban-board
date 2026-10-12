import { useAppSelector } from '../../store/hooks'

const ProfilePage = () => {
  const user = useAppSelector((state) => state.auth.user)!
  const tasks = useAppSelector((state) => state.tasks.items)
  const initials = user.name.split(' ').map((part) => part[0]).join('')
  return <main className="profile-page"><div className="container"><div className="profile-page__heading"><span className="eyebrow">Личный кабинет</span><h1>Мой профиль</h1></div><section className="profile-card"><div className="profile-card__hero"><span className="avatar avatar--xl">{initials}</span><div><h2>{user.name}</h2><p>{user.position}</p><span className="role-badge">{user.role === 'lead' ? 'Капитан команды' : 'Участник команды'}</span></div></div><div className="profile-card__info"><label>Логин<strong>@{user.login}</strong></label><label>Команда<strong>Веб-продукт</strong></label><label>Роль<strong>{user.role === 'lead' ? 'Тимлид' : 'Исполнитель'}</strong></label></div></section><section className="profile-stats"><div><span>{tasks.filter((task) => task.taskStatus === 'working').length}</span><p>В работе</p></div><div><span>{tasks.filter((task) => task.taskStatus === 'done').length}</span><p>Завершено</p></div><div><span>{tasks.length}</span><p>Всего задач</p></div></section></div></main>
}
export default ProfilePage

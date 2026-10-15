import { useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import UserAvatar from '../../components/UserAvatar/UserAvatar'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import { loadTeamRequest } from '../../store/slices/teamSlice'

const MemberProfilePage = () => {
  const { userId } = useParams()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const currentUser = useAppSelector((state) => state.auth.user)!
  const team = useAppSelector((state) => state.team)
  const tasks = useAppSelector((state) => state.tasks.items)
  const member = team.members.find((item) => item.id === Number(userId))
  const profileUser = member?.id === currentUser.id ? { ...member, avatarUrl: currentUser.avatarUrl } : member

  useEffect(() => { if (!team.members.length) dispatch(loadTeamRequest()) }, [dispatch, team.members.length])
  useEffect(() => { if (!tasks.length) dispatch(loadTasksRequest()) }, [dispatch, tasks.length])

  if (team.loading) return <main className="member-profile-page"><div className="container"><div className="loader">Загружаем профиль…</div></div></main>
  if (!profileUser) return <main className="member-profile-page"><div className="container"><section className="not-found-card"><h1>Профиль не найден</h1><button className="button button--secondary" onClick={() => navigate('/members')}>К участникам</button></section></div></main>

  const memberTasks = tasks.filter((task) => task.assignee === profileUser.name)
  return <main className="member-profile-page"><div className="container"><button className="back-link" onClick={() => navigate(-1)}>← Назад</button><section className="public-profile"><div className="public-profile__hero"><UserAvatar user={profileUser} size="xl" /><div><span className={`status-badge ${profileUser.active ? 'is-active' : ''}`}>{profileUser.active ? 'Активен' : 'Доступ закрыт'}</span><h1>{profileUser.name}</h1><p>{profileUser.position}</p></div></div><div className="public-profile__details"><div><span>Логин</span><strong>@{profileUser.login}</strong></div><div><span>Роль</span><strong>{profileUser.role === 'lead' ? 'Тимлид' : 'Участник'}</strong></div><div><span>В команде с</span><strong>{new Date(profileUser.joinedAt).toLocaleDateString('ru-RU')}</strong></div></div></section><section className="profile-stats public-profile__stats"><div><span>{memberTasks.filter((task) => task.taskStatus === 'working').length}</span><p>В работе</p></div><div><span>{memberTasks.filter((task) => task.taskStatus === 'checking').length}</span><p>На проверке</p></div><div><span>{memberTasks.filter((task) => task.taskStatus === 'done').length}</span><p>Выполнено</p></div></section>{memberTasks.length > 0 && <section className="public-profile__tasks"><h2>Последние задачи</h2>{memberTasks.slice(0, 4).map((task) => <div key={task.taskId}><span>#{task.taskId}</span><strong>{task.taskName}</strong><small>{task.taskStatus === 'done' ? 'Выполнено' : task.taskStatus === 'checking' ? 'На проверке' : task.taskStatus === 'working' ? 'В работе' : 'Ожидание'}</small></div>)}</section>}</div></main>
}

export default MemberProfilePage

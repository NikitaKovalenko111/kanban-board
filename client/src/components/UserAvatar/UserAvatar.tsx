import type { User } from '../../types'

interface Props {
  user: User
  size?: 'default' | 'large' | 'xl'
}

const UserAvatar = ({ user, size = 'default' }: Props) => {
  const initials = user.name.split(' ').map((part) => part[0]).join('')
  const className = `avatar ${size !== 'default' ? `avatar--${size}` : ''}`
  return <span className={className}>{user.avatarUrl ? <img src={user.avatarUrl} alt={`Аватар ${user.name}`} /> : initials}</span>
}

export default UserAvatar

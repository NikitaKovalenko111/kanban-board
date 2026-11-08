import type { User } from '../../types'
import { useEffect, useState } from 'react'
import { isBackendMode } from '../../api/config'
import { backendHttp } from '../../api/backendApi'

interface Props {
  user: User
  size?: 'default' | 'large' | 'xl'
}

const UserAvatar = ({ user, size = 'default' }: Props) => {
  const [image, setImage] = useState<string>()
  useEffect(() => {
    if (!isBackendMode || !user.avatarUrl) { setImage(undefined); return }
    let cancelled = false
    let objectUrl: string | undefined
    setImage(undefined)
    void backendHttp.get<Blob>(`/users/${encodeURIComponent(String(user.id))}/photo`, { responseType: 'blob' }).then(response => {
      if (!cancelled) { objectUrl = URL.createObjectURL(response.data); setImage(objectUrl) }
    }).catch(() => { /* Keep initials when a photo is unavailable. */ })
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [user.id, user.avatarUrl])
  const initials = user.name.split(' ').map((part) => part[0]).join('')
  const className = `avatar ${size !== 'default' ? `avatar--${size}` : ''}`
  const source = isBackendMode ? image : user.avatarUrl
  return <span className={className}>{source ? <img src={source} alt={`Аватар ${user.name}`} /> : initials}</span>
}

export default UserAvatar

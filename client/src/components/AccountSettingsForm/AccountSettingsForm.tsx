import { useState, type FormEvent } from 'react'
import type { StudyGroup, User } from '../../types'
import { mockApi } from '../../api/mockApi'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loginSuccess } from '../../store/slices/authSlice'
import { updateMemberSuccess } from '../../store/slices/teamSlice'

export default function AccountSettingsForm({ user }: { user: User }) {
  const dispatch = useAppDispatch()
  const currentId = useAppSelector(state => state.auth.user?.id)
  const [login, setLogin] = useState(user.login)
  const [group, setGroup] = useState<StudyGroup | ''>(user.studentGroup ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving) return
    setSaving(true); setError(''); setSaved(false)
    try {
      const updated = await mockApi.updateAccount(user.id, { login, studentGroup: group || undefined })
      dispatch(updateMemberSuccess(updated))
      if (currentId === updated.id) dispatch(loginSuccess(updated))
      setLogin(updated.login); setSaved(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Не удалось сохранить аккаунт') }
    finally { setSaving(false) }
  }
  return <form className="management-form" onSubmit={submit}>
    <label>Логин<input required pattern="[a-zA-Z0-9._-]+" autoComplete="username" value={login} onChange={event => { setLogin(event.target.value); setSaved(false) }} /></label>
    <label>Учебная группа<select value={group} onChange={event => { setGroup(event.target.value as StudyGroup | ''); setSaved(false) }}><option value="">Не указана</option><option value="203">203</option><option value="204">204</option></select></label>
    {error && <p className="error-banner" role="alert">{error}</p>}
    {saved && <p role="status">Данные аккаунта сохранены</p>}
    <button className="button" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить аккаунт'}</button>
  </form>
}

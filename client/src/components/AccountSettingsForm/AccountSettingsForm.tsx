import { useEffect, useState, type FormEvent } from 'react'
import type { StudyGroup, User } from '../../types'
import { api } from '../../api'
import { isBackendMode } from '../../api/config'
import { realApi } from '../../api/realApi'
import type { GroupDto } from '../../api/contracts'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loginSuccess } from '../../store/slices/authSlice'
import { updateMemberSuccess } from '../../store/slices/teamSlice'

export default function AccountSettingsForm({ user }: { user: User }) {
  const dispatch = useAppDispatch()
  const currentId = useAppSelector(state => state.auth.user?.id)
  const editable = !isBackendMode || user.id === currentId
  const [groups, setGroups] = useState<GroupDto[]>([])
  const [login, setLogin] = useState(user.login)
  const [group, setGroup] = useState<StudyGroup | ''>(user.studentGroup ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  useEffect(() => {
    if (!isBackendMode || !editable) return
    let active = true
    void realApi.getGroups().then(result => { if (active) setGroups(result) }).catch(reason => { if (active) setError(reason.message) })
    return () => { active = false }
  }, [editable])
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving) return
    setSaving(true); setError(''); setSaved(false)
    try {
      const updated = await api.updateAccount(user.id, { login, studentGroup: group || undefined })
      dispatch(updateMemberSuccess(updated))
      if (currentId === updated.id) dispatch(loginSuccess(updated))
      setLogin(updated.login); setSaved(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Не удалось сохранить аккаунт') }
    finally { setSaving(false) }
  }
  return <form className="management-form" onSubmit={submit}>
    <label>Логин<input required readOnly={isBackendMode} title={isBackendMode ? 'Изменение логина пока недоступно' : undefined} pattern="[a-zA-Z0-9._-]+" autoComplete="username" value={login} onChange={event => { setLogin(event.target.value); setSaved(false) }} /></label>
    <label>Учебная группа<select disabled={!editable} value={group} onChange={event => { setGroup(event.target.value as StudyGroup | ''); setSaved(false) }}><option value="">Не указана</option>{isBackendMode ? <>{group && !groups.some(item => item.groupCode === group) && <option value={group}>{group}</option>}{groups.map(item => <option value={item.groupCode} key={item.id}>{item.name}</option>)}</> : <><option value="203">203</option><option value="204">204</option></>}</select></label>
    {error && <p className="error-banner" role="alert">{error}</p>}
    {saved && <p role="status">Данные аккаунта сохранены</p>}
    {editable && <button className="button" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить аккаунт'}</button>}
  </form>
}

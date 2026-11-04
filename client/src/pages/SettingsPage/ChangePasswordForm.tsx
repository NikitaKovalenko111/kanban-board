import { useState, type FormEvent } from 'react'
import { mockApi } from '../../api/mockApi'

const ChangePasswordForm = () => {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving) return
    setError(''); setSuccess(false)
    if (newPassword !== confirmation) { setError('Новые пароли не совпадают'); return }
    if (newPassword.length < 6) { setError('Новый пароль должен содержать не менее 6 символов'); return }
    if (currentPassword === newPassword) { setError('Новый пароль должен отличаться от текущего'); return }
    setSaving(true)
    try {
      await mockApi.changePassword({ currentPassword, newPassword })
      setCurrentPassword(''); setNewPassword(''); setConfirmation(''); setSuccess(true)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Не удалось изменить пароль')
    } finally { setSaving(false) }
  }

  return <section className="panel change-password">
    <h2>Смена пароля</h2>
    <form className="management-form" onSubmit={(event) => void submit(event)}>
      <label>Текущий пароль<input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => { setCurrentPassword(event.target.value); setError(''); setSuccess(false) }} disabled={saving} required /></label>
      <label>Новый пароль<input type="password" autoComplete="new-password" minLength={6} value={newPassword} onChange={(event) => { setNewPassword(event.target.value); setError(''); setSuccess(false) }} disabled={saving} required /></label>
      <label>Подтвердите новый пароль<input type="password" autoComplete="new-password" minLength={6} value={confirmation} onChange={(event) => { setConfirmation(event.target.value); setError(''); setSuccess(false) }} disabled={saving} required /></label>
      {error && <div className="error-banner" role="alert">{error}</div>}
      {success && <p className="password-success" role="status">Пароль успешно изменён</p>}
      <button className="button" disabled={saving}>{saving ? 'Сохраняем…' : 'Изменить пароль'}</button>
    </form>
  </section>
}

export default ChangePasswordForm

import Icon from '../../components/Icon/Icon'
import { useEffect, useState, type FormEvent } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import FormModal from '../../components/FormModal/FormModal'
import { loadEventTypes, saveEventTypes, type EventTypeOption } from '../../utils/eventTypes'

import { mockApi } from '../../api/mockApi'
import { loginSuccess } from '../../store/slices/authSlice'
import type { EventPrivacy } from '../../types'
import ReminderEditor from '../../components/ReminderEditor/ReminderEditor'
import { reminderSummary, validateReminderMinutes } from '../../utils/reminders'
import ChangePasswordForm from './ChangePasswordForm'

const SettingsPage = () => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const [section, setSection] = useState<'user' | 'site'>('user')
  const [privacy, setPrivacy] = useState<EventPrivacy>(user.eventPrivacy ?? 'busy')
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') ?? 'light')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem('theme', theme) }, [theme])
  const savePrivacy = async () => {
    setSaving(true); setMessage('')
    try { dispatch(loginSuccess(await mockApi.updatePrivacy(privacy))); setMessage('Сохранено') }
    catch (reason) { setMessage(reason instanceof Error ? reason.message : 'Не удалось сохранить') }
    finally { setSaving(false) }
  }
  const canManage = useAppSelector((state) => state.auth.user?.role === 'lead')
  const [types, setTypes] = useState(loadEventTypes)
  const [editing, setEditing] = useState<EventTypeOption | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [color, setColor] = useState('#6577df')

  const [reminderMinutes, setReminderMinutes] = useState<number[]>([15])
  const [typeError, setTypeError] = useState('')

  const openCreate = () => { setEditing(null); setName(''); setColor('#6577df'); setReminderMinutes([15]); setTypeError(''); setShowForm(true) }
  const openEdit = (item: EventTypeOption) => { setEditing(item); setName(item.label); setColor(item.color); setReminderMinutes(item.reminderMinutes ?? []); setTypeError(''); setShowForm(true) }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!canManage || !name.trim()) return
    let minutes: number[]
    try { minutes = validateReminderMinutes(reminderMinutes) } catch (reason) { setTypeError((reason as Error).message); return }
    const next = editing ? types.map((item) => item.id === editing.id ? { ...item, label: name.trim(), color, reminderMinutes: minutes } : item) : [...types, { id: `custom-${Date.now()}`, label: name.trim(), color, reminderMinutes: minutes }]
    setTypes(next); saveEventTypes(next); setShowForm(false)
  }
  const remove = (id: string) => { const next = types.filter((item) => item.id !== id); setTypes(next); saveEventTypes(next) }

  return <main className="settings-page"><div className="container"><div className="page-heading"><div><h1>Настройки</h1></div>{canManage && section === 'site' && <button className="button" onClick={openCreate}><Icon name="plus" /> Новый тип события</button>}</div><div className="project-page-tabs" role="tablist" aria-label="Раздел настроек"><button role="tab" aria-selected={section === 'user'} className={section === 'user' ? 'is-active' : ''} onClick={() => setSection('user')}>Настройки пользователя</button><button role="tab" aria-selected={section === 'site'} className={section === 'site' ? 'is-active' : ''} onClick={() => setSection('site')}>Настройки сайта</button></div>
    {section === 'user' && <><section className="panel"><h2>Личные события</h2><div className="management-form"><label>Что видят другие участники<select value={privacy} onChange={(event) => setPrivacy(event.target.value as EventPrivacy)}><option value="hidden">Ничего</option><option value="busy">Только «Занят»</option><option value="title">Название события</option><option value="details">Название и описание</option></select></label><button className="button" disabled={saving} onClick={() => void savePrivacy()}>Сохранить</button>{message && <p role="status">{message}</p>}</div></section><ChangePasswordForm /></>}
    {section === 'site' && <><section className="panel theme-panel"><h2>Тема</h2><label><input type="checkbox" checked={theme === 'dark'} onChange={(event) => setTheme(event.target.checked ? 'dark' : 'light')} /> Тёмный режим</label></section><section className="panel event-types-panel"><div className="panel__heading"><div><h2>Типы событий</h2></div></div><div className="event-types-list">{types.map((item) => <article key={item.id}><i style={{ background: item.color }} /><span><strong>{item.label}</strong><small>{reminderSummary(item.reminderMinutes ?? [])}</small></span>{canManage && <><button onClick={() => openEdit(item)}>Изменить</button><button className="is-danger" onClick={() => remove(item.id)} disabled={types.length === 1}>Удалить</button></>}</article>)}</div></section></>}</div>{showForm && canManage && <FormModal title={editing ? 'Изменить тип события' : 'Новый тип события'} onClose={() => setShowForm(false)}><form className="management-form" onSubmit={submit}><label>Название<input value={name} onChange={(event) => setName(event.target.value)} required /></label><label>Цвет<input className="event-color-input" type="color" value={color} onChange={(event) => setColor(event.target.value)} /></label><ReminderEditor value={reminderMinutes} onChange={setReminderMinutes} />{typeError && <p className="error-banner" role="alert">{typeError}</p>}<button className="button">{editing ? 'Сохранить' : 'Создать тип'}</button></form></FormModal>}</main>
}

export default SettingsPage

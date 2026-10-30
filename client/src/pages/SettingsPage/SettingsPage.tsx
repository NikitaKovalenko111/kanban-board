import { useState, type FormEvent } from 'react'
import { useAppSelector } from '../../store/hooks'
import FormModal from '../../components/FormModal/FormModal'
import { loadEventTypes, saveEventTypes, type EventTypeOption } from '../../utils/eventTypes'

const SettingsPage = () => {
  const canManage = useAppSelector((state) => state.auth.user?.role === 'lead')
  const [types, setTypes] = useState(loadEventTypes)
  const [editing, setEditing] = useState<EventTypeOption | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [color, setColor] = useState('#6577df')

  const openCreate = () => { setEditing(null); setName(''); setColor('#6577df'); setShowForm(true) }
  const openEdit = (item: EventTypeOption) => { setEditing(item); setName(item.label); setColor(item.color); setShowForm(true) }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!canManage || !name.trim()) return
    const next = editing ? types.map((item) => item.id === editing.id ? { ...item, label: name.trim(), color } : item) : [...types, { id: `custom-${Date.now()}`, label: name.trim(), color }]
    setTypes(next); saveEventTypes(next); setShowForm(false)
  }
  const remove = (id: string) => { const next = types.filter((item) => item.id !== id); setTypes(next); saveEventTypes(next) }

  return <main className="settings-page"><div className="container"><div className="page-heading"><div><span className="eyebrow">Конфигурация</span><h1>Настройки</h1></div>{canManage && <button className="button" onClick={openCreate}>＋ Новый тип события</button>}</div><section className="panel event-types-panel"><div className="panel__heading"><div><h2>Типы событий</h2><p>Название и цвет используются в расписании</p></div></div><div className="event-types-list">{types.map((item) => <article key={item.id}><i style={{ background: item.color }} /><span><strong>{item.label}</strong><small>{item.builtIn ? 'Базовый тип' : 'Пользовательский тип'}</small></span>{canManage && <><button onClick={() => openEdit(item)}>Изменить</button><button className="is-danger" onClick={() => remove(item.id)} disabled={types.length === 1}>Удалить</button></>}</article>)}</div></section></div>{showForm && canManage && <FormModal title={editing ? 'Изменить тип события' : 'Новый тип события'} onClose={() => setShowForm(false)}><form className="management-form" onSubmit={submit}><label>Название<input value={name} onChange={(event) => setName(event.target.value)} required /></label><label>Цвет<input className="event-color-input" type="color" value={color} onChange={(event) => setColor(event.target.value)} /></label><button className="button">{editing ? 'Сохранить' : 'Создать тип'}</button></form></FormModal>}</main>
}

export default SettingsPage

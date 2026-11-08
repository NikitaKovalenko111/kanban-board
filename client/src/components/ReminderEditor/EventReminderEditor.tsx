import { isBackendMode } from '../../api/config'
import type { EventReminders } from '../../types'
import { reminderSummary } from '../../utils/reminders'
import ReminderEditor from './ReminderEditor'

interface Props { value: EventReminders; defaults: number[]; existing?: boolean; onChange: (value: EventReminders) => void }

export default function EventReminderEditor({ value, defaults, onChange, existing = false }: Props) {
  return <div className="event-reminders">
    <label className="event-reminders__inherit"><input type="checkbox" checked={value.reminderMode === 'type'} onChange={(event) => onChange({ ...value, reminderMode: event.target.checked ? 'type' : 'custom' })} />{isBackendMode && existing ? 'Вернуть исходные напоминания события' : 'Использовать напоминания типа'}</label>
    {value.reminderMode === 'type' ? <p className="event-reminders__summary">{isBackendMode && existing ? 'Будут восстановлены интервалы, сохранённые при создании события.' : reminderSummary(defaults)}</p> : <ReminderEditor value={value.reminderMinutes} onChange={(reminderMinutes) => onChange({ ...value, reminderMinutes })} />}
  </div>
}

import type { EventReminders } from '../../types'
import { reminderSummary } from '../../utils/reminders'
import ReminderEditor from './ReminderEditor'

interface Props { value: EventReminders; defaults: number[]; onChange: (value: EventReminders) => void }

export default function EventReminderEditor({ value, defaults, onChange }: Props) {
  return <div className="event-reminders">
    <label className="event-reminders__inherit"><input type="checkbox" checked={value.reminderMode === 'type'} onChange={(event) => onChange({ ...value, reminderMode: event.target.checked ? 'type' : 'custom' })} />Использовать напоминание типа</label>
    {value.reminderMode === 'type' ? <p className="event-reminders__summary">{reminderSummary(defaults)}</p> : <ReminderEditor value={value.reminderMinutes} onChange={(reminderMinutes) => onChange({ ...value, reminderMinutes })} />}
  </div>
}

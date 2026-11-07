import { isBackendMode } from '../../api/config'
import Icon from '../Icon/Icon'
interface Props { value: number[]; onChange: (minutes: number[]) => void }

export default function ReminderEditor({ value, onChange }: Props) {
  return <fieldset className="reminder-editor"><legend>Напоминания</legend>
    <label className="reminder-editor__enabled"><input type="checkbox" checked={value.length > 0} onChange={(event) => onChange(event.target.checked ? [15] : [])} />Напомнить о событии</label>
    {value.map((minutes, index) => <div className="reminder-editor__row" key={index}>
      <label>За сколько минут<input aria-label={index === 0 ? 'За сколько минут напомнить' : `За сколько минут напомнить (${index + 1})`} type="number" min={isBackendMode ? 1 : 0} max={isBackendMode ? 40320 : undefined} step="1" required value={Number.isNaN(minutes) ? '' : minutes} onChange={(event) => onChange(value.map((minute, position) => position === index ? event.target.valueAsNumber : minute))} /><small>{isBackendMode ? 'От 1 до 40320 минут' : '0 — в момент начала'}</small></label>
      <button type="button" className="icon-button" aria-label={`Удалить напоминание ${index + 1}`} onClick={() => onChange(value.filter((_, position) => position !== index))}><Icon name="trash" /></button>
    </div>)}
    {value.length > 0 && <button type="button" className="text-button" onClick={() => { let minute = 15; while (value.includes(minute)) minute += 15; onChange([...value, minute]) }}><Icon name="plus" /> Добавить напоминание</button>}
  </fieldset>
}

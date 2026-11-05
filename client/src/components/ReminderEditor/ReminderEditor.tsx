import { isBackendMode } from '../../api/config'
interface Props { value: number[]; onChange: (minutes: number[]) => void }

export default function ReminderEditor({ value, onChange }: Props) {
  return <fieldset className="reminder-editor"><legend>Напоминание</legend>
    <label className="reminder-editor__enabled"><input type="checkbox" checked={value.length > 0} onChange={(event) => onChange(event.target.checked ? [15] : [])} />Напомнить о событии</label>
    {value.length > 0 && <div className="reminder-editor__row">
      <label>За сколько минут<input aria-label="За сколько минут напомнить" type="number" min={isBackendMode ? 1 : 0} max={isBackendMode ? 40320 : undefined} step="1" required value={Number.isNaN(value[0]) ? '' : value[0]} onChange={(event) => onChange([event.target.valueAsNumber])} /><small>{isBackendMode ? 'От 1 до 40320 минут' : '0 — в момент начала'}</small></label>
    </div>}
  </fieldset>
}

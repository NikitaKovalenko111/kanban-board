import Icon from '../Icon/Icon'

interface Props { value: number[]; onChange: (minutes: number[]) => void }

export default function ReminderEditor({ value, onChange }: Props) {
  return <fieldset className="reminder-editor"><legend>Напоминания</legend>
    <label className="reminder-editor__enabled"><input type="checkbox" checked={value.length > 0} onChange={(event) => onChange(event.target.checked ? [15] : [])} />Напомнить о событии</label>
    {value.map((minutes, index) => <div className="reminder-editor__row" key={index}>
      <label>За сколько минут<input aria-label={`За сколько минут напомнить ${index + 1}`} type="number" min="0" step="1" required value={Number.isNaN(minutes) ? '' : minutes} onChange={(event) => onChange(value.map((current, i) => i === index ? event.target.valueAsNumber : current))} /><small>0 — в момент начала</small></label>
      <button className="icon-button" type="button" aria-label={`Удалить напоминание ${index + 1}`} onClick={() => onChange(value.filter((_, i) => i !== index))}><Icon name="trash" /></button>
    </div>)}
    <button className="button button--secondary" type="button" onClick={() => { let minutes = 15; while (value.includes(minutes)) minutes += 15; onChange([...value, minutes]) }}><Icon name="plus" /> Добавить напоминание</button>
  </fieldset>
}

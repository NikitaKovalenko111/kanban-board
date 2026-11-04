import Icon from '../Icon/Icon'
interface Props { value: string; onChange: (value: string) => void; placeholder?: string }
const SearchField = ({ value, onChange, placeholder = 'Поиск' }: Props) => <label className="search">
  <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
  <input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={(event) => onChange(event.target.value)} />
  {value && <button type="button" onClick={() => onChange('')} aria-label="Очистить поиск"><Icon name="close" /></button>}
</label>
export default SearchField

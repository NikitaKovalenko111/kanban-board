type Name = 'plus' | 'close' | 'arrow-left' | 'arrow-right' | 'arrow-down' | 'trash'
const paths: Record<Name, string> = {
  plus: 'M12 5v14M5 12h14',
  close: 'm6 6 12 12M18 6 6 18',
  'arrow-left': 'M20 12H4m7-7-7 7 7 7',
  'arrow-right': 'M4 12h16m-7-7 7 7-7 7',
  'arrow-down': 'M12 4v16m-7-7 7 7 7-7',
  trash: 'M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5',
}
const Icon = ({ name }: { name: Name }) => <svg className="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={paths[name]} /></svg>
export default Icon

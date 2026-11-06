type Name = 'plus' | 'close' | 'arrow-left' | 'arrow-right' | 'arrow-down' | 'arrow-up-right' | 'trash' | 'settings'
const paths: Record<Name, string> = {
  plus: 'M12 5v14M5 12h14',
  close: 'm6 6 12 12M18 6 6 18',
  'arrow-left': 'M20 12H4m7-7-7 7 7 7',
  'arrow-right': 'M4 12h16m-7-7 7 7-7 7',
  'arrow-down': 'M12 4v16m-7-7 7 7 7-7',
  'arrow-up-right': 'M6 18 18 6M6 6h12v12',
  trash: 'M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5',
  settings: 'm9.5 3-.6 2.2-1.8 1.1L5 5.7 2.5 10l1.7 1.5v2L2.5 15 5 19.3l2.1-.6 1.8 1.1.6 2.2h5l.6-2.2 1.8-1.1 2.1.6 2.5-4.3-1.7-1.5v-2l1.7-1.5L19 5.7l-2.1.6-1.8-1.1L14.5 3h-5ZM15 12.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
}
const Icon = ({ name }: { name: Name }) => <svg className="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={paths[name]} /></svg>
export default Icon

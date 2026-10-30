import FormModal from '../FormModal/FormModal'

export interface NotificationItem { id: string; title: string; text: string; createdAt: string; href: string }
interface Props { items: NotificationItem[]; readIds: string[]; onRead: (id: string) => void; onReadAll: () => void; onClose: () => void; onOpen: (href: string) => void }

const NotificationsModal = ({ items, readIds, onRead, onReadAll, onClose, onOpen }: Props) => <FormModal title="Уведомления" className="notifications-modal" onClose={onClose}>
  <div className="notifications-toolbar"><span>{items.filter((item) => !readIds.includes(item.id)).length} непрочитанных</span><button className="text-button" onClick={onReadAll}>Прочитать все</button></div>
  <div className="notifications-list">{items.length ? items.map((item) => <article className={readIds.includes(item.id) ? 'is-read' : ''} key={item.id}>
    <div><strong>{item.title}</strong><time>{new Date(item.createdAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })}</time></div>
    <p>{item.text}</p>
    <div><button className="text-button" onClick={() => { onRead(item.id); onOpen(item.href) }}>Открыть</button>{!readIds.includes(item.id) && <button className="text-button" onClick={() => onRead(item.id)}>Прочитано</button>}</div>
  </article>) : <div className="empty-state">Новых уведомлений пока нет</div>}</div>
</FormModal>

export default NotificationsModal

import FormModal from '../FormModal/FormModal'

export interface NotificationItem { id: string; title: string; text: string; createdAt: string; href: string }
interface Props { items: NotificationItem[]; readIds: string[]; onClose: () => void; onOpen: (item: NotificationItem) => void; onReadAll: () => void }

const NotificationsModal = ({ items, readIds, onClose, onOpen, onReadAll }: Props) => <FormModal title="Уведомления" className="notifications-modal" onClose={onClose}>
  <div className="notifications-toolbar"><button type="button" className="text-button notifications-read-all" onClick={onReadAll} disabled={items.every(item => readIds.includes(item.id))}>Прочитать все</button></div>
  <div className="notifications-list">{items.length ? items.map((item) => <article className={readIds.includes(item.id) ? 'is-read' : ''} key={item.id}>
    <div><strong>{item.title}</strong><time>{new Date(item.createdAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })}</time></div>
    <p>{item.text}</p>
    <button className="text-button" onClick={() => onOpen(item)}>Открыть</button>
  </article>) : <div className="empty-state">Уведомлений пока нет</div>}</div>
</FormModal>

export default NotificationsModal

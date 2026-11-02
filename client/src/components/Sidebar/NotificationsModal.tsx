import FormModal from '../FormModal/FormModal'

export interface NotificationItem { id: string; title: string; text: string; createdAt: string; href: string }
interface Props { items: NotificationItem[]; readIds: string[]; onClose: () => void; onOpen: (href: string) => void }

const NotificationsModal = ({ items, readIds, onClose, onOpen }: Props) => <FormModal title="Уведомления" className="notifications-modal" onClose={onClose}>
  <div className="notifications-list">{items.length ? items.map((item) => <article className={readIds.includes(item.id) ? 'is-read' : ''} key={item.id}>
    <div><strong>{item.title}</strong><time>{new Date(item.createdAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })}</time></div>
    <p>{item.text}</p>
    <button className="text-button" onClick={() => onOpen(item.href)}>Открыть</button>
  </article>) : <div className="empty-state">Уведомлений пока нет</div>}</div>
</FormModal>

export default NotificationsModal

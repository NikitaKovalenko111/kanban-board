import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Props { title: string; children: ReactNode; onClose: () => void; className?: string }

const FormModal = ({ title, children, onClose, className = '' }: Props) => {
  useEffect(() => {
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', close)
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown', close) }
  }, [onClose])

  return createPortal(<div className={`form-modal ${className}`} role="dialog" aria-modal="true" aria-label={title}><button className="form-modal__backdrop" onClick={onClose} aria-label="Закрыть окно" /><section className="form-modal__panel"><header><h2>{title}</h2><button type="button" className="icon-button" onClick={onClose} aria-label="Закрыть">×</button></header>{children}</section></div>, document.body)
}

export default FormModal

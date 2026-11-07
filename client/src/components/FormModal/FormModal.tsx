import { useModal } from './useModal'
import Icon from '../Icon/Icon'
import { useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Props { title: string; children: ReactNode; onClose: () => void; className?: string }

const FormModal = ({ title, children, onClose, className = '' }: Props) => {
  const panelRef = useRef<HTMLElement>(null)
  useModal(panelRef, onClose)

  return createPortal(<div className={`form-modal ${className}`} role="dialog" aria-modal="true" aria-label={title}><button className="form-modal__backdrop" tabIndex={-1} onClick={onClose} aria-label="Закрыть окно" /><section className="form-modal__panel" ref={panelRef} tabIndex={-1}><header><h2>{title}</h2><button type="button" className="icon-button" onClick={onClose} aria-label="Закрыть"><Icon name="close" /></button></header>{children}</section></div>, document.body)
}

export default FormModal

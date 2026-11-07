import { useCallback, useEffect, useState, type FormEvent } from 'react'
import type { Attachment } from '../../types'
import { createLinkAttachment } from '../../api/attachments'
import FormModal from '../FormModal/FormModal'
import Icon from '../Icon/Icon'

interface Props { onAdd: (link: Attachment) => void; disabled?: boolean }

export default function LinkAttachment({ onAdd, disabled }: Props) {
  const [open, setOpen] = useState(false)
  const [url, setUrl] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const close = useCallback(() => { setOpen(false); setUrl(''); setName(''); setError('') }, [])

  useEffect(() => {
    if (!open) return
    // Escape closes this window without closing the task or project underneath it.
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.stopImmediatePropagation()
      close()
    }
    document.addEventListener('keydown', handleEscape, true)
    return () => document.removeEventListener('keydown', handleEscape, true)
  }, [open, close])

  const add = (event: FormEvent) => {
    event.preventDefault()
    event.stopPropagation()
    if (disabled) return
    try { const link = createLinkAttachment(url, name); onAdd(link); close() }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Проверьте ссылку') }
  }

  return <div className="link-attachment">
    <button className="button" type="button" disabled={disabled} onClick={() => setOpen(true)} aria-expanded={open} aria-haspopup="dialog"><Icon name="plus" /> Добавить ссылку</button>
    {open && <FormModal title="Добавить ссылку" className="link-attachment-modal" onClose={close}>
      <form className="management-form" onSubmit={add}>
        <label>Ссылка<input autoFocus type="url" required placeholder="https://…" value={url} onChange={event => setUrl(event.target.value)} /></label>
        <label>Название ссылки<input value={name} onChange={event => setName(event.target.value)} placeholder="Необязательно" /></label>
        {error && <p className="error-banner" role="alert">{error}</p>}
        <div className="task-details__actions"><button className="button button--secondary" type="button" onClick={close}>Отмена</button><button className="button" type="submit" disabled={disabled || !url.trim()}>Прикрепить ссылку</button></div>
      </form>
    </FormModal>}
  </div>
}

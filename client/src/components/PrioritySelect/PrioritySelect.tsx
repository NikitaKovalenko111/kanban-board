import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import type { TaskPriority } from '../../types'
import { priorityLabels } from '../../utils/taskPriority'

interface Props { value: TaskPriority; onChange: (value: TaskPriority) => void; disabled?: boolean; compactLabel?: boolean }
const priorities: TaskPriority[] = ['low', 'medium', 'high']

export default function PrioritySelect({ value, onChange, disabled, compactLabel }: Props) {
  const id = useId()
  const root = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(priorities.indexOf(value))

  useEffect(() => {
    if (!open || disabled) return
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    const escape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      event.stopImmediatePropagation()
      setOpen(false)
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape, true)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape, true) }
  }, [open, disabled])

  const choose = (priority: TaskPriority) => { setOpen(false); onChange(priority) }
  const keyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      event.preventDefault()
      setActive(event.key === 'Home' ? 0 : event.key === 'End' ? priorities.length - 1 : !open ? priorities.indexOf(value) : (active + (event.key === 'ArrowDown' ? 1 : -1) + priorities.length) % priorities.length)
      setOpen(true)
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      if (open) choose(priorities[active])
      else { setActive(priorities.indexOf(value)); setOpen(true) }
    }
  }

  return <div className="task-priority-field">
    <span className={compactLabel ? 'eyebrow' : undefined} id={`${id}-label`}>Приоритет</span>
    <div className="priority-select" ref={root}>
      <button type="button" className="priority-select__trigger" role="combobox" aria-labelledby={`${id}-label`} aria-haspopup="listbox" aria-expanded={open && !disabled} aria-controls={open && !disabled ? `${id}-list` : undefined} aria-activedescendant={open && !disabled ? `${id}-${priorities[active]}` : undefined} disabled={disabled} onBlur={() => setOpen(false)} onKeyDown={keyDown} onClick={() => { setActive(priorities.indexOf(value)); setOpen(!open) }}>
        <span className={`priority-select__dot priority-select__dot--${value}`} /><span>{priorityLabels[value]}</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && !disabled && <div className="priority-select__menu" id={`${id}-list`} role="listbox" aria-labelledby={`${id}-label`}>
        {priorities.map((priority, index) => <div role="option" aria-selected={priority === value} id={`${id}-${priority}`} key={priority} className={`priority-select__option ${active === index ? 'is-active' : ''}`} onPointerMove={() => setActive(index)} onMouseDown={event => event.preventDefault()} onClick={() => choose(priority)}>
          <span className={`priority-select__dot priority-select__dot--${priority}`} /><span>{priorityLabels[priority]}</span>
          {priority === value && <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6" /></svg>}
        </div>)}
      </div>}
    </div>
  </div>
}

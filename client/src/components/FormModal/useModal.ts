import { useEffect, useRef, type RefObject } from 'react'

const layers: HTMLElement[] = []
let originalOverflow = ''
const focusableSelector = 'a[href],button,input:not([type="hidden"]),select,textarea,[tabindex]'

function focusable(root: HTMLElement) {
  return [...root.querySelectorAll<HTMLElement>(focusableSelector)].filter(element => {
    const style = getComputedStyle(element)
    return element.tabIndex >= 0 && !element.matches(':disabled') && !element.closest('[hidden],[inert]') && style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0
  })
}

export function useModal(rootRef: RefObject<HTMLElement | null>, onClose: () => void, enabled = true) {
  const onCloseRef = useRef(onClose)
  const openerRef = useRef(typeof document === 'undefined' ? null : document.activeElement as HTMLElement | null)
  onCloseRef.current = onClose

  useEffect(() => {
    const root = rootRef.current
    if (!enabled || !root) return
    const opener = root.contains(document.activeElement) ? openerRef.current : document.activeElement as HTMLElement | null
    if (!layers.length) { originalOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
    layers.push(root)
    const isTop = () => layers.at(-1) === root
    const focusFirst = () => (focusable(root)[0] ?? root).focus({ preventScroll: true })
    if (!root.contains(document.activeElement)) focusFirst()

    const keyDown = (event: KeyboardEvent) => {
      if (!isTop()) return
      if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); onCloseRef.current(); return }
      if (event.key !== 'Tab') return
      const elements = focusable(root)
      if (!elements.length) { event.preventDefault(); root.focus(); return }
      const first = elements[0], last = elements.at(-1)!
      if (event.shiftKey && (document.activeElement === first || document.activeElement === root)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && (document.activeElement === last || !root.contains(document.activeElement))) { event.preventDefault(); first.focus() }
    }
    const keepFocus = () => { if (isTop() && root.isConnected && !root.contains(document.activeElement)) focusFirst() }
    document.addEventListener('keydown', keyDown)
    document.addEventListener('focusin', keepFocus)
    return () => {
      document.removeEventListener('keydown', keyDown)
      document.removeEventListener('focusin', keepFocus)
      const index = layers.indexOf(root)
      if (index >= 0) layers.splice(index, 1)
      if (!layers.length) document.body.style.overflow = originalOverflow
      if (opener?.isConnected && !opener.closest('[inert]') && (!layers.length || layers.at(-1)!.contains(opener))) opener.focus({ preventScroll: true })
    }
  }, [rootRef, enabled])
}

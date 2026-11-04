import { useEffect, useRef, useState, type CSSProperties, type TouchEvent, type TransitionEvent } from 'react'

export const shiftDate = (date: string, amount: number) => {
  const day = new Date(`${date}T12:00:00`)
  day.setDate(day.getDate() + amount)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

export function useDaySlider(date: string, onChange: (date: string) => void, contentKey: string) {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 760px)').matches)
  const [direction, setDirection] = useState(0)
  const [drag, setDrag] = useState(0)
  const [height, setHeight] = useState<number>()
  const start = useRef<{ x: number; y: number } | null>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const media = window.matchMedia('(max-width: 760px)')
    const resize = () => { setMobile(media.matches); setDirection(0); setDrag(0) }
    media.addEventListener('change', resize)
    return () => media.removeEventListener('change', resize)
  }, [])
  useEffect(() => { setDirection(0); setDrag(0); start.current = null }, [date, contentKey])
  useEffect(() => {
    if (!mobile) return
    const panel = viewportRef.current?.querySelector('.is-selected')
    if (!panel) return
    const observer = new ResizeObserver(() => setHeight(panel.getBoundingClientRect().height))
    observer.observe(panel)
    return () => observer.disconnect()
  }, [date, mobile, contentKey])

  const move = (amount: number) => {
    if (direction) return
    if (mobile) { setDrag(0); setDirection(amount) }
    else onChange(shiftDate(date, amount))
  }
  const onTouchStart = (event: TouchEvent) => {
    if (!mobile || direction) return
    const point = event.touches[0]
    start.current = { x: point.clientX, y: point.clientY }
  }
  const onTouchMove = (event: TouchEvent) => {
    if (!start.current || direction) return
    const point = event.touches[0]
    const x = point.clientX - start.current.x
    const y = point.clientY - start.current.y
    if (Math.abs(y) > Math.abs(x) && Math.abs(y) > 10) { start.current = null; setDrag(0); return }
    setDrag(Math.max(-window.innerWidth, Math.min(window.innerWidth, x)))
  }
  const onTouchEnd = (event: TouchEvent) => {
    if (!start.current) return
    const point = event.changedTouches[0]
    const x = point.clientX - start.current.x
    const y = point.clientY - start.current.y
    start.current = null
    setDrag(0)
    if (Math.abs(x) > 50 && Math.abs(x) > Math.abs(y) * 1.3) move(x < 0 ? 1 : -1)
  }
  const onTransitionEnd = (event: TransitionEvent) => {
    if (event.target !== event.currentTarget || event.propertyName !== 'transform' || !direction) return
    onChange(shiftDate(date, direction))
    setDirection(0)
  }
  return {
    mobile, move, viewportRef,
    days: [-1, 0, 1].map((amount) => shiftDate(date, amount)),
    className: `schedule-slider ${mobile ? 'is-mobile' : ''} ${direction ? 'is-animating' : ''}`,
    viewportStyle: mobile && height ? { height } : undefined,
    trackStyle: { '--day-shift': direction, '--day-drag': `${drag}px` } as CSSProperties,
    onTransitionEnd,
    handlers: { onTouchStart, onTouchMove, onTouchEnd, onTouchCancel: () => { start.current = null; setDrag(0) } },
  }
}

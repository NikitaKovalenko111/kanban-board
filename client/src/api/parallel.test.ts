import { expect, it, vi } from 'vitest'
import { mapLimited } from './parallel'

it('ограничивает одновременные запросы и сохраняет порядок результатов', async () => {
  vi.useFakeTimers()
  try {
    let active = 0, peak = 0
    const request = mapLimited([1, 2, 3, 4, 5], async value => {
      active++; peak = Math.max(peak, active)
      await new Promise(resolve => setTimeout(resolve, 10))
      active--; return value * 2
    }, 2)
    await vi.runAllTimersAsync()
    expect(await request).toEqual([2, 4, 6, 8, 10])
    expect(peak).toBe(2)
  } finally { vi.useRealTimers() }
})

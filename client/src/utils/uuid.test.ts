import { afterEach, describe, expect, it, vi } from 'vitest'
import { createUuid } from './uuid'
import { createLinkAttachment } from './attachmentLinks'

afterEach(() => vi.unstubAllGlobals())

describe('UUID по HTTP', () => {
  it('создаёт разные UUID v4 и вложения без crypto.randomUUID', () => {
    const getRandomValues = crypto.getRandomValues.bind(crypto)
    vi.stubGlobal('crypto', { getRandomValues })

    const ids = Array.from({ length: 100 }, () => createUuid())
    const link = createLinkAttachment('https://example.com/result', 'Результат')
    ids.push(String(link.id))

    expect(new Set(ids).size).toBe(ids.length)
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(link.url).toBe('https://example.com/result')
  })
})

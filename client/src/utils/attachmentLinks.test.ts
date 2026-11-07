import { describe, expect, it } from 'vitest'
import { createLinkAttachment } from './attachmentLinks'

describe('ссылки во вложениях', () => {
  it('сохраняет адрес и название, без файлового содержимого', () => {
    expect(createLinkAttachment(' https://example.com/result ', ' Результат ')).toMatchObject({ name: 'Результат', url: 'https://example.com/result', size: 0 })
    expect(createLinkAttachment('https://example.com').name).toBe('example.com')
  })
  it.each(['javascript:alert(1)', 'data:text/html,test', '/relative', 'https://user:password@example.com'])('отклоняет неподходящий URL: %s', url => {
    expect(() => createLinkAttachment(url)).toThrow()
  })
})

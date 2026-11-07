import type { Attachment } from '../types'

export const createLinkAttachment = (value: string, name = ''): Attachment => {
  let url: URL
  try { url = new URL(value.trim()) } catch { throw new Error('Укажите полную ссылку, начиная с https:// или http://') }
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) throw new Error('Допустимы только ссылки http:// и https:// без логина и пароля')
  return { id: crypto.randomUUID(), name: name.trim() || url.hostname, url: url.href, size: 0 }
}

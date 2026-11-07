import { mockApi } from './mockApi'
import type { Attachment } from '../types'

export const uploadFiles = async (files: FileList | File[]): Promise<Attachment[]> => {
  const selected = Array.from(files)
  if (selected.some((file) => file.size > 25 * 1024 * 1024)) throw new Error('Размер каждого файла не должен превышать 25 МБ')
  return Promise.all(selected.map((file) => mockApi.uploadAttachment(file)))
}
export { createLinkAttachment } from '../utils/attachmentLinks'
import { createLinkAttachment } from '../utils/attachmentLinks'
export const downloadFile = async (file: Attachment): Promise<void> => {
  if (file.url) {
    const safe = createLinkAttachment(file.url, file.name)
    window.open(safe.url, '_blank', 'noopener,noreferrer')
    return
  }
  const blob = await mockApi.downloadAttachment(file.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

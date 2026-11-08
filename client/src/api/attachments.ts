import { api } from './index'
import type { Attachment } from '../types'
import { isBackendMode } from './config'

export const uploadFiles = async (files: FileList | File[]): Promise<Attachment[]> => {
  const selected = Array.from(files)
  const limit = isBackendMode ? 10 : 25
  if (selected.some((file) => file.size > limit * 1024 * 1024)) throw new Error(`Размер каждого файла не должен превышать ${limit} МБ`)
  return Promise.all(selected.map((file) => api.uploadAttachment(file)))
}
export { createLinkAttachment } from '../utils/attachmentLinks'
import { createLinkAttachment } from '../utils/attachmentLinks'
export const downloadFile = async (file: Attachment): Promise<void> => {
  if (file.url) {
    const safe = createLinkAttachment(file.url, file.name)
    window.open(safe.url, '_blank', 'noopener,noreferrer')
    return
  }
  const blob = await api.downloadAttachment(file.downloadId ?? file.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

import { api } from './index'
import type { Attachment } from '../types'

export const uploadFiles = async (files: FileList | File[]): Promise<Attachment[]> => {
  const selected = Array.from(files)
  if (selected.some((file) => file.size > 25 * 1024 * 1024)) throw new Error('Размер каждого файла не должен превышать 25 МБ')
  return Promise.all(selected.map((file) => api.uploadAttachment(file)))
}
export const downloadFile = async (file: Attachment): Promise<void> => {
  const blob = await api.downloadAttachment(file.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

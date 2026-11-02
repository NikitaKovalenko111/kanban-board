const memoryFiles = new Map<string, Blob>()
const database = (): Promise<IDBDatabase> => new Promise((resolve, reject) => {
  const request = indexedDB.open('kanban-files', 1)
  request.onupgradeneeded = () => request.result.createObjectStore('files')
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(new Error('Не удалось открыть хранилище файлов'))
})

export async function saveFile(id: string, blob: Blob): Promise<void> {
  if (typeof indexedDB === 'undefined') { memoryFiles.set(id, blob); return }
  const db = await database()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction('files', 'readwrite')
      transaction.objectStore('files').put(blob, id)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(new Error('Не удалось сохранить файл. Проверьте свободное место'))
      transaction.onabort = () => reject(new Error('Сохранение файла прервано'))
    })
  } finally { db.close() }
}

export async function loadFile(id: string): Promise<Blob> {
  if (typeof indexedDB === 'undefined') {
    const file = memoryFiles.get(id)
    if (!file) throw new Error('Файл недоступен. Попросите автора загрузить его повторно')
    return file
  }
  const db = await database()
  try {
    return await new Promise<Blob>((resolve, reject) => {
      const request = db.transaction('files').objectStore('files').get(id)
      request.onsuccess = () => request.result ? resolve(request.result as Blob) : reject(new Error('Файл недоступен. Попросите автора загрузить его повторно'))
      request.onerror = () => reject(new Error('Не удалось прочитать файл'))
    })
  } finally { db.close() }
}

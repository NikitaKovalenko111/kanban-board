// Keep large boards within the backend connection pool and request timeout.
export async function mapLimited<T, R>(items: T[], map: (item: T) => Promise<R>, limit = 6): Promise<R[]> {
  const results: R[] = new Array(items.length)
  let next = 0
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) { const index = next++; results[index] = await map(items[index]) }
  }))
  return results
}

import type { EntityId } from '../types'
export const displayId = (id: EntityId) => typeof id === 'string' && id.length > 12 ? id.slice(0, 8) : String(id)

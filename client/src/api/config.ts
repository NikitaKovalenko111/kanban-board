const mode = import.meta.env.VITE_API_MODE ?? (import.meta.env.MODE === 'test' ? 'mock' : 'backend')
if (mode !== 'backend' && mode !== 'mock') throw new Error('VITE_API_MODE должен быть backend или mock')

export const isBackendMode = mode === 'backend'
export const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api/v1').replace(/\/$/, '').replace(/\/api$/, '/api/v1')

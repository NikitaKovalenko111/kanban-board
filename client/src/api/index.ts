import { isBackendMode } from './config'
import { mockApi } from './mockApi'
import { realApi } from './realApi'

export const api: typeof mockApi = isBackendMode ? realApi : mockApi

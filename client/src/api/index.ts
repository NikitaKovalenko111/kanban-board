import { isBackendMode } from './config'
import { mockApi } from './mockApi'

const unavailable = async (): Promise<never> => { throw new Error('Эта функция пока не поддерживается API сервера') }
// Prevent existing mock-only screens from silently using invented endpoints or demo data.
const unsupportedApi: typeof mockApi = {
  login: unavailable,
  updateEventReminders: unavailable,
  uploadAttachment: unavailable,
  resetPassword: unavailable,
  changePassword: unavailable,
  updatePrivacy: unavailable,
  getProjects: unavailable,
  createProject: unavailable,
  updateProject: unavailable,
  addProjectSources: unavailable,
  deleteProjectSource: unavailable,
  getSchedule: unavailable,
  createScheduleEvent: unavailable,
  downloadAttachment: unavailable,
  getTeam: unavailable,
  updateTeam: unavailable,
  createMember: unavailable,
  toggleMember: unavailable,
  getTasks: unavailable,
  createTask: unavailable,
  assignTask: unavailable,
  updateTaskStatus: unavailable,
  addTaskComment: unavailable,
}
export const api = isBackendMode ? unsupportedApi : mockApi

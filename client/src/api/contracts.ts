export interface ProfileDto { id: string; fullName: string; username: string; studentGroup: string; imageUrl: string | null; role: 'CAPTAIN' | 'MEMBER'; schedulePrivacy: 'NOTHING' | 'BUSY' | 'TITLE' | 'TITLE_AND_DESCRIPTION'; joinedAt: string; active: boolean }
export interface FileDto { id: string; name: string; sizeBytes: number; mimeType: string; downloadUrl: string }
export interface SourceDto { id: string; kind: 'FILE' | 'LINK'; title: string; url: string | null; file: FileDto | null; addedBy: string | null; addedAt: string }
export interface ProjectDto { id: string; name: string; description: string; members: { userId: string; role: string }[] | null; sources: SourceDto[] | null }
export interface ColumnDto { id: string; projectId: string; title: string; position: number; kind: 'BACKLOG' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'; color: string }
export interface AttachmentDto { id: string; kind: 'SOURCE' | 'RESULT'; uploadedBy: string; uploadedAt: string; file: FileDto }
export interface CommentDto { id: string; authorId: string; text: string; createdAt: string }
export interface HistoryDto { id: string; action: string; actorId: string | null; at: string; fromColumn?: string; toColumn?: string; comment?: string }
export interface TaskDto { id: string; projectId: string; columnId: string; position: number; title: string; description: string; priority: string; assigneeId: string | null; createdAt: string; dueAt: string | null; completedAt: string | null; attachments?: AttachmentDto[]; comments?: CommentDto[]; history?: HistoryDto[] }
export interface EventTypeDto { id: string; title: string; color: string; ownerId: string | null; defaultReminders: number[] }
export interface EventDto { id: string; ownerId: string; title: string; description?: string; location?: string; startsAt: string; endsAt: string | null; visibility: 'TEAM' | 'GROUP' | 'PERSONAL'; group: string | null; typeId?: string; masked: boolean; partial: boolean }
export interface LessonDto { id: string; group: string; date: string; start: string; end: string; title: string; room: string; teacher: string; kind: string }
export interface LessonsDto { lessons: LessonDto[] | null; updatedAt: string | null; source: 'ACTUAL' | 'CACHED' | 'UNAVAILABLE' }
export interface GroupDto { id: string; groupCode: string; name: string }
export interface NotificationDto { id: string; kind: string; title: string; text: string; at: string; read: boolean; taskId: string | null; eventId: string | null }

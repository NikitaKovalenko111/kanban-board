import Icon from '../Icon/Icon'
import { useCallback, useEffect, useRef, useState, type FormEvent, type JSX } from 'react'
import { createPortal } from 'react-dom'
import type React from 'react'
import { uploadFiles, downloadFile as saveDownload } from '../../api/attachments'
import SearchField from '../SearchField/SearchField'
import { isAssignedTo } from '../../utils/taskPermissions'
import { useProjectSelection } from '../../store/useProjectSelection'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { addTaskCommentRequest, assignTaskRequest, createTaskRequest, updateTaskStatusRequest } from '../../store/slices/tasksSlice'
import { loadTeamRequest } from '../../store/slices/teamSlice'
import type { Attachment, Task, TaskStatus } from '../../types'

type PropsType = { task: Task | null; onClose: () => void }
const statusLabels: Record<TaskStatus, string> = { waiting: 'Ожидание', working: 'В работе', checking: 'На проверке', done: 'Выполнено' }

const AddTaskModal: React.FC<PropsType> = ({ task, onClose }): JSX.Element => {
    const dispatch = useAppDispatch()
    const { saving, error: taskError } = useAppSelector((state) => state.tasks)
    const user = useAppSelector((state) => state.auth.user)!
    const { members, loading: teamLoading, error: teamError } = useAppSelector((state) => state.team)
    const activeProjectId = useProjectSelection('board')
    const currentTask = useAppSelector((state) => state.tasks.items.find((item) => item.taskId === task?.taskId)) ?? task
    task = currentTask
    const [assigneeDraft, setAssigneeDraft] = useState<string | null>(null)
    const currentAssignee = task?.assigneeId ?? members.find((member) => member.name === task?.assignee)?.id
    const assigneeValue = assigneeDraft ?? (currentAssignee === undefined ? '' : String(currentAssignee))
    const [submitted, setSubmitted] = useState(false)
    const [fileError, setFileError] = useState('')
    const [uploading, setUploading] = useState(false)
    const [commentQuery, setCommentQuery] = useState('')
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [deadline, setDeadline] = useState('')
    const [attachments, setAttachments] = useState<Attachment[]>([])
    const [resultAttachments, setResultAttachments] = useState<Attachment[]>(task?.resultAttachments ?? [])
    const [revision, setRevision] = useState('')
    const [comment, setComment] = useState('')
    const comments = task?.comments ?? []
    const visibleComments = comments.filter((item) => `${item.author} ${item.text}`.toLowerCase().includes(commentQuery.toLowerCase()))
    const [closing, setClosing] = useState(false)
    const closeTimer = useRef<number | null>(null)
    const closingRef = useRef(false)
    const onCloseRef = useRef(onClose)
    onCloseRef.current = onClose

    const close = useCallback(() => {
      if (closingRef.current) return
      closingRef.current = true
      setClosing(true)
      closeTimer.current = window.setTimeout(() => onCloseRef.current(), 260)
    }, [])

    useEffect(() => { if (user.role === 'lead') dispatch(loadTeamRequest()) }, [dispatch, user.role])
    useEffect(() => { setAssigneeDraft(null) }, [task?.assigneeId, task?.assignee])
    useEffect(() => { if (submitted && !saving && !taskError) close() }, [submitted, saving, taskError, close])

    useEffect(() => {
      const previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      const handleKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') close() }
      document.addEventListener('keydown', handleKeyDown)
      return () => {
        document.body.style.overflow = previousOverflow
        document.removeEventListener('keydown', handleKeyDown)
        if (closeTimer.current) window.clearTimeout(closeTimer.current)
      }
    }, [close])

    const submit = (event: FormEvent) => {
      event.preventDefault()
      dispatch(createTaskRequest({ projectId: activeProjectId, taskName: name, taskDesc: description, taskDeadline: deadline || undefined, attachments, ...(assigneeValue ? { assigneeId: Number(assigneeValue) } : {}) }))
      setSubmitted(true)
    }

    const move = (status: TaskStatus, workFiles?: Attachment[]) => {
      if (!task) return
      const assignee = task.taskStatus === 'waiting' ? user.name : undefined
      dispatch(updateTaskStatusRequest({ taskId: task.taskId, status, resultAttachments: workFiles, author: user.name, assignee }))
    }

    const addComment = () => {
      if (!task || !comment.trim()) return
      dispatch(addTaskCommentRequest({ taskId: task.taskId, author: user.name, text: comment.trim() }))
      setComment('')
    }

    const downloadFile = async (file: Attachment) => {
      try { setFileError(''); await saveDownload(file) }
      catch (error) { setFileError(error instanceof Error ? error.message : 'Не удалось скачать файл') }
    }

    const returnForRevision = () => {
      if (!task || !revision.trim()) return
      dispatch(updateTaskStatusRequest({ taskId: task.taskId, status: 'working', revisionComment: revision, resultAttachments: [], author: user.name }))
      close()
    }

    const handleUpload = async (files: FileList | null, result: boolean) => {
      if (!files?.length) return
      setUploading(true); setFileError('')
      try {
        const uploaded = await uploadFiles(files)
        if (result) setResultAttachments((current) => [...current, ...uploaded])
        else setAttachments((current) => [...current, ...uploaded])
      } catch (error) { setFileError(error instanceof Error ? error.message : 'Не удалось загрузить файлы') }
      finally { setUploading(false) }
    }

    const canTakeTask = !!task && (user.role === 'lead' || isAssignedTo(task, user) || task.assignee === 'Не назначен')
    const canSubmitTask = !!task && isAssignedTo(task, user)
    const assigneeSelect = <label className="task-assignment__label">Исполнитель<select value={assigneeValue} disabled={saving || teamLoading} onChange={(event) => setAssigneeDraft(event.target.value)}><option value="" disabled={task?.taskStatus === 'working'}>Не назначен</option>{members.filter((member) => member.active).map((member) => <option key={member.id} value={member.id}>{member.name} (@{member.login})</option>)}{currentAssignee !== undefined && !members.some((member) => member.active && member.id === currentAssignee) && <option value={currentAssignee} disabled>{task?.assignee}</option>}</select></label>

    return createPortal(
        <div className={`add-task-modal ${closing ? 'is-closing' : ''}`} role="dialog" aria-modal="true">
            <button className="add-task-modal__backdrop" onClick={close} aria-label="Закрыть" />
            <div className="add-task-modal__wrapper">
                <div className="add-task-modal__top"><div><span className="eyebrow">{task ? `Задача #${task.taskId}` : 'Новая задача'}</span><h2 className="add-task-modal__header">{task ? task.taskName : 'Создать задачу'}</h2></div><button className="icon-button" onClick={close} aria-label="Закрыть"><Icon name="close" /></button></div>
                {(fileError || taskError) && <div className="error-banner" role="alert">{fileError || taskError}</div>}
                {user.role === 'lead' && teamError && <div className="error-banner" role="alert">{teamError}</div>}
                {task ? (
                  <div className="task-details">
                    <div className="task-details__status"><span>Статус</span><strong>{statusLabels[task.taskStatus]}</strong></div>
                    <section><span className="eyebrow">Описание / ТЗ</span><p>{task.taskDesc}</p></section>
                    {task.revisionComment && <section className="task-details__notice"><span className="eyebrow">Комментарий к доработке</span><p>{task.revisionComment}</p></section>}
                    <div className="task-details__grid"><section><span className="eyebrow">Исполнитель</span><p>{task.assignee}</p></section><section><span className="eyebrow">Дедлайн</span><p>{task.taskDeadline ? new Date(task.taskDeadline).toLocaleDateString('ru-RU') : 'Не указан'}</p></section></div>
                    {user.role === 'lead' && (task.taskStatus === 'waiting' || task.taskStatus === 'working') && <section className="task-assignment">{assigneeSelect}<button type="button" className="button button--secondary" disabled={saving || teamLoading || assigneeValue === (currentAssignee === undefined ? '' : String(currentAssignee))} onClick={() => dispatch(assignTaskRequest({ taskId: task.taskId, assigneeId: assigneeValue ? Number(assigneeValue) : null }))}>Сохранить исполнителя</button></section>}
                    <section><span className="eyebrow">Исходники</span>{task.attachments.length ? task.attachments.map((file) => <button type="button" className="file-row" key={file.id} onClick={() => void downloadFile(file)}><Icon name="arrow-down" /> <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></button>) : <p className="muted">Нет вложений</p>}</section>
                    {task.taskStatus === 'working' && canSubmitTask && <section className="work-result"><span className="eyebrow">Результат работы</span><p className="muted">Перед отправкой на проверку прикрепите выполненную работу.</p><label className="file-upload"><Icon name="plus" /> Прикрепить результат<input type="file" multiple onChange={(event) => void handleUpload(event.target.files, true)} /></label>{resultAttachments.map((file) => <button type="button" className="file-row" key={file.id} onClick={() => void downloadFile(file)}><Icon name="arrow-down" /> <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></button>)}</section>}
                    {task.taskStatus === 'working' && !canSubmitTask && <section className="task-details__notice"><span className="eyebrow">Результат работы</span><p>Задача назначена другому участнику</p></section>}
                    {(task.taskStatus === 'checking' || task.taskStatus === 'done') && <section><span className="eyebrow">Результат работы</span>{task.resultAttachments.map((file) => <button type="button" className="file-row" key={file.id} onClick={() => void downloadFile(file)}><Icon name="arrow-down" /> <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></button>)}</section>}
                    <div className="task-details__actions">
                      {task.taskStatus === 'waiting' && <button className="button" disabled={!canTakeTask} title={!canTakeTask ? `Задача назначена: ${task.assignee}` : undefined} onClick={() => move('working')}>{canTakeTask ? (task.assignee !== 'Не назначен' && !isAssignedTo(task, user) ? 'Переназначить на себя и взять в работу' : 'Взять в работу') : 'Назначена другому участнику'}</button>}
                      {task.taskStatus === 'working' && canSubmitTask && <button className="button" disabled={!resultAttachments.length || saving || uploading} onClick={() => move('checking', resultAttachments)}>Отправить на проверку</button>}
                      {task.taskStatus === 'checking' && user.role === 'lead' && <button className="button" onClick={() => move('done')}>Принять задачу</button>}
                      {task.taskStatus === 'done' && user.role === 'lead' && <button className="button button--secondary" onClick={() => move('working')}>Вернуть в работу</button>}
                    </div>
                    {user.role === 'lead' && task.taskStatus === 'checking' && <div className="revision-form"><label>Что нужно доработать<textarea value={revision} onChange={(event) => setRevision(event.target.value)} placeholder="Оставьте понятный комментарий" /></label><button className="button button--danger" onClick={returnForRevision} disabled={!revision.trim()}>Вернуть на доработку</button></div>}
                    <section className="task-comments">
                      <div className="task-comments__heading"><span className="task-comments__icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5h14v10H9l-4 4V5Z" /><path d="M8 9h8M8 12h5" /></svg></span><div><span className="eyebrow">Комментарии</span><small>{comments.length} сообщений</small></div></div>
                      <SearchField value={commentQuery} onChange={setCommentQuery} placeholder="Поиск комментариев" />
                      {comments.length > 0 && <div className="task-comments__list" tabIndex={0} role="region" aria-label="Общий разбор">{visibleComments.map((item) => <article key={item.id}><div><strong>{item.author}</strong><time>{new Date(item.createdAt).toLocaleString('ru-RU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}</time></div><p>{item.text}</p></article>)}</div>}
                      {commentQuery && !visibleComments.length && <p className="muted">Комментарии не найдены</p>}
                      <div className="task-comments__form"><textarea value={comment} onChange={(event) => setComment(event.target.value)} placeholder="Написать комментарий…" /><button type="button" onClick={addComment} disabled={!comment.trim() || saving} aria-label="Отправить комментарий">➤</button></div>
                    </section>
                    <section className="task-history">
                      <span className="eyebrow">История задачи</span>
                      {task.history.length ? <div className="task-history__list">{[...task.history].reverse().map((event) => <article className={`history-event history-event--${event.type}`} key={event.id}><i /><div className="history-event__content"><div className="history-event__heading"><strong>{event.type === 'assignment' ? 'Изменение исполнителя' : event.type === 'revision' ? 'Возврат на доработку' : 'Изменение статуса'}</strong><time>{new Date(event.createdAt).toLocaleString('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</time></div><p><b>{event.author}</b>{event.type !== 'assignment' && <span className="status-change">{statusLabels[event.fromStatus]} <Icon name="arrow-right" /> {statusLabels[event.toStatus]}</span>}</p>{event.comment && <blockquote>{event.comment}</blockquote>}</div></article>)}</div> : <p className="task-history__empty">Изменений пока не было</p>}
                    </section>
                  </div>
                ) : (
                <form className="add-task-modal__form" onSubmit={submit}>
                    <label>
                        Название задачи
                        <input required value={name} onChange={(event) => setName(event.target.value)} type="text" placeholder="Например, подготовить макет" />
                    </label>
                    <label>
                        Описание / ТЗ
                        <textarea required value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Опишите результат и важные детали" />
                    </label>
                    <label>
                        Дедлайн <small>необязательно</small>
                        <input value={deadline} onChange={(event) => setDeadline(event.target.value)} type="date" />
                    </label>
                    {user.role === 'lead' && assigneeSelect}
                    <label className="file-upload"><Icon name="plus" /> Прикрепить исходники<input type="file" multiple onChange={(event) => void handleUpload(event.target.files, false)} /></label>
                    {attachments.map((file) => <div className="file-row" key={file.id}>▱ <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></div>)}
                    <div className="add-task-modal__footer"><button type="button" className="button button--secondary" onClick={close}>Отмена</button><button type="submit" className="button" disabled={saving || uploading}>{uploading ? 'Загрузка файлов…' : saving ? 'Создаём…' : 'Создать задачу'}</button></div>
                </form>
                )}
            </div>
        </div>,
        document.body,
    )
}

export default AddTaskModal

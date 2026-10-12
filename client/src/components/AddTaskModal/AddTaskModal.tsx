import { useCallback, useEffect, useRef, useState, type FormEvent, type JSX } from 'react'
import { createPortal } from 'react-dom'
import type React from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createTaskRequest, updateTaskStatusRequest } from '../../store/slices/tasksSlice'
import type { Attachment, Task, TaskStatus } from '../../types'

type PropsType = { task: Task | null; onClose: () => void }
const statusLabels: Record<TaskStatus, string> = { waiting: 'Ожидание', working: 'В работе', checking: 'На проверке', done: 'Выполнено' }

const AddTaskModal: React.FC<PropsType> = ({ task, onClose }): JSX.Element => {
    const dispatch = useAppDispatch()
    const { saving } = useAppSelector((state) => state.tasks)
    const user = useAppSelector((state) => state.auth.user)!
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [deadline, setDeadline] = useState('')
    const [attachments, setAttachments] = useState<Attachment[]>([])
    const [resultAttachments, setResultAttachments] = useState<Attachment[]>(task?.resultAttachments ?? [])
    const [revision, setRevision] = useState('')
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
      dispatch(createTaskRequest({ taskName: name, taskDesc: description, taskDeadline: deadline || undefined, attachments }))
      close()
    }

    const move = (status: TaskStatus, workFiles?: Attachment[]) => {
      if (!task) return
      dispatch(updateTaskStatusRequest({ taskId: task.taskId, status, resultAttachments: workFiles }))
      close()
    }

    const returnForRevision = () => {
      if (!task || !revision.trim()) return
      dispatch(updateTaskStatusRequest({ taskId: task.taskId, status: 'working', revisionComment: revision, resultAttachments: [] }))
      close()
    }

    const handleFiles = (files: FileList | null) => {
      if (!files) return
      setAttachments(Array.from(files).map((file, index) => ({ id: `${Date.now()}-${index}`, name: file.name, size: file.size })))
    }

    const handleResultFiles = (files: FileList | null) => {
      if (!files) return
      setResultAttachments(Array.from(files).map((file, index) => ({ id: `result-${Date.now()}-${index}`, name: file.name, size: file.size })))
    }

    return createPortal(
        <div className={`add-task-modal ${closing ? 'is-closing' : ''}`} role="dialog" aria-modal="true">
            <button className="add-task-modal__backdrop" onClick={close} aria-label="Закрыть" />
            <div className="add-task-modal__wrapper">
                <div className="add-task-modal__top"><div><span className="eyebrow">{task ? `Задача #${task.taskId}` : 'Новая задача'}</span><h2 className="add-task-modal__header">{task ? task.taskName : 'Создать задачу'}</h2></div><button className="icon-button" onClick={close}>×</button></div>
                {task ? (
                  <div className="task-details">
                    <div className="task-details__status"><span>Статус</span><strong>{statusLabels[task.taskStatus]}</strong></div>
                    <section><span className="eyebrow">Описание / ТЗ</span><p>{task.taskDesc}</p></section>
                    {task.revisionComment && <section className="task-details__notice"><span className="eyebrow">Комментарий к доработке</span><p>{task.revisionComment}</p></section>}
                    <div className="task-details__grid"><section><span className="eyebrow">Исполнитель</span><p>{task.assignee}</p></section><section><span className="eyebrow">Дедлайн</span><p>{task.taskDeadline ? new Date(task.taskDeadline).toLocaleDateString('ru-RU') : 'Не указан'}</p></section></div>
                    <section><span className="eyebrow">Исходники</span>{task.attachments.length ? task.attachments.map((file) => <div className="file-row" key={file.id}>▱ <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></div>) : <p className="muted">Нет вложений</p>}</section>
                    {task.taskStatus === 'working' && <section className="work-result"><span className="eyebrow">Результат работы</span><p className="muted">Перед отправкой на проверку прикрепите выполненную работу.</p><label className="file-upload">＋ Прикрепить результат<input type="file" multiple onChange={(event) => handleResultFiles(event.target.files)} /></label>{resultAttachments.map((file) => <div className="file-row" key={file.id}>▱ <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></div>)}</section>}
                    {(task.taskStatus === 'checking' || task.taskStatus === 'done') && <section><span className="eyebrow">Результат работы</span>{task.resultAttachments.map((file) => <div className="file-row" key={file.id}>▱ <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></div>)}</section>}
                    <div className="task-details__actions">
                      {task.taskStatus === 'waiting' && <button className="button" onClick={() => move('working')}>Взять в работу</button>}
                      {task.taskStatus === 'working' && <button className="button" disabled={!resultAttachments.length || saving} onClick={() => move('checking', resultAttachments)}>Отправить на проверку</button>}
                      {task.taskStatus === 'checking' && user.role === 'lead' && <button className="button" onClick={() => move('done')}>Принять задачу</button>}
                      {task.taskStatus === 'done' && user.role === 'lead' && <button className="button button--secondary" onClick={() => move('working')}>Вернуть в работу</button>}
                    </div>
                    {user.role === 'lead' && task.taskStatus === 'checking' && <div className="revision-form"><label>Что нужно доработать<textarea value={revision} onChange={(event) => setRevision(event.target.value)} placeholder="Оставьте понятный комментарий" /></label><button className="button button--danger" onClick={returnForRevision} disabled={!revision.trim()}>Вернуть на доработку</button></div>}
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
                    <label className="file-upload">＋ Прикрепить исходники<input type="file" multiple onChange={(event) => handleFiles(event.target.files)} /></label>
                    {attachments.map((file) => <div className="file-row" key={file.id}>▱ <span>{file.name}</span><small>{Math.ceil(file.size / 1024)} КБ</small></div>)}
                    <div className="add-task-modal__footer"><button type="button" className="button button--secondary" onClick={close}>Отмена</button><button type="submit" className="button" disabled={saving}>{saving ? 'Создаём…' : 'Создать задачу'}</button></div>
                </form>
                )}
            </div>
        </div>,
        document.body,
    )
}

export default AddTaskModal

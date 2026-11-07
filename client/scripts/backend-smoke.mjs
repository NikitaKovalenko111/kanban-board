// Disposable stack only: creates an initial captain in its temporary DB; backend sources are untouched.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
const project = 'kanban-client-integration'
const db = project + '-db-1'
const inspect = JSON.parse(execFileSync('docker', ['inspect', db], { encoding: 'utf8' }))[0]
assert.equal(inspect.Config.Labels['com.docker.compose.project'], project)
const login = 'web-' + randomUUID().slice(0, 8), password = 'A1' + randomUUID()
const memberLogin = 'member-' + randomUUID().slice(0, 8), memberPassword = 'B2' + randomUUID()
const sql = `CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO student_groups(student_group_id, group_code, name) VALUES(gen_random_uuid(), 'TOP-203B', 'TOP-203B') ON CONFLICT(group_code) DO NOTHING;
INSERT INTO users(user_id, username, display_name, student_group_id, system_role, password_hash)
SELECT gen_random_uuid(), '${login}', 'Web Captain', student_group_id, 'CAPTAIN', crypt('${password}',gen_salt('bf')) FROM student_groups WHERE group_code='TOP-203B';`
execFileSync('docker', ['exec', '-i', db, 'psql', '-U', 'kanban_client_check', '-d', 'kanban_client_check', '-v', 'ON_ERROR_STOP=1'], { input: sql, stdio: ['pipe', 'pipe', 'pipe'] })
const base = 'http://127.0.0.1:13005/api/v1'
let token
const call = async(path, method='GET', body, authorization=token) => {
  const response = await fetch(base+path, { method, headers: { ...(authorization?{Authorization:'Bearer '+authorization}:{}), ...(body && !(body instanceof FormData)?{'Content-Type':'application/json'}:{}) }, body:body instanceof FormData?body:body===undefined?undefined:JSON.stringify(body) })
  if (!response.ok) { const detail=await response.text(); throw Error(method+' '+path+' '+response.status+' '+detail) }
  return response.status===204?null:await response.json()
}
assert.equal((await fetch(base+'/system/health')).status,200)
assert([401,404].includes((await fetch(base+'/auth/register',{method:'POST'})).status))
let auth=await call('/auth/login','POST',{login,password},null);token=auth.accessToken
assert.equal(auth.user.role,'CAPTAIN')
const captain=auth.user
const member=await call('/users','POST',{fullName:'Web Member',username:memberLogin,temporaryPassword:memberPassword,studentGroup:'TOP-203B',role:'MEMBER'})
const created=await call('/projects','POST',{name:'Web integration '+randomUUID().slice(0,6),description:'Backend and MinIO',members:[{userId:member.id,role:'Developer'},{userId:captain.id,role:'Lead'}]})
const columns=await call('/projects/'+created.id+'/columns')
const working=columns.find(c=>c.kind==='IN_PROGRESS'), review=columns.find(c=>c.kind==='REVIEW'), done=columns.find(c=>c.kind==='DONE')
const task=await call('/projects/'+created.id+'/tasks','POST',{title:'Integration task',description:'Keep source materials',priority:'NORMAL',assigneeId:member.id})
const upload=async(path,kind,text)=>{const form=new FormData();form.set('file',new File([text],'check.txt',{type:'text/plain'}));if(kind)form.set('kind',kind);return call(path,'POST',form)}
const source=await upload('/tasks/'+task.id+'/attachments','SOURCE','Original source')
const projectSource=await upload('/projects/'+created.id+'/sources/files',undefined,'Project source')
// UUID assignment and clearing are checked on an existing task, not only during creation.
await call('/tasks/'+task.id,'PATCH',{assigneeId:captain.id})
assert.equal((await call('/tasks/'+task.id)).assigneeId,captain.id)
await call('/tasks/'+task.id,'PATCH',{assigneeId:null})
assert.equal((await call('/tasks/'+task.id)).assigneeId,null)
await call('/tasks/'+task.id,'PATCH',{assigneeId:member.id})
const foreignTask=await call('/projects/'+created.id+'/tasks','POST',{title:'Assigned to captain',description:'QA authorization',priority:'NORMAL',assigneeId:captain.id})
const sourceFile=await fetch(base+'/files/'+source.file.id,{headers:{Authorization:'Bearer '+token}})
assert.equal(await sourceFile.text(),'Original source')
const captainToken=token
auth=await call('/auth/login','POST',{login:memberLogin,password:memberPassword},null);token=auth.accessToken
const deniedTake=await fetch(base+'/tasks/'+foreignTask.id+'/take',{method:'POST',headers:{Authorization:'Bearer '+token}})
assert([403,409].includes(deniedTake.status))
assert.equal((await call('/tasks/'+foreignTask.id)).assigneeId,captain.id)
const deniedMove=await fetch(base+'/tasks/'+foreignTask.id+'/move',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({columnId:working.id})})
assert.equal(deniedMove.status,403)
await call('/tasks/'+task.id+'/move','POST',{columnId:working.id})
const wrongResult=new FormData();wrongResult.set('file',new File(['Unauthorized'],'foreign.txt'));wrongResult.set('kind','RESULT')
assert.equal((await fetch(base+'/tasks/'+task.id+'/attachments',{method:'POST',headers:{Authorization:'Bearer '+captainToken},body:wrongResult})).status,403)
const result=await upload('/tasks/'+task.id+'/attachments','RESULT','Completed result')
await call('/tasks/'+task.id+'/comments','POST',{text:'Please review'})
await call('/tasks/'+task.id+'/move','POST',{columnId:review.id})
token=captainToken
await call('/tasks/'+task.id+'/move','POST',{columnId:working.id,comment:'Please correct spacing'})
const revised=await call('/tasks/'+task.id)
assert(revised.attachments.some(file=>file.id===source.id))
assert(revised.history.some(event=>event.comment==='Please correct spacing'))
token=auth.accessToken
await call('/tasks/'+task.id+'/move','POST',{columnId:review.id})
token=captainToken
await call('/tasks/'+task.id+'/move','POST',{columnId:done.id})
assert((await call('/tasks/completed')).some(item=>item.id===task.id))
const type=await call('/event-types','POST',{title:'Check '+randomUUID().slice(0,5),color:'#6577df',defaultReminders:[90]})
const startsAt=new Date(Date.now()+2*86400000).toISOString()
const event=await call('/events','POST',{title:'Web event',description:'Shared event',startsAt,visibility:'TEAM',typeId:type.id})
await call('/me/reminders/'+encodeURIComponent('event:'+event.id),'PUT',{minutes:[30]})
assert.deepEqual((await call('/me/reminders'))['event:'+event.id],[30])
const notifications=await call('/notifications')
assert(Array.isArray(notifications))
const lessonId=randomUUID(), externalLessonId='uust-'+randomUUID()
const lessonSql=`INSERT INTO lessons(lesson_id,student_group_id,external_id,title,lesson_kind,starts_at,ends_at,source_status,source_updated_at)
SELECT '${lessonId}',student_group_id,'${externalLessonId}','Web lesson','LECTURE','${startsAt}'::timestamptz,'${startsAt}'::timestamptz+INTERVAL '80 minutes','ACTUAL',CURRENT_TIMESTAMP FROM student_groups WHERE group_code='TOP-203B';
INSERT INTO notifications(notification_id,user_id,kind,title,text,lesson_id) VALUES(gen_random_uuid(),'${captain.id}','LESSON','Web lesson','Fixture lesson reminder','${lessonId}');`
execFileSync('docker',['exec','-i',db,'psql','-U','kanban_client_check','-d','kanban_client_check','-v','ON_ERROR_STOP=1'],{input:lessonSql,stdio:['pipe','pipe','pipe']})
const lessonNotice=(await call('/notifications')).find(item=>item.lessonId===lessonId)
assert.equal(lessonNotice.kind,'LESSON')
assert.equal(lessonNotice.taskId,undefined)
assert.equal(lessonNotice.eventId,undefined)
const lessonRange=await call('/schedule/lessons?group=TOP-203B&from='+new Date().toISOString().slice(0,10)+'&to='+startsAt.slice(0,10))
assert(lessonRange.lessons.some(item=>item.id===externalLessonId))
assert(!lessonRange.lessons.some(item=>item.id===lessonId))
console.log('CONFIRMED CONTRACT GAP: notification.lessonId is internal UUID, but schedule.lessons[].id is external_id; no lesson-by-UUID endpoint.')
await call('/notifications/read-all','POST')
assert((await call('/notifications')).every(item=>item.read))
assert.equal(columns.length,4)
assert.deepEqual(columns.map(column=>column.kind),['BACKLOG','IN_PROGRESS','REVIEW','DONE'])
assert((await call('/projects')).every(project=>project.canViewBoard===true))
const hidden=await call('/projects','POST',{name:'Private project '+randomUUID().slice(0,8),members:[{userId:captain.id,role:'Lead'}]})
token=auth.accessToken
const hiddenSummary=(await call('/projects')).find(project=>project.id===hidden.id)
assert.equal(hiddenSummary.canViewBoard,false)
assert.equal(hiddenSummary.members,undefined)
assert.equal(hiddenSummary.sources,undefined)
assert.equal(hiddenSummary.description,undefined)
assert.equal((await fetch(base+'/projects/'+hidden.id+'/tasks',{headers:{Authorization:'Bearer '+token}})).status,403)
assert((await call('/tasks')).some(item=>item.id===task.id))
assert(!(await call('/tasks')).some(item=>item.projectId===hidden.id))
const from=new Date(Date.now()-86400000).toISOString().slice(0,10),to=new Date(Date.now()+4*86400000).toISOString().slice(0,10)
assert((await call('/events?from='+from+'&to='+to+'&personId='+captain.id)).some(item=>item.id===event.id))
token=captainToken
const personal=await call('/events','POST',{title:'Private title',description:'Private description',startsAt,visibility:'PERSONAL',typeId:type.id})
for(const [privacy,expected] of [['NOTHING',null],['BUSY','Занят'],['TITLE','Private title'],['TITLE_AND_DESCRIPTION','Private title']]) {
  await call('/me','PATCH',{schedulePrivacy:privacy})
  const found=(await call('/events?from='+from+'&to='+to+'&personId='+captain.id,'GET',undefined,auth.accessToken)).find(item=>item.id===personal.id)
  if(expected===null) assert.equal(found,undefined)
  else { assert.equal(found.title,expected); assert.equal(found.description??'',privacy==='TITLE_AND_DESCRIPTION'?'Private description':''); assert.equal(found.masked,privacy==='BUSY'); assert.equal(found.partial,privacy!=='BUSY') }
}
await call('/me','PATCH',{schedulePrivacy:'BUSY'})
// The old undocumented groups parameter must no longer change another person's schedule.
assert.deepEqual(await call('/events?from='+from+'&to='+to+'&personId='+captain.id+'&groups=nonexistent','GET',undefined,auth.accessToken),await call('/events?from='+from+'&to='+to+'&personId='+captain.id,'GET',undefined,auth.accessToken))
const rotated=await call('/auth/refresh','POST',{refreshToken:auth.refreshToken},null)
assert.notEqual(rotated.refreshToken,auth.refreshToken)
assert.equal((await fetch(base+'/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:auth.refreshToken})})).status,401)
auth=rotated
const passwordResponse = await fetch(base+'/auth/change-password',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+auth.accessToken},body:JSON.stringify({currentPassword:memberPassword,newPassword:'NewPassword123'})})
assert.equal(passwordResponse.status,204)
assert.equal((await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({login:memberLogin,password:memberPassword})})).status,401)
const fresh=await call('/auth/login','POST',{login:memberLogin,password:'NewPassword123'},null)
const logoutResponse=await fetch(base+'/auth/logout',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+fresh.accessToken},body:JSON.stringify({refreshToken:fresh.refreshToken})})
assert.equal(logoutResponse.status,204)
assert.equal((await fetch(base+'/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:fresh.refreshToken})})).status,401)
await call('/auth/change-password','POST',{currentPassword:'NewPassword123',newPassword:memberPassword},(await call('/auth/login','POST',{login:memberLogin,password:'NewPassword123'},null)).accessToken)
const dir=new URL('../.backend-check/',import.meta.url);await mkdir(dir,{recursive:true})
await writeFile(new URL('user.json',dir),JSON.stringify({login,password,memberLogin,memberPassword,projectId:created.id,taskId:task.id,eventId:event.id,typeId:type.id,sourceId:projectSource.id,resultId:result.id,lessonId,externalLessonId,lessonAt:lessonNotice.at,lessonDate:startsAt.slice(0,10)}),{mode:0o600})
console.log('PASS: auth/logout/change-password/refresh revocation, project access/redaction, four fixed columns, GET /tasks, task/source/result/comment/review/revision/completion, MinIO downloads, events and four privacy modes, types/reminders, notifications/read-all.')

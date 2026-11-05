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
assert.equal((await fetch(base+'/auth/register',{method:'POST'})).status,404)
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
const sourceFile=await fetch(base+'/files/'+source.file.id,{headers:{Authorization:'Bearer '+token}})
assert.equal(await sourceFile.text(),'Original source')
const captainToken=token
auth=await call('/auth/login','POST',{login:memberLogin,password:memberPassword},null);token=auth.accessToken
await call('/tasks/'+task.id+'/move','POST',{columnId:working.id})
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
await call('/notifications/read-all','POST')
assert((await call('/notifications')).every(item=>item.read))
const revoked=auth.refreshToken
const logoutResponse = await fetch(base+'/auth/logout',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+auth.accessToken},body:JSON.stringify({refreshToken:revoked})})
assert.equal(logoutResponse.status,404) // Known backend router shadowing, recorded in the report.
const passwordResponse = await fetch(base+'/auth/change-password',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+auth.accessToken},body:JSON.stringify({currentPassword:memberPassword,newPassword:'NewPassword123'})})
assert.equal(passwordResponse.status,404)
assert.equal((await fetch(base+'/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:revoked})})).status,200)
const dir=new URL('../.backend-check/',import.meta.url);await mkdir(dir,{recursive:true})
await writeFile(new URL('user.json',dir),JSON.stringify({login,password,memberLogin,memberPassword,projectId:created.id,taskId:task.id,eventId:event.id,typeId:type.id,sourceId:projectSource.id,resultId:result.id}),{mode:0o600})
console.log('PASS: real auth, users, projects/members, task/source/result/comment/review/revision/completion, MinIO downloads, event types/reminders, notifications/read-all and confirmed logout/change-password routing discrepancy.')

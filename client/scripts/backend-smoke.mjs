// Run against the disposable backend-check.compose.yaml stack, never a production DB.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'

const baseUrl = 'http://127.0.0.1:13005'
const request = (path, payload) => fetch(`${baseUrl}${path}`, payload ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) } : undefined)
const login = `web-check-${randomUUID().slice(0, 8)}`
const password = `A1${randomUUID()}`

assert.equal((await request('/system/health')).status, 200)
const registered = await request('/auth/register', { username: login, email: `${login}@example.test`, password, group_name: 'TOP-203B' })
assert.equal(registered.status, 201)
const { user_id } = await registered.json()
assert.equal((await request('/auth/login', { login, password: 'Wrong123' })).status, 401)
const signedIn = await request('/auth/login', { login, password })
assert.equal(signedIn.status, 200)
const first = await signedIn.json()
assert.equal(first.user_id, user_id)
assert.equal(first.token_type, 'Bearer')
assert(first.access_token && first.refresh_token)
const renewed = await request('/auth/refresh', { refresh_token: first.refresh_token })
assert.equal(renewed.status, 200)
const second = await renewed.json()
assert.equal(second.user_id, user_id)
assert.notEqual(second.access_token, first.access_token)
assert.notEqual(second.refresh_token, first.refresh_token)
assert.equal((await request('/auth/refresh', { refresh_token: first.refresh_token })).status, 401)
assert.equal((await request('/auth/logout', { refresh_token: second.refresh_token })).status, 204)
assert.equal((await request('/auth/refresh', { refresh_token: second.refresh_token })).status, 401)
for (const path of ['/projects', '/tasks', '/team', '/schedule/all']) assert.equal((await request(path)).status, 404)

// Only test credentials, in a gitignored client directory, for the follow-up browser check.
const directory = new URL('../.backend-check/', import.meta.url)
await mkdir(directory, { recursive: true })
await writeFile(new URL('user.json', directory), JSON.stringify({ login, password }), { mode: 0o600 })
console.log('PASS: existing backend health/register/login/refresh rotation/logout/revoked tokens; unsupported routes return 404.')

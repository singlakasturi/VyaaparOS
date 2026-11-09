import test, { beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { fetchNotifications, fetchUnreadNotificationCount, markAllNotificationsRead, markNotificationRead } from '../src/services/notifications.js'

beforeEach(() => {
  mock.reset()
  globalThis.window = { sessionStorage: { getItem: () => 'token', removeItem: () => {} }, dispatchEvent: () => {} }
  globalThis.fetch = mock.fn(async () => ({ ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ content: [] }) }))
})

test('notification list supports paging through the authenticated API', async () => {
  await fetchNotifications(2, 30)
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  assert.equal(new URL(url).pathname, '/api/notifications')
  assert.equal(new URL(url).searchParams.get('page'), '2')
  assert.equal(new URL(url).searchParams.get('size'), '30')
  assert.equal(options.headers.Authorization, 'Bearer token')
})

test('unread count and read actions use notification endpoints', async () => {
  await fetchUnreadNotificationCount()
  await markNotificationRead('notification-1')
  await markAllNotificationsRead()
  assert.deepEqual(globalThis.fetch.mock.calls.map((call) => {
    const [url, options] = call.arguments
    return [new URL(url).pathname, options.method || 'GET']
  }), [
    ['/api/notifications/unread-count', 'GET'],
    ['/api/notifications/notification-1/read', 'PATCH'],
    ['/api/notifications/read-all', 'PATCH'],
  ])
})

test('notification read errors are preserved for clear UI feedback', async () => {
  globalThis.fetch = mock.fn(async () => ({ ok: false, status: 404, headers: { get: () => 'application/json' }, json: async () => ({ message: 'Notification not found.' }) }))
  await assert.rejects(markNotificationRead('private-id'), /Notification not found/)
})

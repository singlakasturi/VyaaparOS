import test, { beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { approveDeskOrder, fetchDeskOrders, fetchDeskOverview, rejectDeskOrder, updateDeskFulfillment } from '../src/services/orderDesk.js'

beforeEach(() => {
  mock.reset()
  globalThis.window = {
    sessionStorage: { getItem: () => 'token', removeItem: () => {} },
    dispatchEvent: () => {},
  }
  globalThis.fetch = mock.fn(async () => ({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ content: [], totalElements: 0 }),
  }))
})

test('order queue search, filters, and pagination are sent to the scoped API', async () => {
  await fetchDeskOrders({ q: 'ORD-9', status: 'SUBMITTED', retailerId: 'retailer-1', page: 2, size: 50 })
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  const parsed = new URL(url)
  assert.equal(parsed.pathname, '/api/order-desk/orders')
  assert.equal(parsed.searchParams.get('q'), 'ORD-9')
  assert.equal(parsed.searchParams.get('status'), 'SUBMITTED')
  assert.equal(parsed.searchParams.get('retailerId'), 'retailer-1')
  assert.equal(parsed.searchParams.get('page'), '2')
  assert.equal(parsed.searchParams.get('size'), '50')
  assert.equal(options.method, 'GET')
})

test('operational overview is read from the authenticated Order Desk endpoint', async () => {
  globalThis.fetch = mock.fn(async () => ({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ statusCounts: { SUBMITTED: 3 }, totalOrders: 3, recentOrders: [{ id: 'order-1' }] }),
  }))
  const overview = await fetchDeskOverview()
  assert.equal(overview.totalOrders, 3)
  assert.equal(overview.recentOrders[0].id, 'order-1')
  assert.equal(globalThis.fetch.mock.calls[0].arguments[0], 'http://localhost:8080/api/order-desk/overview')
})

test('review actions use separate endpoints and preserve conflict errors', async () => {
  await approveDeskOrder('order-1', 'Reviewed')
  await rejectDeskOrder('order-2', 'Out of stock')
  assert.deepEqual(globalThis.fetch.mock.calls.slice(0, 2).map((call) => {
    const [url, options] = call.arguments
    return [new URL(url).pathname, options.method, JSON.parse(options.body)]
  }), [
    ['/api/order-desk/orders/order-1/approve', 'POST', { reason: 'Reviewed' }],
    ['/api/order-desk/orders/order-2/reject', 'POST', { reason: 'Out of stock' }],
  ])

  globalThis.fetch = mock.fn(async () => ({
    ok: false,
    status: 409,
    headers: { get: () => 'application/json' },
    json: async () => ({ message: 'Order cannot be approved from its current status.' }),
  }))
  await assert.rejects(approveDeskOrder('order-1'), { status: 409, message: 'Order cannot be approved from its current status.' })
})

test('fulfillment progress updates use the scoped Order Desk API and preserve tracking references', async () => {
  await updateDeskFulfillment('order-42', 'SHIPPED', 'TRACK-9')
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  assert.equal(new URL(url).pathname, '/api/order-desk/orders/order-42/fulfillment')
  assert.equal(options.method, 'PATCH')
  assert.deepEqual(JSON.parse(options.body), { fulfillmentStatus: 'SHIPPED', trackingReference: 'TRACK-9' })
})

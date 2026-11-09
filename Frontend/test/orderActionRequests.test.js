import test, { beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { fetchOrderActionRequests, approveOrderActionRequest, rejectOrderActionRequest } from '../src/services/orderDesk.js'
import { fetchOrderActionRequests as fetchRetailerRequests, requestOrderCancellation, requestOrderReturn } from '../src/services/order.js'
import { fetchOrderInvoice } from '../src/services/order.js'
import { fetchDeskOrderInvoice } from '../src/services/orderDesk.js'

beforeEach(() => {
  mock.reset()
  globalThis.window = { sessionStorage: { getItem: () => 'token', removeItem: () => {} }, dispatchEvent: () => {} }
  globalThis.fetch = mock.fn(async () => ({ ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ content: [] }) }))
})

test('retailer request history and create actions use the order-scoped API', async () => {
  await fetchRetailerRequests('order-1')
  await requestOrderCancellation('order-1', 'Please stop')
  await requestOrderReturn('order-2', 'Damaged on arrival')
  assert.deepEqual(globalThis.fetch.mock.calls.map((call) => {
    const [url, options] = call.arguments
    return [new URL(url).pathname, options.method || 'GET', options.body ? JSON.parse(options.body) : null]
  }), [
    ['/api/orders/order-1/action-requests', 'GET', null],
    ['/api/orders/order-1/action-requests/cancellation', 'POST', { reason: 'Please stop' }],
    ['/api/orders/order-2/action-requests/return', 'POST', { reason: 'Damaged on arrival' }],
  ])
})

test('Order Desk request list is paged and supports scoped decision actions', async () => {
  await fetchOrderActionRequests({ status: 'PENDING', type: 'RETURN', orderId: 'order-8', page: 1, size: 40 })
  await approveOrderActionRequest('request-1', 'Approved')
  await rejectOrderActionRequest('request-2', 'Please provide photos')
  const [url] = globalThis.fetch.mock.calls[0].arguments
  const listUrl = new URL(url)
  assert.equal(listUrl.pathname, '/api/order-desk/action-requests')
  assert.equal(listUrl.searchParams.get('status'), 'PENDING')
  assert.equal(listUrl.searchParams.get('type'), 'RETURN')
  assert.equal(listUrl.searchParams.get('orderId'), 'order-8')
  assert.equal(listUrl.searchParams.get('page'), '1')
  assert.deepEqual(globalThis.fetch.mock.calls.slice(1).map((call) => {
    const [requestUrl, options] = call.arguments
    return [new URL(requestUrl).pathname, options.method, JSON.parse(options.body)]
  }), [
    ['/api/order-desk/action-requests/request-1/approve', 'POST', { comment: 'Approved' }],
    ['/api/order-desk/action-requests/request-2/reject', 'POST', { comment: 'Please provide photos' }],
  ])
})

test('invoice actions use authenticated retailer and Order Desk API endpoints', async () => {
  globalThis.fetch = mock.fn(async () => ({ ok: true, status: 200, headers: { get: (name) => name === 'content-disposition' ? 'inline; filename="invoice.html"' : 'text/html' }, blob: async () => new Blob(['<html></html>']) }))
  const retailer = await fetchOrderInvoice('order/1')
  const desk = await fetchDeskOrderInvoice('order-2')
  assert.equal(retailer.filename, 'invoice.html')
  assert.equal(retailer.blob.type, '')
  assert.equal(desk.blob.size, 13)
  assert.deepEqual(globalThis.fetch.mock.calls.map((call) => {
    const [url, options] = call.arguments
    return [new URL(url).pathname, options.method, options.headers.Authorization]
  }), [
    ['/api/orders/order%2F1/invoice', 'GET', 'Bearer token'],
    ['/api/order-desk/orders/order-2/invoice', 'GET', 'Bearer token'],
  ])
})

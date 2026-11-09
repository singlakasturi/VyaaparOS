import { apiDownload, apiRequest } from './api.js'

function append(params, name, value) {
  if (value !== undefined && value !== null && value !== '') params.set(name, String(value))
}

export function fetchDeskOverview() {
  return apiRequest('/api/order-desk/overview', { method: 'GET' })
}

export function fetchDeskOrders({ status, retailerId, q, startDate, endDate, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  append(params, 'status', status)
  append(params, 'retailerId', retailerId)
  append(params, 'q', q)
  append(params, 'startDate', startDate)
  append(params, 'endDate', endDate)
  params.set('page', String(page))
  params.set('size', String(size))
  return apiRequest(`/api/order-desk/orders?${params}`, { method: 'GET' })
}

export function fetchDeskOrder(orderId) {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}`, { method: 'GET' })
}

export function fetchDeskOrderInvoice(orderId) {
  return apiDownload(`/api/order-desk/orders/${encodeURIComponent(orderId)}/invoice`, { method: 'GET' })
}

export function fetchOrderHistory(orderId) {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}/history`, { method: 'GET' })
}

export function approveDeskOrder(orderId, reason = '') {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}/approve`, {
    method: 'POST', body: { reason },
  })
}

export function rejectDeskOrder(orderId, reason) {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}/reject`, {
    method: 'POST', body: { reason },
  })
}

export function updateDeskFulfillment(orderId, fulfillmentStatus, trackingReference) {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}/fulfillment`, {
    method: 'PATCH', body: { fulfillmentStatus, trackingReference },
  })
}

export function fetchOrderActionRequests({ status, type, orderId, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  append(params, 'status', status)
  append(params, 'type', type)
  append(params, 'orderId', orderId)
  params.set('page', String(page))
  params.set('size', String(size))
  return apiRequest(`/api/order-desk/action-requests?${params}`, { method: 'GET' })
}

export function approveOrderActionRequest(requestId, comment = '') {
  return apiRequest(`/api/order-desk/action-requests/${encodeURIComponent(requestId)}/approve`, {
    method: 'POST', body: { comment },
  })
}

export function rejectOrderActionRequest(requestId, comment = '') {
  return apiRequest(`/api/order-desk/action-requests/${encodeURIComponent(requestId)}/reject`, {
    method: 'POST', body: { comment },
  })
}

export function exportTallyCsv({ orderIds, startDate, endDate } = {}) {
  return apiDownload('/api/order-desk/exports/tally', {
    method: 'POST', body: { orderIds, startDate, endDate },
  })
}

export function fetchExportBatches(page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest(`/api/order-desk/exports?${params}`, { method: 'GET' })
}

export function fetchExportBatch(batchId) {
  return apiRequest(`/api/order-desk/exports/${encodeURIComponent(batchId)}`, { method: 'GET' })
}

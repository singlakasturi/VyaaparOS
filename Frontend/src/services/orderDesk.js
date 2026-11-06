import { apiDownload, apiRequest } from './api.js'

function append(params, name, value) {
  if (value !== undefined && value !== null && value !== '') params.set(name, String(value))
}

export function fetchDeskOrders({ status, retailerId, startDate, endDate, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  append(params, 'status', status)
  append(params, 'retailerId', retailerId)
  append(params, 'startDate', startDate)
  append(params, 'endDate', endDate)
  params.set('page', String(page))
  params.set('size', String(size))
  return apiRequest(`/api/order-desk/orders?${params}`, { method: 'GET' })
}

export function fetchDeskOrder(orderId) {
  return apiRequest(`/api/order-desk/orders/${encodeURIComponent(orderId)}`, { method: 'GET' })
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

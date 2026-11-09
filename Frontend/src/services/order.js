import { apiDownload, apiRequest } from './api.js'

export function placeOrder(retailerId, clientOrderId, notes = '', items = null) {
  const body = { retailerId, clientOrderId, notes }
  if (items && Array.isArray(items) && items.length > 0) {
    body.items = items
  }
  return apiRequest('/api/orders', {
    method: 'POST',
    body,
  })
}

export function fetchOrders(retailerId, page = 0, size = 20) {
  const params = new URLSearchParams()
  params.append('retailerId', retailerId)
  params.append('page', String(page))
  params.append('size', String(size))

  return apiRequest(`/api/orders?${params.toString()}`, {
    method: 'GET',
  })
}

export function fetchOrder(orderId) {
  return apiRequest(`/api/orders/${encodeURIComponent(orderId)}`, {
    method: 'GET',
  })
}

export function reorderHistoricalOrder(orderId) {
  return apiRequest(`/api/retailer/me/orders/${encodeURIComponent(orderId)}/reorder`, { method: 'POST' })
}

export function fetchOrderInvoice(orderId) {
  return apiDownload(`/api/orders/${encodeURIComponent(orderId)}/invoice`, { method: 'GET' })
}

export function fetchOrderActionRequests(orderId) {
  return apiRequest(`/api/orders/${encodeURIComponent(orderId)}/action-requests`, { method: 'GET' })
}

export function requestOrderCancellation(orderId, reason = '') {
  return apiRequest(`/api/orders/${encodeURIComponent(orderId)}/action-requests/cancellation`, {
    method: 'POST', body: { reason },
  })
}

export function requestOrderReturn(orderId, reason) {
  return apiRequest(`/api/orders/${encodeURIComponent(orderId)}/action-requests/return`, {
    method: 'POST', body: { reason },
  })
}

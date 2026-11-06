import { apiRequest } from './api.js'

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

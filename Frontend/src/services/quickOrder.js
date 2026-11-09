import { apiRequest } from './api.js'

export function submitQuickOrder(retailerId, rows) {
  return apiRequest(`/api/retailer/me/quick-order?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'POST', body: { rows },
  })
}

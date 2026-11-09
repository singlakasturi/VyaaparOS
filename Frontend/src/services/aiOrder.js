import { apiRequest } from './api.js'

export function getAcceptedAiOrderItems(items = []) {
  return items.filter((item) => !item.removed && item.selectedProductId && Number(item.paidCases) > 0 && item.resolutionStatus === 'MATCHED' && item.confirmed === true && item.unit === 'CASE' && !item.unitAmbiguous)
}

/**
 * POST /api/ai/orders/parse
 * Sends natural-language order text to the backend for AI parsing.
 * Does NOT modify the cart.
 *
 * @param {string} retailerId - UUID of the retailer
 * @param {string} text       - Natural-language order text (English / Hindi / Hinglish)
 * @returns {Promise<Object>} AiOrderParseResponse
 */
export function parseOrderText(retailerId, text) {
  return apiRequest('/api/ai/orders/parse', {
    method: 'POST',
    body: { retailerId, text },
  })
}

/**
 * POST /api/ai/orders/apply
 * Adds accepted parsed items to the retailer's cart.
 *
 * @param {string} retailerId - UUID of the retailer
 * @param {Array<{productId: string, paidCases: number, unit?: string}>} items - Accepted items
 * @returns {Promise<Object>} CartResponse
 */
export function applyOrderItems(retailerId, items) {
  return apiRequest('/api/ai/orders/apply', {
    method: 'POST',
    body: { retailerId, items },
  })
}

/**
 * GET /api/cart?retailerId=...
 * Fetches the current cart for price/scheme refresh after apply.
 *
 * @param {string} retailerId - UUID of the retailer
 * @returns {Promise<Object>} CartResponse
 */
export function fetchCart(retailerId) {
  return apiRequest(`/api/cart?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'GET',
  })
}

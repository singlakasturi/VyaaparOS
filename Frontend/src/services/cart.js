import { apiRequest } from './api.js'

export function fetchCart(retailerId) {
  return apiRequest(`/api/cart?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'GET',
  })
}

export function addToCart(retailerId, productId, paidCases) {
  return apiRequest('/api/cart/items', {
    method: 'POST',
    body: { retailerId, productId, paidCases },
  })
}

export function updateCartQuantity(itemId, paidCases) {
  return apiRequest(`/api/cart/items/${encodeURIComponent(itemId)}`, {
    method: 'PATCH',
    body: { paidCases },
  })
}

export function removeCartItem(itemId) {
  return apiRequest(`/api/cart/items/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
  })
}

export function clearCart(retailerId) {
  return apiRequest(`/api/cart?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'DELETE',
  })
}

import { apiRequest } from './api.js'

export function fetchCategories(retailerId) {
  const params = new URLSearchParams()
  if (retailerId) params.append('retailerId', retailerId)
  return apiRequest(`/api/catalog/categories?${params.toString()}`, { method: 'GET' })
}

export function fetchProducts({ retailerId, category, q, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (retailerId) params.append('retailerId', retailerId)
  if (category) params.append('category', category)
  if (q) params.append('q', q)
  params.append('page', String(page))
  params.append('size', String(size))

  return apiRequest(`/api/catalog/products?${params.toString()}`, { method: 'GET' })
}

export function fetchProduct(productId, retailerId) {
  const params = retailerId ? `?retailerId=${encodeURIComponent(retailerId)}` : ''
  return apiRequest(`/api/catalog/products/${encodeURIComponent(productId)}${params}`, { method: 'GET' })
}

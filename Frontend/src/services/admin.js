import { apiRequest } from './api.js'

function query(values) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const suffix = params.toString()
  return suffix ? `?${suffix}` : ''
}

export const fetchAdminRetailers = () => apiRequest('/api/retailers/assigned')
export const fetchAdminRetailer = (id) => apiRequest(`/api/salesperson/retailers/${encodeURIComponent(id)}`)
export const fetchRetailerSalespeople = (id) => apiRequest(`/api/retailers/${encodeURIComponent(id)}/salespeople`)
export const addRetailerSalesperson = (retailerId, salespersonId) => apiRequest(`/api/retailers/${encodeURIComponent(retailerId)}/salespeople/${encodeURIComponent(salespersonId)}`, { method: 'POST' })
export const removeRetailerSalesperson = (retailerId, salespersonId) => apiRequest(`/api/retailers/${encodeURIComponent(retailerId)}/salespeople/${encodeURIComponent(salespersonId)}`, { method: 'DELETE' })
export const fetchAdminSchemes = (filters = {}) => apiRequest(`/api/schemes${query(filters)}`)
export const createAdminScheme = (body) => apiRequest('/api/schemes', { method: 'POST', body })
export const updateAdminScheme = (id, body) => apiRequest(`/api/schemes/${encodeURIComponent(id)}`, { method: 'PUT', body })
export const deactivateAdminScheme = (id) => apiRequest(`/api/schemes/${encodeURIComponent(id)}`, { method: 'DELETE' })
export const setDefaultPrice = (productId, body) => apiRequest(`/api/pricing/products/${encodeURIComponent(productId)}/default`, { method: 'PUT', body })
export const setRetailerPrice = (retailerId, productId, body) => apiRequest(`/api/pricing/retailers/${encodeURIComponent(retailerId)}/products/${encodeURIComponent(productId)}`, { method: 'PUT', body })
export const clearRetailerPrice = (retailerId, productId) => apiRequest(`/api/pricing/retailers/${encodeURIComponent(retailerId)}/products/${encodeURIComponent(productId)}`, { method: 'DELETE' })

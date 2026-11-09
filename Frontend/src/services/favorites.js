import { apiRequest } from './api.js'

export const fetchRetailerFavorites = retailerId =>
  apiRequest(`/api/retailer/me/favorites?retailerId=${encodeURIComponent(retailerId)}`)

export const saveRetailerFavorite = (retailerId, productId) =>
  apiRequest(`/api/retailer/me/favorites?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'POST', body: { productId },
  })

export const removeRetailerFavorite = (retailerId, productId) =>
  apiRequest(`/api/retailer/me/favorites/${encodeURIComponent(productId)}?retailerId=${encodeURIComponent(retailerId)}`, {
    method: 'DELETE',
  })

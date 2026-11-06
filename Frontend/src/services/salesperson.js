import { apiRequest } from './api.js'

/**
 * Fetch retailers assigned to the authenticated salesperson.
 * Resolves salesperson identity from the authenticated JWT server-side.
 *
 * @returns {Promise<Array<Object>>} List of RetailerResponse objects
 */
export function fetchAssignedRetailers() {
  return apiRequest('/api/salesperson/retailers', {
    method: 'GET',
  })
}

/**
 * Fetch a specific assigned retailer by ID.
 *
 * @param {string} retailerId
 * @returns {Promise<Object>} RetailerResponse
 */
export function fetchAssignedRetailer(retailerId) {
  return apiRequest(`/api/salesperson/retailers/${encodeURIComponent(retailerId)}`, {
    method: 'GET',
  })
}

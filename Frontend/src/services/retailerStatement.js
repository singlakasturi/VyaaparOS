import { apiRequest } from './api.js'

export function fetchRetailerStatementDistributor(retailerId) {
  return apiRequest(`/api/retailer/me/distributors?retailerId=${encodeURIComponent(retailerId)}`)
}

export function fetchRetailerStatement({ retailerId, distributorId, fromDate, toDate }) {
  const params = new URLSearchParams({ retailerId, fromDate, toDate })
  return apiRequest(`/api/retailer/me/distributors/${encodeURIComponent(distributorId)}/statement?${params}`)
}

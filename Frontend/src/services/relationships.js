import { apiRequest } from './api.js'

export const discoverDistributors = (q) => apiRequest(`/api/retailer-relationships/distributors?q=${encodeURIComponent(q)}`)
export const fetchRelationshipRequests = (retailerId) => apiRequest(`/api/retailer-relationships/requests?retailerId=${encodeURIComponent(retailerId)}`)
export const fetchCurrentRetailerDistributor = (retailerId) => apiRequest(`/api/retailer-relationships/current?retailerId=${encodeURIComponent(retailerId)}`)
export const createRelationshipRequest = (request) => apiRequest('/api/retailer-relationships/requests', { method: 'POST', body: request })
export const cancelRelationshipRequest = (id) => apiRequest(`/api/retailer-relationships/requests/${encodeURIComponent(id)}`, { method: 'DELETE' })
export const fetchInvitationPreview = (token) => apiRequest(`/api/retailer-relationships/invitations/${encodeURIComponent(token)}`)
export const acceptRelationshipInvitation = (token, retailerId) => apiRequest(`/api/retailer-relationships/invitations/${encodeURIComponent(token)}/accept`, { method: 'POST', body: { retailerId } })
export const fetchDistributorRequests = () => apiRequest('/api/distributor/me/retailer-requests')
export const approveDistributorRequest = (id) => apiRequest(`/api/distributor/me/retailer-requests/${encodeURIComponent(id)}/approve`, { method: 'POST' })
export const rejectDistributorRequest = (id) => apiRequest(`/api/distributor/me/retailer-requests/${encodeURIComponent(id)}/reject`, { method: 'POST' })
export const fetchDistributorRetailers = () => apiRequest('/api/distributor/me/retailers')
export const removeDistributorRetailer = (id) => apiRequest(`/api/distributor/me/retailers/${encodeURIComponent(id)}`, { method: 'DELETE' })
export const createRetailerInvitation = (email) => apiRequest('/api/distributor/me/retailer-invitations', { method: 'POST', body: { email } })
export const fetchRetailerInvitations = () => apiRequest('/api/distributor/me/retailer-invitations')
export const revokeRetailerInvitation = (id) => apiRequest(`/api/distributor/me/retailer-invitations/${encodeURIComponent(id)}`, { method: 'DELETE' })

export function relationshipViewState({ loading, error, activeRelationship, requests }) {
  if (loading) return 'loading'
  if (error) return 'error'
  if (activeRelationship) return 'active'
  return requests?.some((request) => request.status === 'PENDING') ? 'pending' : 'unlinked'
}

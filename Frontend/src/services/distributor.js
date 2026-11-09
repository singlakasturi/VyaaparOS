import { apiRequest } from './api.js'

export function fetchCurrentDistributor() {
  return apiRequest('/api/distributor/me', { method: 'GET' })
}

export function updateCurrentDistributor(details) {
  return apiRequest('/api/distributor/me', { method: 'PUT', body: details })
}

export function fetchDistributorStaff() {
  return apiRequest('/api/distributor/me/staff', { method: 'GET' })
}

export function addDistributorStaff(email) {
  return apiRequest('/api/distributor/me/staff', { method: 'POST', body: { email } })
}

export function revokeDistributorStaff(userId) {
  return apiRequest(`/api/distributor/me/staff/${encodeURIComponent(userId)}`, { method: 'DELETE' })
}

export function canManageDistributorStaff(business) {
  return Boolean(business?.canManageStaff)
}

export function validateDistributorBusinessName(value) {
  const name = String(value ?? '').trim()
  return name.length >= 1 && name.length <= 200
}

export function getDistributorStaffListState({ loading, error, staff }) {
  if (loading) return 'loading'
  if (error) return 'error'
  return Array.isArray(staff) && staff.length > 0 ? 'success' : 'empty'
}

export function getWorkspaceAvailability({ distributor, retailers }) {
  return {
    distributor: Boolean(distributor),
    retailer: Array.isArray(retailers) && retailers.length > 0,
    empty: !distributor && (!Array.isArray(retailers) || retailers.length === 0),
  }
}

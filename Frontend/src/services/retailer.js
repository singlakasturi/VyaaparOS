import { apiRequest } from './api.js'

export function fetchMyRetailers() {
  return apiRequest('/api/retailers/mine', { method: 'GET' })
}

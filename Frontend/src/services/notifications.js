import { apiRequest } from './api.js'

export function fetchNotifications(page = 0, size = 20) {
  return apiRequest(`/api/notifications?page=${page}&size=${size}`)
}
export function fetchUnreadNotificationCount() {
  return apiRequest('/api/notifications/unread-count')
}
export function markNotificationRead(id) {
  return apiRequest(`/api/notifications/${id}/read`, { method: 'PATCH' })
}
export function markAllNotificationsRead() {
  return apiRequest('/api/notifications/read-all', { method: 'PATCH' })
}

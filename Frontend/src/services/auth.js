import { apiRequest, setAccessToken } from './api'

export function requestOtp(email) {
  return apiRequest('/api/auth/otp/request', {
    method: 'POST',
    body: { email },
  })
}

export async function verifyOtp(email, otp) {
  const response = await apiRequest('/api/auth/otp/verify', {
    method: 'POST',
    body: { email, otp },
  })
  if (!response?.accessToken || !response?.user) {
    throw new Error('The server returned an incomplete authentication response.')
  }
  setAccessToken(response.accessToken)
  return response
}

export function signOut() {
  setAccessToken(null)
}

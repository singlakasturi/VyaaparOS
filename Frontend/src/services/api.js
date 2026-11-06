const API_BASE_URL = (
  import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.DEV || typeof window === 'undefined' ? 'http://localhost:8080' : '')
).replace(/\/+$/, '')
const ACCESS_TOKEN_KEY = 'vyapaaros.accessToken'
let backendHealthPromise = null
let backendHealthCheckedAt = 0
let backendHealthResult = false

export class ApiError extends Error {
  constructor(message, status, details = [], data = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
    this.data = data
  }
}

export function getAccessToken() {
  return window.sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token) {
  if (token) window.sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
  else window.sessionStorage.removeItem(ACCESS_TOKEN_KEY)
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    try {
      return await response.json()
    } catch {
      return null
    }
  }
  return await response.text()
}

export async function apiRequest(path, options = {}) {
  const { body, headers = {}, ...requestOptions } = options
  const token = getAccessToken()
  const requestHeaders = {
    Accept: 'application/json',
    ...headers,
  }

  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'
  if (token) requestHeaders.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestOptions,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Unable to reach the server. Please try again.', 0)
  }

  const payload = await parseResponse(response)

  if (response.status === 401) {
    setAccessToken(null)
    window.dispatchEvent(new CustomEvent('vyapaaros:session-expired'))
  }

  if (!response.ok) {
    const message = typeof payload === 'string'
      ? payload
      : payload?.message || payload?.error || 'Something went wrong. Please try again.'
    throw new ApiError(message, response.status, payload?.details || [], payload?.data || {})
  }

  return payload
}

export function checkBackendReachable({ force = false } = {}) {
  if (backendHealthPromise) return backendHealthPromise
  if (!force && Date.now() - backendHealthCheckedAt < 5000) {
    return Promise.resolve(backendHealthResult)
  }
  backendHealthPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/actuator/health/readiness`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        cache: 'no-store',
      })
      backendHealthResult = response.ok
    } catch {
      backendHealthResult = false
    }
    backendHealthCheckedAt = Date.now()
    return backendHealthResult
  })().finally(() => { backendHealthPromise = null })
  return backendHealthPromise
}

export async function apiDownload(path, options = {}) {
  const { body, headers = {}, ...requestOptions } = options
  const token = getAccessToken()
  const requestHeaders = { Accept: '*/*', ...headers }
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'
  if (token) requestHeaders.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestOptions,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Unable to reach the server. Please try again.', 0)
  }

  if (response.status === 401) {
    setAccessToken(null)
    window.dispatchEvent(new CustomEvent('vyapaaros:session-expired'))
  }
  if (!response.ok) {
    const payload = await parseResponse(response)
    const message = typeof payload === 'string'
      ? payload
      : payload?.message || payload?.error || 'Something went wrong. Please try again.'
    throw new ApiError(message, response.status, payload?.details || [])
  }

  const disposition = response.headers.get('content-disposition') || ''
  const encodedFilename = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const filename = encodedFilename || disposition.match(/filename="?([^";]+)"?/i)?.[1]
  let decodedFilename = filename || null
  if (encodedFilename) {
    try { decodedFilename = decodeURIComponent(encodedFilename) } catch { decodedFilename = encodedFilename }
  }
  return { blob: await response.blob(), filename: decodedFilename }
}

export { API_BASE_URL, ACCESS_TOKEN_KEY }

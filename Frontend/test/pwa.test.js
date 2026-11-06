import test from 'node:test'
import assert from 'node:assert/strict'
import { getInstallChoiceMessage, getInstallHelpText, isBackendReachableFromApiError } from '../src/services/pwa.js'
import { checkBackendReachable } from '../src/services/api.js'

test('PWA install guidance identifies origin, service worker and browser requirements', () => {
  assert.match(getInstallHelpText('Chrome/140', { secureContext: false, serviceWorkerReady: true }), /HTTPS/)
  assert.match(getInstallHelpText('Chrome/140', { secureContext: true, serviceWorkerReady: false }), /service worker/)
  assert.match(getInstallHelpText('iPhone Safari', { secureContext: true, serviceWorkerReady: true }), /Safari/)
  assert.match(getInstallHelpText('Chrome/140', { secureContext: true, serviceWorkerReady: true }), /Chrome/)
})

test('PWA copy does not claim that an installation completed without browser confirmation', () => {
  assert.match(getInstallChoiceMessage('accepted'), /confirmed only after the browser reports it complete/)
  assert.match(getInstallChoiceMessage('dismissed'), /dismissed/)
})

test('connectivity distinguishes browser online state from API reachability', () => {
  assert.equal(isBackendReachableFromApiError({ status: 401 }), true)
  assert.equal(isBackendReachableFromApiError({ status: 0 }), false)
  assert.equal(isBackendReachableFromApiError(new Error('network failure')), false)
})

test('backend reachability uses the readiness endpoint and reports a healthy response', async () => {
  const originalFetch = globalThis.fetch
  let requestedUrl = ''
  globalThis.fetch = async (url) => {
    requestedUrl = url
    return { ok: true, status: 200 }
  }
  try {
    assert.equal(await checkBackendReachable({ force: true }), true)
    assert.match(requestedUrl, /\/actuator\/health\/readiness$/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('backend readiness fails when the server is unavailable even if the browser can fetch', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => ({ ok: false, status: 503 })
  try {
    assert.equal(await checkBackendReachable({ force: true }), false)
  } finally {
    globalThis.fetch = originalFetch
  }
})

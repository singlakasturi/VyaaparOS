export function getInstallHelpText(userAgent, { secureContext, serviceWorkerReady }) {
  if (!secureContext) return 'Installation requires HTTPS, or localhost during development. Open this site from a secure origin.'
  if (!serviceWorkerReady) return 'The offline service worker is not ready yet. Keep the site open, then check your browser menu again.'
  if (/iPhone|iPad|iPod/i.test(userAgent)) return 'In Safari, tap Share, then choose “Add to Home Screen”.'
  if (/Android/i.test(userAgent)) return 'In Chrome on Android, open ⋮ and choose “Install app” or “Add to Home screen”.'
  if (/Edg\//i.test(userAgent)) return 'In Microsoft Edge, open Settings and more, then Apps → Install this site as an app.'
  if (/Chrome\//i.test(userAgent)) return 'In Chrome, open ⋮ and choose “Install VyapaarOS” or “Save and share → Install page as app”.'
  return 'Open your browser menu and look for “Install app” or “Add to Home Screen”. This browser may not support PWA installation.'
}

export function getInstallChoiceMessage(outcome) {
  return outcome === 'accepted'
    ? 'Your browser accepted the install request. Installation is confirmed only after the browser reports it complete.'
    : 'Installation was dismissed. You can try again from this button or your browser menu.'
}

export function isBackendReachableFromApiError(error) {
  return Number.isFinite(error?.status) && error.status > 0
}

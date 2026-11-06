import { useEffect, useState } from 'react'
import { Download, RefreshCw, Wifi, WifiOff, X } from 'lucide-react'
import { registerSW } from 'virtual:pwa-register'
import { getInstallChoiceMessage, getInstallHelpText } from '../../services/pwa.js'

function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true
}

export default function PwaExperience() {
  const [networkNotice, setNetworkNotice] = useState(null)
  const [installEvent, setInstallEvent] = useState(null)
  const [installed, setInstalled] = useState(isStandalone)
  const [showInstallHelp, setShowInstallHelp] = useState(false)
  const [updateSW, setUpdateSW] = useState(null)
  const [updateAvailable, setUpdateAvailable] = useState(false)
  const [offlineReady, setOfflineReady] = useState(false)
  const [installing, setInstalling] = useState(false)
  const [installMessage, setInstallMessage] = useState('')
  const [serviceWorkerReady, setServiceWorkerReady] = useState(false)

  useEffect(() => {
    let noticeTimer
    const showNetworkNotice = (status) => {
      setNetworkNotice(status)
      window.clearTimeout(noticeTimer)
      noticeTimer = window.setTimeout(() => setNetworkNotice(null), 5000)
    }
    const handleOffline = () => showNetworkNotice('offline')
    const handleOnline = () => showNetworkNotice('online')
    const handleInstallPrompt = (event) => {
      event.preventDefault()
      if (!isStandalone()) setInstallEvent(event)
    }
    const handleInstalled = () => { setInstalled(true); setInstallEvent(null); setShowInstallHelp(false); setInstallMessage('VyapaarOS installation was confirmed by your browser.') }

    window.addEventListener('offline', handleOffline)
    window.addEventListener('online', handleOnline)
    window.addEventListener('beforeinstallprompt', handleInstallPrompt)
    window.addEventListener('appinstalled', handleInstalled)
    const update = registerSW({
      immediate: true,
      onNeedRefresh() { setUpdateAvailable(true) },
      onOfflineReady() { setOfflineReady(true) },
    })
    setUpdateSW((current) => current || update)
    if (navigator.serviceWorker) navigator.serviceWorker.getRegistration().then((registration) => setServiceWorkerReady(Boolean(registration))).catch(() => setServiceWorkerReady(false))

    return () => {
      window.clearTimeout(noticeTimer)
      window.removeEventListener('offline', handleOffline)
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('beforeinstallprompt', handleInstallPrompt)
      window.removeEventListener('appinstalled', handleInstalled)
    }
  }, [])

  const install = async () => {
    if (!installEvent) return
    setInstalling(true)
    try {
      await installEvent.prompt()
      const choice = await installEvent.userChoice
      setInstallMessage(getInstallChoiceMessage(choice?.outcome))
    } catch {
      setInstallMessage('The browser could not open its install prompt. Use the browser menu instructions below.')
      setShowInstallHelp(true)
    } finally {
      setInstallEvent(null)
      setInstalling(false)
    }
  }

  return (
    <>
      {networkNotice === 'offline' && <div className="pwa-connectivity" role="status" aria-live="polite">
        <WifiOff size={17} aria-hidden="true" />
        <span><strong>You’re offline.</strong> Saved drafts stay on this device until your connection returns.</span>
      </div>}
      {networkNotice === 'online' && <div className="pwa-connectivity pwa-connectivity-online" role="status" aria-live="polite">
        <Wifi size={17} aria-hidden="true" />
        <span><strong>Connection restored.</strong> Pending drafts can be retried from Orders.</span>
      </div>}
      {installMessage && <div className="pwa-install-card" role="status" aria-live="polite"><span>{installMessage}</span><button type="button" aria-label="Dismiss install status" onClick={() => setInstallMessage('')}><X size={17} /></button></div>}
      {offlineReady && <div className="pwa-install-card" role="status">
        <span>VyapaarOS is ready to open offline. Previously loaded data may be out of date.</span>
        <button type="button" aria-label="Dismiss offline-ready message" onClick={() => setOfflineReady(false)}><X size={17} /></button>
      </div>}
      {!installed && (installEvent || showInstallHelp) && <div className="pwa-install-card" aria-live="polite">
        <div className="pwa-install-copy"><strong>Use VyapaarOS like an app</strong><span>{installEvent ? 'Install for quicker access from your device.' : getInstallHelpText(navigator.userAgent || '', { secureContext: window.isSecureContext, serviceWorkerReady })}</span></div>
        <div className="pwa-install-actions">
          {installEvent && <button className="pwa-install-button" type="button" disabled={installing} onClick={install}><Download size={16} />{installing ? 'Opening…' : 'Install'}</button>}
          {!installEvent && <button className="pwa-dismiss-button" type="button" onClick={() => setShowInstallHelp(false)}>Got it</button>}
          <button className="pwa-dismiss-button" type="button" aria-label="Close install guidance" onClick={() => { setInstallEvent(null); setShowInstallHelp(false) }}><X size={17} /></button>
        </div>
      </div>}
      {!installed && !installEvent && !showInstallHelp && <button className="pwa-help-button" type="button" onClick={() => setShowInstallHelp(true)}><Download size={16} />Install help</button>}
      {updateAvailable && updateSW && <div className="pwa-update-card" aria-live="polite">
        <span>A VyapaarOS update is ready.</span>
        <button type="button" className="pwa-install-button" onClick={() => updateSW(true)}><RefreshCw size={15} />Refresh app</button>
      </div>}
    </>
  )
}

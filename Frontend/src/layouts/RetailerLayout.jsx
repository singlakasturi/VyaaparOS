import { useCallback, useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { ChevronDown, LoaderCircle } from 'lucide-react'
import {
  MobileBottomNavigation,
  MobileHeader,
  SidebarNavigation,
} from '../components/common/Navigation'
import { retailerNavigation } from '../components/common/navigationConfig'
import { useAuth } from '../services/AuthProvider'
import { fetchMyRetailers } from '../services/retailer.js'
import { fetchCart } from '../services/cart.js'
import { getDraftsByRetailer } from '../services/db.js'
import { loadRetailerCart } from '../services/retailerOrdering.js'
import { checkBackendReachable } from '../services/api.js'
import { autoSyncRetailerOrders } from '../services/sync.js'
import './RetailerOrdering.css'

const SELECTED_KEY = 'vyapaaros.activeRetailer'

function readSelected() {
  try { return window.sessionStorage.getItem(SELECTED_KEY) || '' } catch { return '' }
}

export default function RetailerLayout() {
  const [isNavigationOpen, setNavigationOpen] = useState(false)
  const [retailers, setRetailers] = useState([])
  const [retailerId, setRetailerId] = useState(readSelected)
  const [cartCount, setCartCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { logout, session } = useAuth()

  const loadRetailers = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await fetchMyRetailers()
      const available = Array.isArray(data) ? data : []
      setRetailers(available)
      const selected = available.some((item) => item.id === retailerId)
        ? retailerId
        : available.length === 1 ? available[0].id : ''
      setRetailerId(selected)
      if (selected) window.sessionStorage.setItem(SELECTED_KEY, selected)
      else window.sessionStorage.removeItem(SELECTED_KEY)
    } catch (err) {
      setError(err.message || 'Could not load your retailer account.')
    } finally {
      setLoading(false)
    }
  }, [retailerId])

  useEffect(() => { loadRetailers() }, [loadRetailers])
  const retailer = retailers.find((item) => item.id === retailerId) || null

  useEffect(() => {
    let alive = true
    if (!retailerId) { setCartCount(0); return () => { alive = false } }
    loadRetailerCart({ retailerId, userId: session?.user?.id, fetchCart, getDraftsByRetailer, isOnline: navigator.onLine }).then((cart) => {
      if (alive) setCartCount((cart?.items || []).reduce((sum, item) => sum + item.paidCases, 0))
    }).catch(async () => {
      if (!alive) return
      const userId = session?.user?.id
      const drafts = userId ? await getDraftsByRetailer(retailerId, userId).catch(() => []) : []
      const offlineCart = drafts.find((draft) => draft.isOfflineWorkingCart && draft.userId === userId)
      setCartCount((offlineCart?.items || []).reduce((sum, item) => sum + Number(item.paidCases || 0), 0))
    })
    return () => { alive = false }
  }, [retailerId, session?.user?.id])

  useEffect(() => {
    let active = true
    let checkInFlight = null
    let lastAutoSyncCheckAt = 0
    const trySyncPendingOrders = async ({ forceHealthCheck = false } = {}) => {
      if (checkInFlight) return checkInFlight
      if (Date.now() - lastAutoSyncCheckAt < 5000) return
      lastAutoSyncCheckAt = Date.now()
      const userId = session?.user?.id
      if (!retailerId || !userId) return
      if (!navigator.onLine) {
        window.dispatchEvent(new CustomEvent('vyapaaros:backend-status', { detail: { reachable: false } }))
        return
      }
      checkInFlight = (async () => {
        const drafts = await getDraftsByRetailer(retailerId, userId).catch(() => [])
        const result = await autoSyncRetailerOrders(drafts, {
          isOnline: navigator.onLine,
          healthCheck: () => checkBackendReachable({ force: forceHealthCheck }),
        })
        if (!active) return
        if (result.reachable !== null) {
          window.dispatchEvent(new CustomEvent('vyapaaros:backend-status', { detail: { reachable: result.reachable } }))
        }
      })().finally(() => { checkInFlight = null })
      return checkInFlight
    }

    const syncOnReconnect = () => { lastAutoSyncCheckAt = 0; trySyncPendingOrders({ forceHealthCheck: true }) }
    const syncOnWake = () => {
      if (document.visibilityState === 'visible') trySyncPendingOrders()
    }
    trySyncPendingOrders()
    window.addEventListener('online', syncOnReconnect)
    window.addEventListener('focus', syncOnWake)
    document.addEventListener('visibilitychange', syncOnWake)
    return () => {
      active = false
      window.removeEventListener('online', syncOnReconnect)
      window.removeEventListener('focus', syncOnWake)
      document.removeEventListener('visibilitychange', syncOnWake)
    }
  }, [retailerId, session?.user?.id])

  const selectRetailer = (id) => {
    setRetailerId(id)
    window.sessionStorage.setItem(SELECTED_KEY, id)
  }

  return (
    <div className="app-layout retailer-app-layout">
      <SidebarNavigation isOpen={isNavigationOpen} items={retailerNavigation} title="Retailer" onSignOut={logout} />
      <div className="app-main">
        <MobileHeader title="retailer" onMenuClick={() => setNavigationOpen((open) => !open)} />
        <header className="retailer-topbar">
          <div className="retailer-topbar-copy">
            <span className="retailer-eyebrow">RETAILER WORKSPACE</span>
            <strong>{retailer?.businessName || session?.user?.fullName || 'Your account'}</strong>
          </div>
          {retailers.length > 1 && (
            <label className="retailer-switcher">
              <span className="sr-only">Select business</span>
              <select aria-label="Select business" value={retailerId} onChange={(event) => selectRetailer(event.target.value)}>
                <option value="">Choose a business</option>
                {retailers.map((item) => <option key={item.id} value={item.id}>{item.businessName}</option>)}
              </select>
              <ChevronDown size={16} aria-hidden="true" />
            </label>
          )}
          <Link className="retailer-cart-indicator" to="/retailer/cart" aria-label={`Cart, ${cartCount} cases`}>
            <span>Cart</span><b>{cartCount}</b>
          </Link>
        </header>
        <main className="app-content retailer-content">
          {loading ? <div className="retailer-state"><LoaderCircle className="retailer-spin" />Loading your account…</div>
            : error ? <div className="retailer-state retailer-error" role="alert">{error}<button className="retailer-button retailer-button-outline" onClick={loadRetailers}>Retry</button></div>
              : !retailers.length ? <div className="retailer-state retailer-empty"><h1>No retailer business linked</h1><p>This account does not have an active retailer membership yet.</p></div>
                : !retailer ? <div className="retailer-state"><h1>Select a business</h1><p>Choose a retailer membership to continue ordering.</p><select aria-label="Select business" onChange={(event) => selectRetailer(event.target.value)} defaultValue=""><option value="">Choose a business</option>{retailers.map((item) => <option key={item.id} value={item.id}>{item.businessName}</option>)}</select></div>
                  : <Outlet context={{ retailer, retailerId, cartCount, setCartCount, userId: session?.user?.id }} />}
        </main>
      </div>
      <MobileBottomNavigation items={retailerNavigation} />
    </div>
  )
}

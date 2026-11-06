import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  Brand,
  SidebarNavigation,
  MobileBottomNavigation,
} from '../components/common/Navigation'
import { salespersonNavigation } from '../components/common/navigationConfig'
import { useAuth } from '../services/AuthProvider'
import { useSalesperson } from '../services/SalespersonProvider'
import { Store, ArrowLeftRight, Menu, AlertCircle, Wifi, WifiOff, CloudOff } from 'lucide-react'

function SalespersonHeader({ onMenuClick }) {
  const { selectedRetailer, isOnline, pendingDraftsCount } = useSalesperson()
  const navigate = useNavigate()

  return (
    <header className="mobile-header sp-mobile-header">
      <div className="sp-mobile-brand-box">
        <Brand />
        <div className="sp-header-status-group">
          {/* Online/Offline status badge */}
          <span className={`sp-online-pill ${isOnline ? 'pill-online' : 'pill-offline'}`}>
            {isOnline ? <Wifi size={11} /> : <WifiOff size={11} />}
            <span>{isOnline ? 'Online' : 'Offline'}</span>
          </span>

          {/* Pending drafts counter */}
          {pendingDraftsCount > 0 && (
            <button
              type="button"
              className="sp-drafts-counter-pill"
              onClick={() => navigate('/salesperson/drafts')}
              title={`${pendingDraftsCount} offline drafts pending sync`}
            >
              <CloudOff size={11} />
              <span>{pendingDraftsCount}</span>
            </button>
          )}

          {selectedRetailer && (
            <div className="sp-mobile-retailer-pill">
              <Store size={12} />
              <span className="sp-mobile-retailer-name">{selectedRetailer.businessName}</span>
            </div>
          )}
        </div>
      </div>
      <button
        aria-label="Open sales team navigation"
        className="navigation-menu-button"
        onClick={onMenuClick}
        type="button"
      >
        <Menu aria-hidden="true" size={22} />
      </button>
    </header>
  )
}

function RetailerContextBar() {
  const { selectedRetailer, isOnline, isCachedData, cachedAt, pendingDraftsCount } = useSalesperson()
  const navigate = useNavigate()
  const location = useLocation()

  const isRetailerList =
    location.pathname === '/salesperson' || location.pathname === '/salesperson/retailers'
  const isDraftsPage = location.pathname === '/salesperson/drafts'

  const formatCurrency = (val) => {
    const num = Number(val) || 0
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(num)
  }

  return (
    <div className="sp-top-bar-container">
      {/* Network & Draft Status Strip */}
      <div className="sp-network-strip">
        <div className="sp-strip-left">
          <span className={`sp-network-indicator ${isOnline ? 'is-online' : 'is-offline'}`}>
            {isOnline ? <Wifi size={13} /> : <WifiOff size={13} />}
            <span>{isOnline ? 'Connected' : 'Offline Mode — Local Drafts Active'}</span>
          </span>
          {isCachedData && cachedAt && (
            <span className="sp-cached-timestamp-label">
              (Data cached {new Date(cachedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
            </span>
          )}
        </div>

        <div className="sp-strip-right">
          {pendingDraftsCount > 0 && !isDraftsPage && (
            <button
              type="button"
              className="sp-btn-pending-drafts"
              onClick={() => navigate('/salesperson/drafts')}
            >
              <CloudOff size={13} />
              <span>{pendingDraftsCount} pending draft{pendingDraftsCount === 1 ? '' : 's'}</span>
            </button>
          )}
        </div>
      </div>

      {/* Retailer Context Banner */}
      {!selectedRetailer ? (
        !isRetailerList && !isDraftsPage ? (
          <div className="sp-context-banner sp-context-none card">
            <div className="sp-context-msg">
              <AlertCircle size={16} />
              <span>No retailer selected. Please choose an assigned retailer before placing orders.</span>
            </div>
            <button
              className="btn btn-primary sp-context-btn"
              onClick={() => navigate('/salesperson/retailers')}
              type="button"
            >
              <Store size={14} />
              Choose Retailer
            </button>
          </div>
        ) : null
      ) : (
        <div className="sp-context-banner sp-context-active card">
          <div className="sp-context-main">
            <div className="sp-context-icon">
              <Store size={18} />
            </div>
            <div className="sp-context-text">
              <div className="sp-context-title-row">
                <span className="sp-context-label">Ordering on behalf of:</span>
                <strong className="sp-context-name">{selectedRetailer.businessName}</strong>
                <span
                  className={`sp-status-badge ${
                    selectedRetailer.status === 'ACTIVE'
                      ? 'sp-badge-active'
                      : 'sp-badge-pending'
                  }`}
                >
                  {selectedRetailer.status}
                </span>
                {isCachedData && (
                  <span className="sp-badge-cached" title="Prices and credit are estimates from cached data">
                    Cached Context
                  </span>
                )}
              </div>
              <div className="sp-context-sub">
                {selectedRetailer.city && <span>{selectedRetailer.city}</span>}
                {selectedRetailer.city && selectedRetailer.contactPhone && <span>•</span>}
                {selectedRetailer.contactPhone && <span>{selectedRetailer.contactPhone}</span>}
                <span>•</span>
                <span className="sp-context-credit">
                  Avail. Credit:{' '}
                  <strong>
                    {formatCurrency(
                      Math.max(
                        0,
                        (Number(selectedRetailer.creditLimit) || 0) -
                          (Number(selectedRetailer.outstandingBalance) || 0)
                      )
                    )}
                  </strong>
                </span>
              </div>
            </div>
          </div>

          <div className="sp-context-actions">
            <button
              className="btn btn-secondary sp-context-switch-btn"
              onClick={() => {
                navigate('/salesperson/retailers')
              }}
              type="button"
              title="Return to assigned retailer list to switch"
            >
              <ArrowLeftRight size={14} />
              Switch Retailer
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function SalespersonLayout() {
  const [isNavigationOpen, setNavigationOpen] = useState(false)
  const { logout } = useAuth()

  return (
    <div className="app-layout">
      <SidebarNavigation
        isOpen={isNavigationOpen}
        items={salespersonNavigation}
        title="Field sales"
        onSignOut={logout}
      />
      <div className="app-main">
        <SalespersonHeader
          onMenuClick={() => setNavigationOpen((isOpen) => !isOpen)}
        />
        <main className="app-content sp-main-content">
          <RetailerContextBar />
          <Outlet />
        </main>
      </div>
      <MobileBottomNavigation items={salespersonNavigation} />
    </div>
  )
}

export default SalespersonLayout

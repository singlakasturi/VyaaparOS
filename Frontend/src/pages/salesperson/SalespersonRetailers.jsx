import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Store,
  Search,
  Phone,
  Mail,
  MapPin,
  ArrowRight,
  RefreshCw,
  AlertCircle,
  Building2,
  Sparkles,
  ShoppingCart,
  Package,
} from 'lucide-react'
import { useSalesperson } from '../../services/SalespersonProvider'

export default function SalespersonRetailers() {
  const {
    filteredRetailers,
    selectedRetailer,
    selectRetailer,
    searchQuery,
    setSearchQuery,
    isLoading,
    error,
    reloadRetailers,
  } = useSalesperson()

  const navigate = useNavigate()
  const [statusFilter, setStatusFilter] = useState('ALL') // ALL, ACTIVE, PENDING

  const displayedRetailers = filteredRetailers.filter((r) => {
    if (statusFilter === 'ALL') return true
    return r.status === statusFilter
  })

  const handleSelectAndOrder = (retailer) => {
    selectRetailer(retailer)
    navigate('/salesperson/catalogue')
  }

  const handleSelectAndAi = (retailer) => {
    selectRetailer(retailer)
    navigate('/salesperson/ai-assistant')
  }

  const formatCurrency = (val) => {
    const num = Number(val) || 0
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(num)
  }

  return (
    <div className="sp-page">
      {/* Page Header */}
      <div className="sp-header">
        <div className="sp-header-left">
          <div className="sp-icon-wrap">
            <Store size={24} />
          </div>
          <div>
            <div className="sp-title-row">
              <h1 className="sp-title">Assigned Retailers</h1>
              <span className="badge">{filteredRetailers.length} Assigned</span>
            </div>
            <p className="sp-subtitle">
              Select a retailer to browse catalogue, build carts, place orders, or use the AI assistant.
            </p>
          </div>
        </div>
      </div>

      {/* Currently Selected Retailer Quick Banner */}
      {selectedRetailer && (
        <div className="sp-active-card card">
          <div className="sp-active-content">
            <div className="sp-active-info">
              <span className="sp-active-eyebrow">Active Ordering Context</span>
              <h2 className="sp-active-name">{selectedRetailer.businessName}</h2>
              <p className="sp-active-meta">
                {selectedRetailer.city && <span><MapPin size={13} /> {selectedRetailer.city}</span>}
                {selectedRetailer.contactPhone && <span><Phone size={13} /> {selectedRetailer.contactPhone}</span>}
              </p>
            </div>
            <div className="sp-active-actions">
              <button
                className="btn btn-primary"
                onClick={() => navigate('/salesperson/catalogue')}
                type="button"
              >
                <Package size={16} />
                Catalogue
              </button>
              <button
                className="btn btn-secondary"
                onClick={() => navigate('/salesperson/ai-assistant')}
                type="button"
              >
                <Sparkles size={16} />
                AI Assistant
              </button>
              <button
                className="btn btn-secondary"
                onClick={() => navigate('/salesperson/cart')}
                type="button"
              >
                <ShoppingCart size={16} />
                Cart
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Search & Filters */}
      <div className="sp-controls card">
        <div className="sp-search-wrap">
          <Search size={18} className="sp-search-icon" />
          <input
            className="input sp-search-input"
            type="search"
            placeholder="Search by retailer name, phone, city..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            aria-label="Search retailers"
          />
        </div>
        <div className="sp-filters">
          <button
            className={`sp-filter-btn ${statusFilter === 'ALL' ? 'sp-filter-active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
            type="button"
          >
            All
          </button>
          <button
            className={`sp-filter-btn ${statusFilter === 'ACTIVE' ? 'sp-filter-active' : ''}`}
            onClick={() => setStatusFilter('ACTIVE')}
            type="button"
          >
            Active
          </button>
          <button
            className={`sp-filter-btn ${statusFilter === 'PENDING' ? 'sp-filter-active' : ''}`}
            onClick={() => setStatusFilter('PENDING')}
            type="button"
          >
            Pending
          </button>
        </div>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="sp-loading card">
          <RefreshCw size={24} className="sp-spinner" />
          <p>Loading assigned retailers…</p>
        </div>
      )}

      {/* Error State */}
      {error && !isLoading && (
        <div className="sp-error card">
          <AlertCircle size={24} className="sp-error-icon" />
          <div>
            <h3 className="sp-error-title">Unable to load retailers</h3>
            <p className="sp-error-message">{error}</p>
          </div>
          <button className="btn btn-secondary" onClick={reloadRetailers} type="button">
            <RefreshCw size={16} />
            Try Again
          </button>
        </div>
      )}

      {/* Empty State: No Retailers Assigned */}
      {!isLoading && !error && filteredRetailers.length === 0 && !searchQuery && (
        <div className="sp-empty card">
          <Building2 size={40} className="sp-empty-icon" />
          <h2>No Assigned Retailers</h2>
          <p>You have not been assigned any retailers yet. Please contact your sales manager or administrator.</p>
        </div>
      )}

      {/* Empty Search Results */}
      {!isLoading && !error && displayedRetailers.length === 0 && searchQuery && (
        <div className="sp-empty card">
          <Search size={36} className="sp-empty-icon" />
          <h2>No Matching Retailers</h2>
          <p>No assigned retailers match "{searchQuery}". Try searching with a different term.</p>
          <button className="btn btn-secondary" onClick={() => setSearchQuery('')} type="button">
            Clear Search
          </button>
        </div>
      )}

      {/* Retailers List */}
      {!isLoading && displayedRetailers.length > 0 && (
        <div className="sp-retailers-grid">
          {displayedRetailers.map((retailer) => {
            const isSelected = selectedRetailer?.id === retailer.id
            const creditLimit = Number(retailer.creditLimit) || 0
            const outstanding = Number(retailer.outstandingBalance) || 0
            const availableCredit = Math.max(0, creditLimit - outstanding)

            return (
              <div
                key={retailer.id}
                className={`sp-retailer-card card ${isSelected ? 'sp-retailer-selected' : ''}`}
              >
                <div className="sp-retailer-top">
                  <div className="sp-retailer-main">
                    <h3 className="sp-retailer-name">{retailer.businessName}</h3>
                    {retailer.ownerName && (
                      <p className="sp-retailer-owner">Owner: {retailer.ownerName}</p>
                    )}
                  </div>
                  <span
                    className={`sp-status-badge ${
                      retailer.status === 'ACTIVE'
                        ? 'sp-badge-active'
                        : 'sp-badge-pending'
                    }`}
                  >
                    {retailer.status}
                  </span>
                </div>

                <div className="sp-retailer-details">
                  {retailer.address && (
                    <div className="sp-detail-item">
                      <MapPin size={14} className="sp-detail-icon" />
                      <span>{retailer.address}{retailer.city ? `, ${retailer.city}` : ''}</span>
                    </div>
                  )}
                  {retailer.contactPhone && (
                    <div className="sp-detail-item">
                      <Phone size={14} className="sp-detail-icon" />
                      <span>{retailer.contactPhone}</span>
                    </div>
                  )}
                  {retailer.contactEmail && (
                    <div className="sp-detail-item">
                      <Mail size={14} className="sp-detail-icon" />
                      <span>{retailer.contactEmail}</span>
                    </div>
                  )}
                </div>

                {/* Credit & Ordering Context */}
                <div className="sp-credit-box">
                  <div className="sp-credit-item">
                    <span className="sp-credit-label">Credit Limit</span>
                    <span className="sp-credit-val">{formatCurrency(creditLimit)}</span>
                  </div>
                  <div className="sp-credit-item">
                    <span className="sp-credit-label">Outstanding</span>
                    <span className="sp-credit-val sp-credit-outstanding">
                      {formatCurrency(outstanding)}
                    </span>
                  </div>
                  <div className="sp-credit-item">
                    <span className="sp-credit-label">Available Credit</span>
                    <span className="sp-credit-val sp-credit-available">
                      {formatCurrency(availableCredit)}
                    </span>
                  </div>
                </div>

                {/* Card Actions */}
                <div className="sp-retailer-actions">
                  <button
                    className={`btn ${isSelected ? 'btn-secondary' : 'btn-primary'} sp-action-btn`}
                    onClick={() => handleSelectAndOrder(retailer)}
                    type="button"
                  >
                    <Package size={16} />
                    {isSelected ? 'Open Catalogue' : 'Select & Order'}
                    <ArrowRight size={16} />
                  </button>
                  <button
                    className="btn btn-secondary sp-action-ai-btn"
                    onClick={() => handleSelectAndAi(retailer)}
                    title="Order with AI Assistant"
                    type="button"
                  >
                    <Sparkles size={16} />
                    AI Assistant
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

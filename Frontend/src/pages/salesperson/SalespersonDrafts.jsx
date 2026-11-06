import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CloudOff,
  RefreshCw,
  Trash2,
  Edit3,
  CheckCircle2,
  AlertCircle,
  Clock,
  Send,
  Building2,
  ChevronDown,
  ChevronUp,
  Search,
  Wifi,
  WifiOff,
  ShoppingBag,
} from 'lucide-react'
import { useSalesperson } from '../../services/SalespersonProvider'
import { getAllDrafts, deleteDraft, DRAFT_STATUS } from '../../services/db'
import { syncDraft, syncAllDrafts } from '../../services/sync'

export default function SalespersonDrafts() {
  const { salespersonId, isOnline, refreshDraftsCount, selectRetailer, retailers } = useSalesperson()
  const [drafts, setDrafts] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [syncingDraftId, setSyncingDraftId] = useState(null)
  const [isSyncingAll, setIsSyncingAll] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [expandedDraftId, setExpandedDraftId] = useState(null)
  const [feedback, setFeedback] = useState(null)

  const navigate = useNavigate()

  const loadDrafts = useCallback(async () => {
    setIsLoading(true)
    try {
      const data = await getAllDrafts(salespersonId)
      setDrafts(data)
      refreshDraftsCount()
    } catch (err) {
      setFeedback({ type: 'error', message: 'Failed to load offline drafts: ' + err.message })
    } finally {
      setIsLoading(false)
    }
  }, [salespersonId, refreshDraftsCount])

  useEffect(() => {
    loadDrafts()
  }, [loadDrafts])

  const handleSyncOne = async (draft) => {
    if (!isOnline) {
      setFeedback({ type: 'warning', message: 'Device is offline. Please connect to the internet to sync.' })
      return
    }

    setSyncingDraftId(draft.id)
    setFeedback(null)
    try {
      const updated = await syncDraft(draft.id)
      await loadDrafts()
      if (updated.status === DRAFT_STATUS.CREDIT_HOLD) {
        setFeedback({
          type: 'warning',
          message: `Order submitted on CREDIT HOLD for ${draft.retailerName}: ${updated.serverOrder?.creditHoldReason || 'Exceeds credit limit'}`,
        })
      } else {
        setFeedback({
          type: 'success',
          message: `Order ${updated.serverOrder?.orderNumber || ''} successfully synchronized for ${draft.retailerName}!`,
        })
      }
    } catch (err) {
      await loadDrafts()
      setFeedback({
        type: 'error',
        message: `Sync failed for ${draft.retailerName}: ${err.message}`,
      })
    } finally {
      setSyncingDraftId(null)
    }
  }

  const handleSyncAll = async () => {
    if (!isOnline) {
      setFeedback({ type: 'warning', message: 'Device is offline. Please reconnect before syncing.' })
      return
    }

    setIsSyncingAll(true)
    setFeedback(null)
    try {
      const results = await syncAllDrafts(drafts)
      await loadDrafts()
      if (results.failed > 0) {
        setFeedback({
          type: 'warning',
          message: `Sync completed: ${results.succeeded} succeeded, ${results.failed} failed. Review failed drafts below.`,
        })
      } else if (results.succeeded > 0) {
        setFeedback({
          type: 'success',
          message: `All ${results.succeeded} ready drafts were successfully synchronized!`,
        })
      } else {
        setFeedback({ type: 'info', message: 'No pending drafts ready to sync.' })
      }
    } catch (err) {
      setFeedback({ type: 'error', message: 'Sync process error: ' + err.message })
    } finally {
      setIsSyncingAll(false)
    }
  }

  const handleDelete = async (draftId, retailerName) => {
    const confirmDelete = window.confirm(`Delete draft for ${retailerName}? This action cannot be undone.`)
    if (!confirmDelete) return

    try {
      await deleteDraft(draftId)
      await loadDrafts()
      setFeedback({ type: 'success', message: 'Draft deleted.' })
    } catch (err) {
      setFeedback({ type: 'error', message: 'Failed to delete draft: ' + err.message })
    }
  }

  const handleEdit = (draft) => {
    // Look up retailer in assigned list or build snapshot
    const match = retailers.find((r) => r.id === draft.retailerId) || {
      id: draft.retailerId,
      businessName: draft.retailerName,
      city: draft.retailerCity,
    }
    selectRetailer(match)
    // Pass draftId in query or state so Cart can load the draft items
    navigate(`/salesperson/cart?draftId=${encodeURIComponent(draft.id)}`)
  }

  const filteredDrafts = drafts.filter((d) => {
    const q = searchQuery.trim().toLowerCase()
    const matchesSearch =
      !q ||
      (d.retailerName || '').toLowerCase().includes(q) ||
      (d.retailerCity || '').toLowerCase().includes(q) ||
      (d.id || '').includes(q)

    let matchesStatus = true
    if (statusFilter === 'READY') {
      matchesStatus =
        d.status === DRAFT_STATUS.SAVED_LOCALLY ||
        d.status === DRAFT_STATUS.WAITING_FOR_CONNECTION ||
        d.status === DRAFT_STATUS.READY_TO_SYNC
    } else if (statusFilter === 'REVIEW') {
      matchesStatus =
        d.status === DRAFT_STATUS.NEEDS_REVIEW || d.status === DRAFT_STATUS.SYNC_UNCERTAIN
    } else if (statusFilter === 'COMPLETED') {
      matchesStatus =
        d.status === DRAFT_STATUS.SUBMITTED || d.status === DRAFT_STATUS.CREDIT_HOLD
    }

    return matchesSearch && matchesStatus
  })

  const getStatusBadge = (status) => {
    switch (status) {
      case DRAFT_STATUS.SAVED_LOCALLY:
        return <span className="sp-draft-pill draft-pill-saved">Saved Locally</span>
      case DRAFT_STATUS.WAITING_FOR_CONNECTION:
        return <span className="sp-draft-pill draft-pill-waiting">Waiting for Connection</span>
      case DRAFT_STATUS.READY_TO_SYNC:
        return <span className="sp-draft-pill draft-pill-ready">Ready to Sync</span>
      case DRAFT_STATUS.SYNCING:
        return <span className="sp-draft-pill draft-pill-syncing">Syncing...</span>
      case DRAFT_STATUS.NEEDS_REVIEW:
        return <span className="sp-draft-pill draft-pill-review">Needs Review</span>
      case DRAFT_STATUS.SYNC_UNCERTAIN:
        return <span className="sp-draft-pill draft-pill-uncertain">Outcome Uncertain</span>
      case DRAFT_STATUS.SUBMITTED:
        return <span className="sp-draft-pill draft-pill-submitted">Submitted</span>
      case DRAFT_STATUS.CREDIT_HOLD:
        return <span className="sp-draft-pill draft-pill-hold">Credit Hold</span>
      default:
        return <span className="sp-draft-pill draft-pill-saved">{status}</span>
    }
  }

  const pendingCount = drafts.filter(
    (d) => d.status !== DRAFT_STATUS.SUBMITTED && d.status !== DRAFT_STATUS.CREDIT_HOLD
  ).length
  const retryableCount = drafts.filter((d) => [
    DRAFT_STATUS.SAVED_LOCALLY,
    DRAFT_STATUS.WAITING_FOR_CONNECTION,
    DRAFT_STATUS.READY_TO_SYNC,
    DRAFT_STATUS.SYNC_UNCERTAIN,
  ].includes(d.status)).length

  return (
    <div className="sp-drafts-page">
      {/* Top Banner / Status Overview */}
      <div className="sp-drafts-hero">
        <div className="sp-drafts-hero-header">
          <div className="sp-drafts-hero-titles">
            <h1 className="sp-drafts-title">Offline Order Drafts</h1>
            <p className="sp-drafts-subtitle">
              Draft orders saved locally while visiting retailers. Review and synchronize when online.
            </p>
          </div>
          <div className="sp-drafts-connectivity-badge">
            {isOnline ? (
              <span className="online-indicator">
                <Wifi size={16} /> Online
              </span>
            ) : (
              <span className="offline-indicator">
                <WifiOff size={16} /> Offline
              </span>
            )}
          </div>
        </div>

        {/* Sync Summary Cards */}
        <div className="sp-drafts-stats-grid">
          <div className="sp-draft-stat-card">
            <span className="stat-label">Total Drafts</span>
            <span className="stat-value">{drafts.length}</span>
          </div>
          <div className="sp-draft-stat-card">
              <span className="stat-label">Pending / review</span>
            <span className="stat-value text-amber-600">{pendingCount}</span>
          </div>
          <div className="sp-draft-stat-card">
            <span className="stat-label">Action</span>
            <button
              type="button"
              className="sp-btn-sync-all"
              onClick={handleSyncAll}
              disabled={isSyncingAll || retryableCount === 0 || !isOnline}
            >
              <RefreshCw size={14} className={isSyncingAll ? 'animate-spin' : ''} />
              {isSyncingAll ? 'Syncing...' : 'Sync All Ready'}
            </button>
          </div>
        </div>
      </div>

      {/* User Feedback Alert */}
      {feedback && (
        <div className={`sp-drafts-alert sp-alert-${feedback.type}`} role={feedback.type === 'error' ? 'alert' : 'status'} aria-live={feedback.type === 'error' ? 'assertive' : 'polite'}>
          {feedback.type === 'error' && <AlertCircle size={18} />}
          {feedback.type === 'success' && <CheckCircle2 size={18} />}
          {feedback.type === 'warning' && <AlertCircle size={18} />}
          {feedback.type === 'info' && <Clock size={18} />}
          <span>{feedback.message}</span>
          <button
            type="button"
            className="sp-alert-close"
            onClick={() => setFeedback(null)}
          >
            &times;
          </button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="sp-drafts-toolbar">
        <div className="sp-drafts-search-box">
          <Search size={16} className="sp-search-icon" />
          <input
            type="text"
            className="sp-search-input"
            placeholder="Search by retailer name or city..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="sp-drafts-filter-tabs">
          <button
            type="button"
            className={`sp-filter-tab ${statusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            All ({drafts.length})
          </button>
          <button
            type="button"
            className={`sp-filter-tab ${statusFilter === 'READY' ? 'active' : ''}`}
            onClick={() => setStatusFilter('READY')}
          >
            Ready to Sync
          </button>
          <button
            type="button"
            className={`sp-filter-tab ${statusFilter === 'REVIEW' ? 'active' : ''}`}
            onClick={() => setStatusFilter('REVIEW')}
          >
            Needs Review
          </button>
          <button
            type="button"
            className={`sp-filter-tab ${statusFilter === 'COMPLETED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('COMPLETED')}
          >
            Synchronized
          </button>
        </div>
      </div>

      {/* Drafts List */}
      {isLoading ? (
        <div className="sp-drafts-loading">
          <RefreshCw size={24} className="animate-spin text-primary" />
          <span>Loading saved drafts...</span>
        </div>
      ) : filteredDrafts.length === 0 ? (
        <div className="sp-drafts-empty">
          <CloudOff size={48} className="sp-empty-icon" />
          <h3>No offline drafts found</h3>
          <p>
            {searchQuery
              ? 'No drafts match your current search criteria.'
              : 'When visiting retailers without an internet connection, create orders offline from the catalogue and save them as drafts.'}
          </p>
          <button
            type="button"
            className="sp-btn-primary"
            onClick={() => navigate('/salesperson/retailers')}
          >
            Select a Retailer
          </button>
        </div>
      ) : (
        <div className="sp-drafts-list">
          {filteredDrafts.map((draft) => {
            const isExpanded = expandedDraftId === draft.id
            const isSyncing = syncingDraftId === draft.id
            const isSubmitted =
              draft.status === DRAFT_STATUS.SUBMITTED || draft.status === DRAFT_STATUS.CREDIT_HOLD

            const totalEstimatedAmount = (draft.items || []).reduce(
              (sum, item) => sum + (Number(item.cachedPrice) || 0) * (Number(item.paidCases) || 1),
              0
            )

            return (
              <div key={draft.id} className="sp-draft-card">
                {/* Draft Card Header */}
                <div className="sp-draft-card-header">
                  <div className="sp-draft-retailer-info">
                    <div className="sp-draft-retailer-name-row">
                      <Building2 size={16} className="text-secondary" />
                      <span className="sp-draft-retailer-name">{draft.retailerName}</span>
                      {draft.retailerCity && (
                        <span className="sp-draft-retailer-city">{draft.retailerCity}</span>
                      )}
                    </div>
                    <div className="sp-draft-meta-row">
                      <span>ID: {draft.id.slice(0, 8)}...</span>
                      <span>&bull;</span>
                      <span>Modified: {new Date(draft.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      <span>&bull;</span>
                      <span>{draft.items?.length || 0} items</span>
                    </div>
                  </div>
                  <div className="sp-draft-status-box">
                    {getStatusBadge(draft.status)}
                  </div>
                </div>

                {/* Conflict or Error Message Callout */}
                {draft.syncError && !isSubmitted && (
                  <div className="sp-draft-error-callout">
                    <AlertCircle size={15} />
                    <span>{draft.syncError}</span>
                  </div>
                )}

                {/* Server Order Snapshot if Submitted */}
                {draft.serverOrder && (
                  <div className="sp-draft-server-callout">
                    <CheckCircle2 size={15} />
                    <div className="server-callout-content">
                      <span className="server-order-number">
                        Order #{draft.serverOrder.orderNumber}
                      </span>
                      <span className="server-order-status">
                        Status: {draft.serverOrder.status}
                      </span>
                      {draft.serverOrder.creditHoldReason && (
                        <span className="server-hold-reason">
                          Reason: {draft.serverOrder.creditHoldReason}
                        </span>
                      )}
                    </div>
                  </div>
                )}

                {/* Items Summary & Toggle */}
                <div className="sp-draft-summary-bar">
                  <span className="sp-draft-total-estimate">
                    Est. Total: ₹{totalEstimatedAmount.toLocaleString('en-IN')}
                    <span className="sp-estimate-notice"> (reference only)</span>
                  </span>
                  <button
                    type="button"
                    className="sp-btn-toggle-items"
                    onClick={() => setExpandedDraftId(isExpanded ? null : draft.id)}
                  >
                    {isExpanded ? 'Hide items' : 'View items'}
                    {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                  </button>
                </div>

                {/* Expandable Line Items Preview */}
                {isExpanded && (
                  <div className="sp-draft-items-preview">
                    <table className="sp-draft-items-table">
                      <thead>
                        <tr>
                          <th>Product</th>
                          <th>Pack</th>
                          <th>Cases</th>
                          <th>Ref. Price</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(draft.items || []).map((item, idx) => (
                          <tr key={idx}>
                            <td>
                              <div className="item-name">{item.productName}</div>
                              <div className="item-sku">{item.sku}</div>
                            </td>
                            <td>{item.packSize || '-'}</td>
                            <td>{item.paidCases} cases</td>
                            <td>₹{item.cachedPrice ? Number(item.cachedPrice).toFixed(2) : '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {draft.notes && (
                      <div className="sp-draft-notes">
                        <strong>Order Notes:</strong> {draft.notes}
                      </div>
                    )}
                  </div>
                )}

                {/* Card Actions */}
                <div className="sp-draft-card-actions">
                  <div className="actions-left">
                    <button
                      type="button"
                      className="sp-btn-delete-draft"
                      onClick={() => handleDelete(draft.id, draft.retailerName)}
                      title="Delete draft"
                    >
                      <Trash2 size={15} />
                      <span>Delete</span>
                    </button>
                    {!isSubmitted && (
                      <button
                        type="button"
                        className="sp-btn-edit-draft"
                        onClick={() => handleEdit(draft)}
                        title="Edit in cart"
                      >
                        <Edit3 size={15} />
                        <span>Edit</span>
                      </button>
                    )}
                  </div>

                  <div className="actions-right">
                    {!isSubmitted ? (
                      <button
                        type="button"
                        className="sp-btn-sync-single"
                        onClick={() => handleSyncOne(draft)}
                        disabled={isSyncing || !isOnline || draft.status === DRAFT_STATUS.NEEDS_REVIEW}
                      >
                        {isSyncing ? (
                          <>
                            <RefreshCw size={14} className="animate-spin" />
                            <span>Syncing...</span>
                          </>
                        ) : (
                          <>
                            <Send size={14} />
                            <span>{draft.status === DRAFT_STATUS.NEEDS_REVIEW ? 'Edit to retry' : 'Sync Now'}</span>
                          </>
                        )}
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="sp-btn-view-order"
                        onClick={() => navigate('/salesperson/orders')}
                      >
                        <ShoppingBag size={14} />
                        <span>View in Orders</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

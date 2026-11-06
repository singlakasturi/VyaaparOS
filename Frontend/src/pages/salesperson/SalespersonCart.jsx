import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  CheckCircle2,
  AlertCircle,
  Building2,
  ArrowRight,
  Package,
  Sparkles,
  Loader2,
  FileText,
  CloudOff,
  Send,
  Save,
} from 'lucide-react'
import { useSalesperson } from '../../services/SalespersonProvider'
import { fetchCart, updateCartQuantity, removeCartItem, clearCart } from '../../services/cart'
import { placeOrder } from '../../services/order'
import { ApiError } from '../../services/api'
import {
  getDraft,
  getDraftsByRetailer,
  saveDraft,
  deleteDraft,
  DRAFT_STATUS,
} from '../../services/db'
import { syncDraft } from '../../services/sync'

export default function SalespersonCart() {
  const { selectedRetailer, isOnline, salespersonId, refreshDraftsCount } = useSalesperson()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const queryDraftId = searchParams.get('draftId')

  // Live cart state
  const [cart, setCart] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [notes, setNotes] = useState('')
  const [isPlacing, setIsPlacing] = useState(false)
  const [orderResult, setOrderResult] = useState(null)
  const placeGuardRef = useRef(false)

  // Offline draft state
  const [activeDraft, setActiveDraft] = useState(null)
  const [isDraftMode, setIsDraftMode] = useState(false)
  const [isSavingDraft, setIsSavingDraft] = useState(false)
  const [isSyncingDraft, setIsSyncingDraft] = useState(false)
  const [feedback, setFeedback] = useState(null)

  const loadCartOrDraft = useCallback(async () => {
    if (!selectedRetailer) return
    setIsLoading(true)
    setError(null)
    setFeedback(null)

    // 1. If explicit draftId is requested, load that draft from IndexedDB
    if (queryDraftId) {
      try {
        const draft = await getDraft(queryDraftId)
        if (draft) {
          setActiveDraft(draft)
          setIsDraftMode(true)
          setNotes(draft.notes || '')
          setIsLoading(false)
          return
        }
      } catch (err) {
        console.error('Error loading requested draft:', err)
      }
    }

    // 2. If device is offline, check for existing open draft for this retailer
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      try {
        const drafts = await getDraftsByRetailer(selectedRetailer.id, salespersonId)
        const openDraft = drafts.find(
          (d) =>
            d.status === DRAFT_STATUS.SAVED_LOCALLY ||
            d.status === DRAFT_STATUS.WAITING_FOR_CONNECTION ||
            d.status === DRAFT_STATUS.READY_TO_SYNC ||
            d.status === DRAFT_STATUS.NEEDS_REVIEW
        )
        if (openDraft) {
          setActiveDraft(openDraft)
          setIsDraftMode(true)
          setNotes(openDraft.notes || '')
          setIsLoading(false)
          return
        }
        // No existing draft while offline
        setIsDraftMode(true)
        setActiveDraft(null)
        setIsLoading(false)
        return
      } catch {
        setIsDraftMode(true)
        setIsLoading(false)
        return
      }
    }

    // 3. Online: load live cart from backend
    try {
      const data = await fetchCart(selectedRetailer.id)
      setCart(data)
      setIsDraftMode(false)
      setActiveDraft(null)
    } catch (err) {
      // If backend cart fetch fails (e.g. network lost), check for draft
      try {
        const drafts = await getDraftsByRetailer(selectedRetailer.id, salespersonId)
        const openDraft = drafts[0]
        if (openDraft) {
          setActiveDraft(openDraft)
          setIsDraftMode(true)
          setNotes(openDraft.notes || '')
          return
        }
      } catch {
        // ignore
      }
      setError(err?.message || 'Failed to load cart.')
    } finally {
      setIsLoading(false)
    }
  }, [selectedRetailer, queryDraftId, salespersonId])

  useEffect(() => {
    loadCartOrDraft()
  }, [loadCartOrDraft])

  // ============================================
  // LIVE CART ACTIONS
  // ============================================

  const handleUpdateQty = async (itemId, newQty) => {
    if (newQty < 1) return
    try {
      const updated = await updateCartQuantity(itemId, newQty)
      setCart(updated)
    } catch (err) {
      alert(err?.message || 'Failed to update quantity.')
    }
  }

  const handleRemoveItem = async (itemId) => {
    try {
      await removeCartItem(itemId)
      await loadCartOrDraft()
    } catch (err) {
      alert(err?.message || 'Failed to remove item.')
    }
  }

  const handleClearCart = async () => {
    if (!selectedRetailer) return
    if (!window.confirm('Are you sure you want to clear this cart?')) return
    try {
      await clearCart(selectedRetailer.id)
      await loadCartOrDraft()
    } catch (err) {
      alert(err?.message || 'Failed to clear cart.')
    }
  }

  const handlePlaceOrder = async () => {
    if (placeGuardRef.current || !selectedRetailer || !cart || cart.items.length === 0) return
    placeGuardRef.current = true
    setIsPlacing(true)
    setError(null)

    try {
      const clientOrderId = crypto.randomUUID()
      const order = await placeOrder(selectedRetailer.id, clientOrderId, notes.trim())
      setOrderResult(order)
      setCart(null)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message || 'Failed to place order.')
      } else {
        setError('Network interruption. Consider saving as an offline draft to sync later.')
      }
    } finally {
      setIsPlacing(false)
      placeGuardRef.current = false
    }
  }

  const handleSaveCartAsDraft = async () => {
    if (!selectedRetailer || !cart || cart.items.length === 0) return
    setIsSavingDraft(true)
    try {
      const draftId = crypto.randomUUID()
      const items = cart.items.map((i) => ({
        productId: i.productId,
        productName: i.productName,
        sku: i.sku,
        packSize: i.packSize || '',
        paidCases: i.paidCases,
        cachedPrice: i.pricePerCase || 0,
        cachedScheme: i.schemeName || null,
      }))

      const draft = {
        id: draftId,
        salespersonId,
        retailerId: selectedRetailer.id,
        retailerName: selectedRetailer.businessName,
        retailerCity: selectedRetailer.city || '',
        status: DRAFT_STATUS.SAVED_LOCALLY,
        items,
        notes: notes.trim(),
      }

      await saveDraft(draft)
      await refreshDraftsCount()
      setActiveDraft(draft)
      setIsDraftMode(true)
      setFeedback({ type: 'success', message: 'Current order saved as an offline draft.' })
    } catch (err) {
      alert('Failed to save draft: ' + err.message)
    } finally {
      setIsSavingDraft(false)
    }
  }

  // ============================================
  // OFFLINE DRAFT ACTIONS
  // ============================================

  const handleDraftQtyChange = async (productId, delta) => {
    if (!activeDraft) return
    const updatedItems = (activeDraft.items || []).map((item) => {
      if (item.productId === productId) {
        const next = Math.max(1, (Number(item.paidCases) || 1) + delta)
        return { ...item, paidCases: next }
      }
      return item
    })

    const updated = {
      ...activeDraft,
      items: updatedItems,
      status: DRAFT_STATUS.SAVED_LOCALLY,
    }
    await saveDraft(updated)
    setActiveDraft(updated)
    await refreshDraftsCount()
  }

  const handleRemoveDraftItem = async (productId) => {
    if (!activeDraft) return
    const updatedItems = (activeDraft.items || []).filter((item) => item.productId !== productId)
    if (updatedItems.length === 0) {
      if (window.confirm('Removing the last item will delete this draft. Continue?')) {
        await deleteDraft(activeDraft.id)
        setActiveDraft(null)
        await refreshDraftsCount()
        return
      }
    }
    const updated = {
      ...activeDraft,
      items: updatedItems,
      status: DRAFT_STATUS.SAVED_LOCALLY,
    }
    await saveDraft(updated)
    setActiveDraft(updated)
    await refreshDraftsCount()
  }

  const handleSaveDraftNotes = async () => {
    if (!activeDraft) return
    const updated = {
      ...activeDraft,
      notes: notes.trim(),
    }
    await saveDraft(updated)
    setActiveDraft(updated)
    setFeedback({ type: 'success', message: 'Draft notes updated.' })
  }

  const handleSyncCurrentDraft = async () => {
    if (!activeDraft) return
    if (!isOnline) {
      setFeedback({ type: 'warning', message: 'Device is offline. Please connect to the internet to sync.' })
      return
    }

    setIsSyncingDraft(true)
    setFeedback(null)
    try {
      // Save any pending notes before syncing
      const draftToSync = { ...activeDraft, notes: notes.trim() }
      await saveDraft(draftToSync)

      const updated = await syncDraft(draftToSync.id)
      setActiveDraft(updated)
      await refreshDraftsCount()

      if (updated.status === DRAFT_STATUS.CREDIT_HOLD) {
        setFeedback({
          type: 'warning',
          message: `Order submitted on CREDIT HOLD: ${updated.serverOrder?.creditHoldReason || 'Exceeds retailer credit limit'}. Order #${updated.serverOrder?.orderNumber}.`,
        })
      } else {
        setOrderResult(updated.serverOrder)
        setFeedback({
          type: 'success',
          message: `Order #${updated.serverOrder?.orderNumber} successfully placed on the server!`,
        })
      }
    } catch (err) {
      // Reload draft state to reflect updated status and error
      const fresh = await getDraft(activeDraft.id)
      if (fresh) setActiveDraft(fresh)
      setFeedback({
        type: 'error',
        message: err.message || 'Synchronization failed. Review draft and retry.',
      })
    } finally {
      setIsSyncingDraft(false)
    }
  }

  const handleDeleteCurrentDraft = async () => {
    if (!activeDraft) return
    if (!window.confirm('Delete this offline draft? This action cannot be undone.')) return
    try {
      await deleteDraft(activeDraft.id)
      setActiveDraft(null)
      await refreshDraftsCount()
      navigate('/salesperson/drafts')
    } catch (err) {
      alert('Failed to delete draft: ' + err.message)
    }
  }

  const formatCurrency = (val) => {
    const num = Number(val) || 0
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(num)
  }

  if (!selectedRetailer) {
    return (
      <div className="sp-page">
        <div className="sp-empty card">
          <Building2 size={40} className="sp-empty-icon" />
          <h2>No Retailer Selected</h2>
          <p>Please select an assigned retailer to view their cart or place orders on their behalf.</p>
          <button
            className="btn btn-primary"
            onClick={() => navigate('/salesperson/retailers')}
            type="button"
          >
            Select an Assigned Retailer
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    )
  }

  // ============================================
  // ORDER CONFIRMATION VIEW (SUCCESS OR SYNC)
  // ============================================
  if (orderResult) {
    const isCreditHold = orderResult.status === 'CREDIT_HOLD'
    return (
      <div className="sp-page">
        <div className="sp-order-confirm card">
          <div className={`sp-confirm-icon-wrap ${isCreditHold ? 'sp-confirm-icon-hold' : ''}`}>
            {isCreditHold ? <AlertCircle size={48} /> : <CheckCircle2 size={48} />}
          </div>
          <h2 className="sp-confirm-title">
            {isCreditHold ? 'Order Placed on Credit Hold' : 'Order Placed Successfully!'}
          </h2>
          <p className="sp-confirm-num">Order Reference: {orderResult.orderNumber}</p>

          {isCreditHold && (
            <div className="sp-hold-banner">
              <strong>Credit Policy Warning:</strong>
              <p>
                {orderResult.creditHoldReason ||
                  'The order total exceeds the retailer credit limit. It has been routed to the Order Desk for manual credit approval.'}
              </p>
            </div>
          )}

          <div className="sp-confirm-details">
            <div className="sp-confirm-row">
              <span>Retailer:</span>
              <strong>{selectedRetailer.businessName}</strong>
            </div>
            <div className="sp-confirm-row">
              <span>Order Amount:</span>
              <strong>{formatCurrency(orderResult.finalAmount)}</strong>
            </div>
            <div className="sp-confirm-row">
              <span>Status:</span>
              <span className={`sp-status-pill ${isCreditHold ? 'pill-hold' : 'pill-submitted'}`}>
                {orderResult.status}
              </span>
            </div>
          </div>

          <div className="sp-confirm-actions">
            <button
              className="btn btn-secondary"
              onClick={() => {
                setOrderResult(null)
                loadCartOrDraft()
              }}
              type="button"
            >
              Continue Ordering
            </button>
            <button
              className="btn btn-primary"
              onClick={() => navigate('/salesperson/orders')}
              type="button"
            >
              View Order History
              <ArrowRight size={16} />
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ============================================
  // OFFLINE DRAFT VIEW
  // ============================================
  if (isDraftMode) {
    const draftItems = activeDraft?.items || []
    const totalEstAmount = draftItems.reduce(
      (sum, item) => sum + (Number(item.cachedPrice) || 0) * (Number(item.paidCases) || 1),
      0
    )

    return (
      <div className="sp-page">
        {/* Offline Draft Banner */}
        <div className="sp-draft-disclaimer-banner">
          <CloudOff size={20} />
          <div className="sp-disclaimer-content">
            <div className="sp-disclaimer-title">Offline Draft — Not Yet Submitted</div>
            <p>
              This order draft is saved locally on your device. Quantities, prices, and schemes are reference estimates based on cached catalogue data. Stock is not reserved until the order is synchronized with the server.
            </p>
          </div>
        </div>

        {/* Feedback Alert */}
        {feedback && (
          <div className={`sp-drafts-alert sp-alert-${feedback.type}`}>
            {feedback.type === 'error' && <AlertCircle size={18} />}
            {feedback.type === 'success' && <CheckCircle2 size={18} />}
            {feedback.type === 'warning' && <AlertCircle size={18} />}
            <span>{feedback.message}</span>
            <button type="button" className="sp-alert-close" onClick={() => setFeedback(null)}>
              &times;
            </button>
          </div>
        )}

        {/* Header */}
        <div className="sp-header">
          <div className="sp-header-left">
            <div className="sp-icon-wrap">
              <FileText size={24} />
            </div>
            <div>
              <div className="sp-title-row">
                <h1 className="sp-title">Offline Draft Order</h1>
                {activeDraft && (
                  <span className="sp-draft-pill draft-pill-ready">
                    {activeDraft.status}
                  </span>
                )}
              </div>
              <p className="sp-subtitle">
                Retailer: <strong>{selectedRetailer.businessName}</strong> ({selectedRetailer.city || 'Assigned'})
              </p>
            </div>
          </div>

          <div className="sp-header-actions">
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/salesperson/drafts')}
              type="button"
            >
              <CloudOff size={16} />
              All Drafts
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/salesperson/catalogue')}
              type="button"
            >
              <Package size={16} />
              Add More Products
            </button>
          </div>
        </div>

        {isLoading ? (
          <div className="sp-loading card">
            <Loader2 size={24} className="sp-spinner" />
            <p>Loading draft...</p>
          </div>
        ) : !activeDraft || draftItems.length === 0 ? (
          <div className="sp-empty card">
            <CloudOff size={40} className="sp-empty-icon" />
            <h2>No Active Draft for {selectedRetailer.businessName}</h2>
            <p>You do not have any saved draft items for this retailer.</p>
            <button
              className="btn btn-primary"
              onClick={() => navigate('/salesperson/catalogue')}
              type="button"
            >
              Browse Catalogue
              <ArrowRight size={16} />
            </button>
          </div>
        ) : (
          <div className="sp-cart-layout">
            {/* Draft Items List */}
            <div className="sp-cart-items-section card">
              <div className="sp-cart-table-header">
                <div className="sp-col-prod">Product & Pack</div>
                <div className="sp-col-price">Est. Price</div>
                <div className="sp-col-qty">Cases</div>
                <div className="sp-col-total">Est. Total</div>
                <div className="sp-col-action"></div>
              </div>

              <div className="sp-cart-rows">
                {draftItems.map((item) => {
                  const lineTotal =
                    (Number(item.cachedPrice) || 0) * (Number(item.paidCases) || 1)
                  return (
                    <div key={item.productId} className="sp-cart-row">
                      <div className="sp-col-prod">
                        <div className="sp-cart-prod-name">{item.productName}</div>
                        <div className="sp-cart-prod-meta">
                          <span>SKU: {item.sku}</span>
                          {item.packSize && <span>• {item.packSize}</span>}
                        </div>
                        {item.cachedScheme && (
                          <div className="sp-cart-scheme-badge">
                            <Sparkles size={11} />
                            <span>{item.cachedScheme}</span>
                          </div>
                        )}
                      </div>

                      <div className="sp-col-price">
                        <span className="sp-col-label">Price:</span>
                        ₹{Number(item.cachedPrice || 0).toFixed(2)}
                      </div>

                      <div className="sp-col-qty">
                        <div className="sp-stepper">
                          <button
                            className="sp-stepper-btn"
                            onClick={() => handleDraftQtyChange(item.productId, -1)}
                            disabled={item.paidCases <= 1}
                            type="button"
                            aria-label="Decrease quantity"
                          >
                            <Minus size={13} />
                          </button>
                          <span className="sp-stepper-val">{item.paidCases}</span>
                          <button
                            className="sp-stepper-btn"
                            onClick={() => handleDraftQtyChange(item.productId, 1)}
                            type="button"
                            aria-label="Increase quantity"
                          >
                            <Plus size={13} />
                          </button>
                        </div>
                      </div>

                      <div className="sp-col-total">
                        <span className="sp-col-label">Total:</span>
                        <strong>₹{lineTotal.toFixed(2)}</strong>
                      </div>

                      <div className="sp-col-action">
                        <button
                          className="sp-btn-icon-danger"
                          onClick={() => handleRemoveDraftItem(item.productId)}
                          type="button"
                          title="Remove item from draft"
                          aria-label="Remove item"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>

              {/* Order Notes */}
              <div className="sp-order-notes-wrap">
                <label className="sp-notes-label" htmlFor="draft-notes">
                  Order Notes / Delivery Instructions:
                </label>
                <div className="sp-notes-row">
                  <textarea
                    id="draft-notes"
                    className="input sp-notes-input"
                    rows={2}
                    placeholder="Enter instructions (e.g. deliver by 11 AM)..."
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                  <button
                    type="button"
                    className="btn btn-secondary sp-btn-save-notes"
                    onClick={handleSaveDraftNotes}
                  >
                    <Save size={14} />
                    Save Notes
                  </button>
                </div>
              </div>
            </div>

            {/* Summary & Sync Actions */}
            <div className="sp-cart-summary-section">
              <div className="sp-summary-card card">
                <h3 className="sp-summary-title">Draft Summary</h3>

                <div className="sp-summary-row">
                  <span>Draft ID:</span>
                  <span className="sp-summary-id" title={activeDraft.id}>
                    {activeDraft.id.slice(0, 8)}...
                  </span>
                </div>

                <div className="sp-summary-row">
                  <span>Total Cases:</span>
                  <strong>{draftItems.reduce((sum, i) => sum + (Number(i.paidCases) || 0), 0)}</strong>
                </div>

                <div className="sp-summary-row">
                  <span>Est. Subtotal:</span>
                  <span>{formatCurrency(totalEstAmount)}</span>
                </div>

                <div className="sp-summary-total-row">
                  <span>Estimated Total:</span>
                  <span className="sp-total-amount">{formatCurrency(totalEstAmount)}</span>
                </div>

                <div className="sp-summary-disclaimer">
                  * Note: Actual prices, GST, schemes, and credit checks will be determined upon synchronization.
                </div>

                <div className="sp-draft-action-buttons">
                  <button
                    className="btn btn-primary sp-btn-sync-draft"
                    onClick={handleSyncCurrentDraft}
                    disabled={isSyncingDraft || !isOnline}
                    type="button"
                  >
                    {isSyncingDraft ? (
                      <>
                        <Loader2 size={16} className="animate-spin" />
                        <span>Synchronizing with Server...</span>
                      </>
                    ) : (
                      <>
                        <Send size={16} />
                        <span>{isOnline ? 'Sync Order Now' : 'Cannot Sync (Offline)'}</span>
                      </>
                    )}
                  </button>

                  <button
                    className="btn btn-secondary sp-btn-discard-draft"
                    onClick={handleDeleteCurrentDraft}
                    type="button"
                  >
                    <Trash2 size={14} />
                    <span>Discard Draft</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    )
  }

  // ============================================
  // LIVE ONLINE CART VIEW
  // ============================================
  const cartItems = cart?.items || []
  const cartTotal = cart?.totalAmount || 0
  const totalCases = cartItems.reduce((sum, item) => sum + (item.paidCases || 0), 0)

  return (
    <div className="sp-page">
      {/* Header */}
      <div className="sp-header">
        <div className="sp-header-left">
          <div className="sp-icon-wrap">
            <ShoppingCart size={24} />
          </div>
          <div>
            <div className="sp-title-row">
              <h1 className="sp-title">Review Order Cart</h1>
              <span className="badge">{cartItems.length} Products</span>
            </div>
            <p className="sp-subtitle">
              Ordering for <strong>{selectedRetailer.businessName}</strong> ({selectedRetailer.city || 'Assigned'})
            </p>
          </div>
        </div>

        <div className="sp-header-actions">
          <button
            className="btn btn-secondary"
            onClick={handleSaveCartAsDraft}
            disabled={isSavingDraft || cartItems.length === 0}
            type="button"
            title="Save this cart as an offline draft to submit later"
          >
            <CloudOff size={16} />
            {isSavingDraft ? 'Saving Draft...' : 'Save as Offline Draft'}
          </button>
          <button
            className="btn btn-secondary"
            onClick={() => navigate('/salesperson/catalogue')}
            type="button"
          >
            <Package size={16} />
            Continue Browsing
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="sp-loading card">
          <Loader2 size={24} className="sp-spinner" />
          <p>Loading cart for {selectedRetailer.businessName}...</p>
        </div>
      ) : error ? (
        <div className="sp-error card">
          <AlertCircle size={24} />
          <p>{error}</p>
          <button className="btn btn-secondary" onClick={loadCartOrDraft} type="button">
            Retry
          </button>
        </div>
      ) : cartItems.length === 0 ? (
        <div className="sp-empty card">
          <ShoppingCart size={40} className="sp-empty-icon" />
          <h2>The cart is currently empty</h2>
          <p>Browse the catalogue or use the AI Order Assistant to add items for {selectedRetailer.businessName}.</p>
          <div className="sp-empty-actions">
            <button
              className="btn btn-primary"
              onClick={() => navigate('/salesperson/catalogue')}
              type="button"
            >
              Browse Catalogue
              <ArrowRight size={16} />
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/salesperson/ai-assistant')}
              type="button"
            >
              <Sparkles size={16} />
              AI Assistant
            </button>
          </div>
        </div>
      ) : (
        <div className="sp-cart-layout">
          {/* Cart Items List */}
          <div className="sp-cart-items-section card">
            <div className="sp-cart-table-header">
              <div className="sp-col-prod">Product & Pack</div>
              <div className="sp-col-price">Unit Price</div>
              <div className="sp-col-qty">Cases</div>
              <div className="sp-col-total">Line Total</div>
              <div className="sp-col-action"></div>
            </div>

            <div className="sp-cart-rows">
              {cartItems.map((item) => (
                <div key={item.id} className="sp-cart-row">
                  <div className="sp-col-prod">
                    <div className="sp-cart-prod-name">{item.productName}</div>
                    <div className="sp-cart-prod-meta">
                      <span>SKU: {item.sku}</span>
                      {item.packSize && <span>• {item.packSize}</span>}
                    </div>
                    {item.schemeName && (
                      <div className="sp-cart-scheme-badge">
                        <Sparkles size={11} />
                        <span>{item.schemeName}</span>
                      </div>
                    )}
                  </div>

                  <div className="sp-col-price">
                    <span className="sp-col-label">Price:</span>
                    ₹{Number(item.pricePerCase || 0).toFixed(2)}
                  </div>

                  <div className="sp-col-qty">
                    <div className="sp-stepper">
                      <button
                        className="sp-stepper-btn"
                        onClick={() => handleUpdateQty(item.id, item.paidCases - 1)}
                        disabled={item.paidCases <= 1}
                        type="button"
                        aria-label="Decrease quantity"
                      >
                        <Minus size={13} />
                      </button>
                      <span className="sp-stepper-val">{item.paidCases}</span>
                      <button
                        className="sp-stepper-btn"
                        onClick={() => handleUpdateQty(item.id, item.paidCases + 1)}
                        type="button"
                        aria-label="Increase quantity"
                      >
                        <Plus size={13} />
                      </button>
                    </div>
                  </div>

                  <div className="sp-col-total">
                    <span className="sp-col-label">Total:</span>
                    <strong>₹{Number(item.finalAmount || item.baseAmount || 0).toFixed(2)}</strong>
                  </div>

                  <div className="sp-col-action">
                    <button
                      className="sp-btn-icon-danger"
                      onClick={() => handleRemoveItem(item.id)}
                      type="button"
                      title="Remove item"
                      aria-label="Remove item"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Clear Cart & Notes */}
            <div className="sp-cart-footer-controls">
              <button
                className="btn btn-outline-danger btn-sm"
                onClick={handleClearCart}
                type="button"
              >
                <Trash2 size={14} />
                Clear Cart
              </button>
            </div>

            <div className="sp-order-notes-wrap">
              <label className="sp-notes-label" htmlFor="order-notes">
                Order Notes / Delivery Instructions:
              </label>
              <textarea
                id="order-notes"
                className="input sp-notes-input"
                rows={2}
                placeholder="Enter instructions (e.g. deliver by 11 AM, gate 2)..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </div>

          {/* Cart Summary & Checkout */}
          <div className="sp-cart-summary-section">
            <div className="sp-summary-card card">
              <h3 className="sp-summary-title">Order Summary</h3>

              <div className="sp-summary-row">
                <span>Total Items:</span>
                <strong>{totalCases} cases</strong>
              </div>

              <div className="sp-summary-total-row">
                <span>Final Payable:</span>
                <span className="sp-total-amount">{formatCurrency(cartTotal)}</span>
              </div>

              <button
                className="btn btn-primary sp-btn-checkout"
                onClick={handlePlaceOrder}
                disabled={isPlacing || cartItems.length === 0}
                type="button"
              >
                {isPlacing ? (
                  <>
                    <Loader2 size={16} className="animate-spin" />
                    Placing Order...
                  </>
                ) : (
                  <>
                    Submit Order
                    <ArrowRight size={16} />
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

import { useState, useCallback, useRef } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import {
  Sparkles,
  Send,
  Loader2,
  Check,
  AlertCircle,
  HelpCircle,
  X,
  Minus,
  Plus,
  ShoppingCart,
  RotateCcw,
  PackageSearch,
  ChevronDown,
} from 'lucide-react'
import { useAuth } from '../../services/AuthProvider'
import { parseOrderText, applyOrderItems, fetchCart, getAcceptedAiOrderItems } from '../../services/aiOrder'
import { ApiError } from '../../services/api'
import { fetchProducts } from '../../services/catalog.js'

const EXAMPLE_PROMPTS = [
  'bhaiya 5 peti biscuit chhota, 2 peti washing powder 1 kilo, chai patti 250 wala 1 peti',
  'Bhaiya 5 peti biscuit chhota',
  '2 peti washing powder 1 kilo',
  'Chai patti 250 wala 1 peti',
  '5 cases glucose biscuit aur 3 cases detergent',
]

const money = (amount, currency = 'INR') => amount == null
  ? 'Price unavailable'
  : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount))

const STATUS_LABELS = {
  MATCHED: 'Matched',
  AMBIGUOUS: 'Needs confirmation',
  UNMATCHED: 'Not found',
}

function getStatusBadgeClass(status) {
  switch (status) {
    case 'MATCHED': return 'ai-badge-matched'
    case 'AMBIGUOUS': return 'ai-badge-ambiguous'
    case 'UNMATCHED': return 'ai-badge-unmatched'
    default: return ''
  }
}

function getRetailerId(session) {
  const user = session?.user
  if (!user) return null

  // 1. Check explicitly selected retailer (used by salesperson workspace)
  const storedRetailerId = window.sessionStorage.getItem('vyapaaros.retailerId')
  if (storedRetailerId) return storedRetailerId

  // 2. If user is a salesperson and no retailer is selected, return null
  const isSalesperson = user.roles?.some((r) => r === 'SALESPERSON' || r === 'ROLE_SALESPERSON')
  if (isSalesperson) return null

  // 3. Retailer membership ID is stored in the user object
  if (user.retailerId) return user.retailerId
  if (user.retailerMembershipId) return user.retailerMembershipId

  // 4. Fall back to active memberships
  if (user.memberships?.length > 0) {
    const retailerMembership = user.memberships.find(
      (m) => m.role === 'RETAILER' || m.role === 'OWNER'
    )
    if (retailerMembership?.retailerId) return retailerMembership.retailerId
  }

  // 5. Fall back to user id for direct retailer accounts
  return user.id || null
}

function getSelectedRetailerName() {
  try {
    const val = window.sessionStorage.getItem('vyapaaros.selectedRetailer')
    return val ? JSON.parse(val)?.businessName : null
  } catch {
    return null
  }
}

export default function AiOrderAssistant() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const retailerContext = useOutletContext()

  const [orderText, setOrderText] = useState('')
  const [isParsing, setIsParsing] = useState(false)
  const [parseResult, setParseResult] = useState(null)
  const [editableItems, setEditableItems] = useState([])
  const [catalogueProducts, setCatalogueProducts] = useState([])
  const [manualSelections, setManualSelections] = useState({})
  const [parseError, setParseError] = useState(null)

  const [isApplying, setIsApplying] = useState(false)
  const [applySuccess, setApplySuccess] = useState(false)
  const [applyError, setApplyError] = useState(null)
  const [cartResult, setCartResult] = useState(null)
  const [reviewOnly, setReviewOnly] = useState(false)

  const applyGuardRef = useRef(false)
  const textareaRef = useRef(null)

  const isOrderDesk = session?.user?.roles?.some((r) => r === 'ORDER_DESK' || r === 'ROLE_ORDER_DESK')
  const retailerId = isOrderDesk ? null : (retailerContext?.retailerId || getRetailerId(session))
  const effectiveRetailerId = isOrderDesk ? retailerContext?.retailerId : retailerId

  // Build editable items from parse response
  const buildEditableItems = useCallback((items) => {
    return items.map((item, idx) => ({
      _key: `${Date.now()}-${idx}`,
      originalText: item.originalText,
      resolutionStatus: item.resolutionStatus,
      product: item.product,
      candidates: item.candidates || [],
      paidCases: item.paidCases ?? '',
      unit: item.unit || 'CASE',
      unitAmbiguous: Boolean(item.unitAmbiguous || (item.unit !== 'CASE' && item.unit !== 'PIECE')),
      confidence: item.confidence,
      explanation: item.explanation,
      pricePerCase: item.product?.pricePerCase,
      priceAvailable: item.product?.priceAvailable,
      availableCases: item.product?.availableCases,
      schemeName: item.product?.schemeName,
      currency: item.product?.currency || 'INR',
      // User interaction state
      selectedProductId: item.product?.id || null,
      confirmed: false,
      removed: false,
      missingQuantity: item.paidCases == null || item.paidCases <= 0,
    }))
  }, [])

  // Parse order
  const handleParse = useCallback(async () => {
    const text = orderText.trim()
    if (!text) {
      setParseError('Please type your order in the box above.')
      return
    }
    if (!effectiveRetailerId) {
      setParseError(isOrderDesk ? 'Choose an active retailer in the order desk first.' : 'No active retailer found. Please sign in as a retailer.')
      return
    }
    if (isOrderDesk && !retailerContext?.retailers?.some((retailer) => retailer.id === effectiveRetailerId)) {
      setParseError('Choose an active retailer in this order desk before parsing.')
      return
    }
    if (text.length > 4000) {
      setParseError('Order text is too long. Please keep it under 4000 characters.')
      return
    }

    setParseError(null)
    setParseResult(null)
    setEditableItems([])
    setApplySuccess(false)
    setReviewOnly(false)
    setApplyError(null)
    setCartResult(null)
    setIsParsing(true)

    try {
      const result = await parseOrderText(effectiveRetailerId, text)
      setParseResult(result)
      try {
        const page = await fetchProducts({ retailerId: effectiveRetailerId, page: 0, size: 100 })
        setCatalogueProducts(page?.content || [])
      } catch {
        setCatalogueProducts([])
      }
      setManualSelections({})
      if (result.items && result.items.length > 0) {
        setEditableItems(buildEditableItems(result.items))
      } else {
        setParseError('No products could be identified from your message. Try rephrasing with product names and quantities.')
      }
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setParseError('Your session has expired. Please sign in again.')
        } else if (err.status === 403) {
          setParseError('You are not authorized to place orders for this retailer.')
        } else if (err.status === 503) {
          const providerMessage = err.message || 'The AI provider is unavailable.'
          setParseError(`${providerMessage} Your order text was not added to the cart.`)
        } else if (err.status === 429) {
          setParseError('Too many requests. Please wait a moment and try again.')
        } else if (err.status === 422 || err.status === 400) {
          setParseError(err.message || 'Invalid input. Please check your order text and try again.')
        } else {
          setParseError(err.message || 'Something went wrong while parsing your order. Please try again.')
        }
      } else {
        setParseError('Unable to reach the server. Please check your connection and try again.')
      }
    } finally {
      setIsParsing(false)
    }
  }, [orderText, effectiveRetailerId, buildEditableItems, isOrderDesk, retailerContext?.retailers])

  const handleManualProduct = useCallback((key, productId) => {
    const product = catalogueProducts.find((entry) => entry.id === productId)
    if (!product) return
    setEditableItems((current) => current.map((item) => item._key !== key ? item : ({
      ...item,
      resolutionStatus: 'MATCHED',
      unitAmbiguous: false,
      product: { id: product.id, sku: product.sku, name: product.name },
      selectedProductId: product.id,
      pricePerCase: product.pricePerCase,
      priceAvailable: product.priceAvailable,
      availableCases: product.availableCases,
      schemeName: product.schemeName,
      currency: product.currency || 'INR',
      confirmed: false,
      explanation: 'Selected manually from the current catalogue.',
    })))
  }, [catalogueProducts])

  // Update item quantity
  const handleQuantityChange = useCallback((key, value) => {
    setEditableItems((prev) =>
      prev.map((item) => {
        if (item._key !== key) return item
        const num = value === '' ? '' : Math.max(0, parseInt(value, 10) || 0)
        return {
          ...item,
          paidCases: num,
          missingQuantity: num === '' || num <= 0,
          confirmed: false,
        }
      })
    )
  }, [])

  // Increment/decrement quantity
  const handleQuantityStep = useCallback((key, delta) => {
    setEditableItems((prev) =>
      prev.map((item) => {
        if (item._key !== key) return item
        const current = typeof item.paidCases === 'number' ? item.paidCases : 0
        const num = Math.max(1, current + delta)
        return {
          ...item,
          paidCases: num,
          missingQuantity: false,
          confirmed: false,
        }
      })
    )
  }, [])

  // Select candidate for ambiguous item
  const handleSelectCandidate = useCallback((key, candidate) => {
    setEditableItems((prev) =>
      prev.map((item) => {
        if (item._key !== key) return item
        return {
          ...item,
          resolutionStatus: 'MATCHED',
          unitAmbiguous: false,
          product: { id: candidate.id, sku: candidate.sku, name: candidate.name },
          selectedProductId: candidate.id,
          pricePerCase: candidate.pricePerCase,
          priceAvailable: candidate.priceAvailable,
          availableCases: candidate.availableCases,
          schemeName: candidate.schemeName,
          currency: candidate.currency || 'INR',
          candidates: item.candidates,
          confirmed: item.paidCases > 0 && !item.unitAmbiguous,
          explanation: 'Product selected from suggestions.',
        }
      })
    )
  }, [])

  // Remove item
  const handleRemoveItem = useCallback((key) => {
    setEditableItems((prev) =>
      prev.map((item) =>
        item._key === key ? { ...item, removed: true } : item
      )
    )
  }, [])

  const handleConfirmItem = useCallback((key, confirmed) => {
    setEditableItems((prev) => prev.map((item) => item._key === key ? {
      ...item,
      confirmed,
      ...(confirmed && item.unitAmbiguous ? { unit: 'CASE', unitAmbiguous: false } : {}),
    } : item))
  }, [])

  // Get accepted items
  const getAcceptedItems = useCallback(() => {
    return getAcceptedAiOrderItems(editableItems)
  }, [editableItems])

  // Apply to cart
  const handleApply = useCallback(async () => {
    if (applyGuardRef.current) return
    const accepted = getAcceptedItems()
    if (accepted.length === 0) {
      setApplyError('No items are ready to add. Please resolve any issues above first.')
      return
    }
    if (!effectiveRetailerId) {
      setApplyError('No active retailer context. Please sign in again.')
      return
    }
    if (isOrderDesk) {
      setReviewOnly(true)
      setCartResult({ items: accepted.map((item) => ({
        id: item._key,
        name: item.product?.name,
        sku: item.product?.sku,
        paidCases: item.paidCases,
        pricePerCase: item.pricePerCase,
        priceAvailable: item.priceAvailable,
        availableCases: item.availableCases,
        scheme: { schemeAvailable: Boolean(item.schemeName), schemeName: item.schemeName },
      })) })
      setApplySuccess(true)
      return
    }

    applyGuardRef.current = true
    setIsApplying(true)
    setApplyError(null)

    try {
      const items = accepted.map((item) => ({
        productId: item.selectedProductId,
        paidCases: item.paidCases,
        unit: item.unit,
      }))
      const cartResponse = await applyOrderItems(effectiveRetailerId, items)
      setApplySuccess(true)

      // Refresh cart for accurate pricing/schemes
      try {
        const freshCart = await fetchCart(effectiveRetailerId)
        setCartResult(freshCart)
      } catch {
        // Use the apply response if refresh fails
        setCartResult(cartResponse)
      }
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setApplyError('Your session has expired. Please sign in again.')
        } else if (err.status === 403) {
          setApplyError('You are not authorized to modify this cart.')
        } else if (err.status === 409) {
          setApplyError(err.message || 'A conflict occurred. Some items may have already been added.')
        } else if (err.status === 400 || err.status === 422) {
          setApplyError(err.message || 'Some products may be invalid or inactive. Please try parsing again.')
        } else {
          setApplyError(err.message || 'Failed to add items to cart. Please try again.')
        }
      } else {
        setApplyError('Unable to reach the server. Please check your connection.')
      }
    } finally {
      setIsApplying(false)
      applyGuardRef.current = false
    }
  }, [getAcceptedItems, effectiveRetailerId, isOrderDesk])

  // Reset everything
  const handleReset = useCallback(() => {
    setParseResult(null)
    setEditableItems([])
    setParseError(null)
    setApplySuccess(false)
    setApplyError(null)
    setCartResult(null)
    applyGuardRef.current = false
    textareaRef.current?.focus()
  }, [])

  // Use example prompt
  const handleUseExample = useCallback((prompt) => {
    setOrderText(prompt)
    setParseResult(null)
    setEditableItems([])
    setParseError(null)
    setApplySuccess(false)
    setApplyError(null)
    setCartResult(null)
    textareaRef.current?.focus()
  }, [])

  const acceptedItems = getAcceptedItems()
  const activeItems = editableItems.filter((item) => !item.removed)
  const hasUnresolved = activeItems.some(
    (item) =>
      item.resolutionStatus !== 'MATCHED' ||
      item.unitAmbiguous ||
      item.missingQuantity ||
      !item.confirmed
  )

  const isSalesperson = session?.user?.roles?.some(
    (r) => r === 'SALESPERSON' || r === 'ROLE_SALESPERSON'
  )
  const selectedRetailerName = isOrderDesk
    ? retailerContext?.retailers?.find((retailer) => retailer.id === effectiveRetailerId)?.businessName
    : retailerContext?.retailer?.businessName || getSelectedRetailerName()

  // ── Render ──

  if (!effectiveRetailerId) {
    return (
      <div className="ai-page">
        <div className="ai-no-retailer card">
          <AlertCircle size={32} className="ai-no-retailer-icon" />
          <h2>{isOrderDesk ? 'Select a retailer' : 'Retailer Context Required'}</h2>
          <p>
            {isOrderDesk
              ? 'Choose an active retailer from the workspace selector to parse their current catalogue.'
              : isSalesperson
              ? 'Please select an assigned retailer before using the AI Order Assistant.'
              : 'Please sign in as a retailer to use the AI Order Assistant.'}
          </p>
          {isSalesperson && (
            <button
              className="btn btn-primary"
              onClick={() => navigate('/salesperson/retailers')}
              type="button"
            >
              Select an Assigned Retailer
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="ai-page">
      {/* Header */}
      <div className="ai-header">
        <div className="ai-header-icon-wrap">
          <Sparkles size={24} />
        </div>
        <div>
          <h1 className="ai-title">AI Order Assistant</h1>
          <p className="ai-subtitle">
            {selectedRetailerName ? (
              <>
                {isOrderDesk ? 'Preparing a cart for ' : 'Ordering for '}<strong>{selectedRetailerName}</strong>.
              </>
            ) : (
              "Paste a WhatsApp order message. Review each item before adding it to your cart."
            )}
          </p>
          {isOrderDesk && (
            <label className="ai-retailer-select">Active retailer
              <select value="" onChange={(event) => retailerContext?.setRetailerId?.(event.target.value)}>
                <option value="">Choose an active retailer</option>
                {(retailerContext?.retailers || []).map((retailer) => <option key={retailer.id} value={retailer.id}>{retailer.businessName}</option>)}
              </select>
            </label>
          )}
        </div>
        {isOrderDesk && <label className="ai-retailer-select ai-retailer-select-header">Retailer
          <select value={effectiveRetailerId || ''} onChange={(event) => retailerContext?.setRetailerId?.(event.target.value)}>
            <option value="">Choose retailer</option>
            {(retailerContext?.retailers || []).map((retailer) => <option key={retailer.id} value={retailer.id}>{retailer.businessName}</option>)}
          </select>
        </label>}
      </div>

      {/* Success State */}
      {applySuccess && cartResult && (
        <div className="ai-success card">
          <div className="ai-success-icon-wrap">
            <Check size={28} />
          </div>
          <h2>{reviewOnly ? 'Review ready' : 'Items added to cart!'}</h2>
          <p>
            {reviewOnly
              ? `${acceptedItems.length} reviewed item${acceptedItems.length !== 1 ? 's' : ''} shown for the selected retailer. No cart was changed and no order was placed.`
              : `${acceptedItems.length} item${acceptedItems.length !== 1 ? 's' : ''} added to cart. Review it and continue through checkout when ready; no order has been placed.`}
          </p>

          {/* Cart Summary */}
          {cartResult.items && cartResult.items.length > 0 && (
            <div className="ai-cart-summary">
              <h3>{reviewOnly ? 'Reviewed Items' : 'Your Cart'}</h3>
              <div className="ai-cart-items">
                {cartResult.items.map((ci) => (
                  <div className="ai-cart-item" key={ci.id}>
                    <div className="ai-cart-item-info">
                      <span className="ai-cart-item-name">{ci.name}</span>
                      <span className="ai-cart-item-sku">{ci.sku}</span>
                    </div>
                    <div className="ai-cart-item-detail">
                      <span>{ci.paidCases} case{ci.paidCases !== 1 ? 's' : ''} · line total {ci.pricePerCase != null ? `₹${(Number(ci.pricePerCase) * Number(ci.paidCases)).toFixed(2)}` : 'unavailable'}</span>
                      {ci.pricePerCase != null && (
                        <span className="ai-cart-item-price">
                          ₹{Number(ci.pricePerCase).toFixed(2)}/case
                        </span>
                      )}
                      {ci.scheme?.schemeAvailable && (
                        <span className="ai-badge-scheme">
                          {ci.scheme.schemeName || 'Scheme available'}
                        </span>
                      )}
                      {ci.availableCases != null && ci.availableCases < ci.paidCases && (
                        <span className="ai-cart-low-stock">
                          Only {ci.availableCases} in stock
                        </span>
                      )}
                    </div>
                    {ci.indicative && (
                      <p className="ai-cart-indicative">
                        Indicative pricing — final price at order submission
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="ai-success-actions">
            {reviewOnly && <button className="btn btn-secondary" type="button" onClick={handleReset}><RotateCcw size={18}/>Back to parsed lines</button>}
            {!reviewOnly && <>
            <button
              className="btn btn-primary"
              onClick={() => navigate(isSalesperson ? '/salesperson/cart' : '/retailer/cart')}
              type="button"
            >
              <ShoppingCart size={18} />
              View Cart
            </button>
            <button
              className="btn btn-secondary"
              onClick={handleReset}
              type="button"
            >
              <RotateCcw size={18} />
              New Order
            </button>
            </>}
          </div>
        </div>
      )}

      {/* Input Area (hidden after successful apply) */}
      {!applySuccess && (
        <>
          {/* Text Input */}
          <div className="ai-input-section card">
            <label htmlFor="ai-order-input" className="ai-input-label">
              What would you like to order?
            </label>
            <div className="ai-textarea-wrap">
              <textarea
                ref={textareaRef}
                id="ai-order-input"
                className="ai-textarea"
                value={orderText}
                onChange={(e) => setOrderText(e.target.value)}
                placeholder="Type your order here... e.g. '5 peti biscuit aur 2 cases chai'"
                rows={3}
                maxLength={4000}
                disabled={isParsing}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    handleParse()
                  }
                }}
              />
              <div className="ai-textarea-footer">
                <span className="ai-char-count">
                  {orderText.length}/4000
                </span>
                <button
                  className="btn btn-primary ai-parse-btn"
                  onClick={handleParse}
                  disabled={isParsing || !orderText.trim()}
                  type="button"
                >
                  {isParsing ? (
                    <>
                      <Loader2 size={18} className="ai-spinner" />
                      Parsing…
                    </>
                  ) : (
                    <>
                      <Send size={18} />
                      Parse Order
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Example Prompts */}
            {!parseResult && !isParsing && (
              <div className="ai-examples">
                <p className="ai-examples-label">Try an example:</p>
                <div className="ai-examples-list">
                  {EXAMPLE_PROMPTS.map((prompt) => (
                    <button
                      key={prompt}
                      className="ai-example-chip"
                      onClick={() => handleUseExample(prompt)}
                      type="button"
                    >
                      "{prompt}"
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Loading State */}
          {isParsing && (
            <div className="ai-loading card">
              <Loader2 size={28} className="ai-spinner" />
              <div>
                <p className="ai-loading-title">Parsing your order…</p>
                <p className="ai-loading-subtitle">
                  Understanding products and quantities from your message
                </p>
              </div>
            </div>
          )}

          {/* Parse Error */}
          {parseError && (
            <div className="ai-error card">
              <AlertCircle size={20} />
              <div>
                <p className="ai-error-title">Could not parse order</p>
                <p>{parseError}</p>
                <button className="btn btn-secondary" type="button" onClick={handleParse} disabled={isParsing || !orderText.trim()}>Retry parsing</button>
              </div>
            </div>
          )}

          {/* Parse Results */}
          {parseResult && editableItems.length > 0 && (
            <div className="ai-results">
              {/* Results Header */}
              <div className="ai-results-header">
                <h2 className="ai-results-title">
                  Parsed Items
                  <span className="ai-results-count">
                    {activeItems.length} item{activeItems.length !== 1 ? 's' : ''}
                  </span>
                </h2>
                {parseResult.status === 'PARTIALLY_PARSED' && (
                  <p className="ai-results-warning">
                    <AlertCircle size={14} />
                    Some items need your attention before adding to cart.
                  </p>
                )}
                {parseResult.status === 'UNMATCHED' && (
                  <p className="ai-results-warning ai-results-warning-error">
                    <AlertCircle size={14} />
                    None of the items could be matched. Try rephrasing.
                  </p>
                )}
              </div>

              {/* Item Cards */}
              {editableItems.map((item) => {
                if (item.removed) return null
                return (
                  <ParsedItemCard
                    key={item._key}
                    item={item}
                    onQuantityChange={handleQuantityChange}
                    onQuantityStep={handleQuantityStep}
                    onSelectCandidate={handleSelectCandidate}
                    onRemove={handleRemoveItem}
                    onConfirm={handleConfirmItem}
                    catalogueProducts={catalogueProducts}
                    manualSelections={manualSelections}
                    onSelectManual={handleManualProduct}
                  />
                )
              })}

              {/* Apply Error */}
              {applyError && (
                <div className="ai-error card">
                  <AlertCircle size={20} />
                  <div>
                    <p className="ai-error-title">Could not add to cart</p>
                    <p>{applyError}</p>
                  </div>
                </div>
              )}

              {/* Action Bar */}
              <div className="ai-action-bar card">
                <div className="ai-action-info">
                  {acceptedItems.length > 0 ? (
                    <p>
                      <Check size={16} className="ai-action-check" />
                      {acceptedItems.length} item{acceptedItems.length !== 1 ? 's' : ''} ready to add
                    </p>
                  ) : (
                    <p className="ai-action-info-muted">
                      Resolve the items above to add them to your cart
                    </p>
                  )}
                  {hasUnresolved && activeItems.length > acceptedItems.length && (
                    <p className="ai-action-info-muted">
                      {activeItems.length - acceptedItems.length} item{activeItems.length - acceptedItems.length !== 1 ? 's' : ''} still need attention
                    </p>
                  )}
                </div>
                <div className="ai-action-buttons">
                  <button
                    className="btn btn-secondary"
                    onClick={handleReset}
                    type="button"
                  >
                    <RotateCcw size={16} />
                    Start Over
                  </button>
                  <button
                    className="btn btn-primary"
                    onClick={handleApply}
                    disabled={isApplying || acceptedItems.length === 0}
                    type="button"
                  >
                    {isApplying ? (
                      <>
                        <Loader2 size={18} className="ai-spinner" />
                        Adding to cart…
                      </>
                    ) : (
                      <>
                        <ShoppingCart size={18} />
                        {isOrderDesk ? 'Review selected items' : 'Add reviewed items to cart'}
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ────── Parsed Item Card ──────

function ParsedItemCard({
  item,
  onQuantityChange,
  onQuantityStep,
  onSelectCandidate,
  onRemove,
  onConfirm,
  catalogueProducts = [],
  manualSelections = {},
  onSelectManual = () => {},
}) {
  const [candidatesOpen, setCandidatesOpen] = useState(
    item.resolutionStatus === 'AMBIGUOUS'
  )

  const statusLabel = item.missingQuantity && item.resolutionStatus === 'MATCHED'
    ? 'Missing quantity'
    : STATUS_LABELS[item.resolutionStatus] || item.resolutionStatus

  const badgeClass = item.missingQuantity && item.resolutionStatus === 'MATCHED'
    ? 'ai-badge-ambiguous'
    : getStatusBadgeClass(item.resolutionStatus)

  return (
    <div className={`ai-item-card card ${item.confirmed ? 'ai-item-confirmed' : ''}`}>
      {/* Card Header */}
      <div className="ai-item-header">
        <div className="ai-item-header-left">
          <span className={`ai-status-badge ${badgeClass}`}>
            {item.resolutionStatus === 'MATCHED' && !item.missingQuantity && (
              <Check size={12} />
            )}
            {item.resolutionStatus === 'AMBIGUOUS' && <HelpCircle size={12} />}
            {item.resolutionStatus === 'UNMATCHED' && <AlertCircle size={12} />}
            {item.missingQuantity && item.resolutionStatus === 'MATCHED' && (
              <AlertCircle size={12} />
            )}
            {statusLabel}
          </span>
          <span className="ai-item-original-text">"{item.originalText}"</span>
        </div>
        <button
          className="ai-item-remove-btn"
          onClick={() => onRemove(item._key)}
          aria-label="Remove item"
          type="button"
        >
          <X size={16} />
        </button>
      </div>

      {/* Matched Product */}
      {item.resolutionStatus === 'MATCHED' && item.product && (
        <div className="ai-item-product">
          <div className="ai-item-product-info">
            <span className="ai-item-product-name">{item.product.name}</span>
            <span className="ai-item-product-sku">SKU: {item.product.sku}</span>
            <span className="ai-item-review-details">
              {item.priceAvailable && item.pricePerCase != null
                ? `${money(item.pricePerCase, item.currency)} / case · ${item.paidCases || 0} cases = ${money(Number(item.pricePerCase) * Number(item.paidCases || 0), item.currency)}`
                : 'Current price unavailable'}
              {Number.isInteger(item.availableCases) ? ` · ${item.availableCases} cases in stock` : ''}
              {item.schemeName ? ` · ${item.schemeName}` : ''}
            </span>
          </div>
          <div className="ai-quantity-control">
            <button
              className="ai-quantity-btn"
              onClick={() => onQuantityStep(item._key, -1)}
              disabled={typeof item.paidCases !== 'number' || item.paidCases <= 1}
              aria-label="Decrease quantity"
              type="button"
            >
              <Minus size={14} />
            </button>
            <input
              className="ai-quantity-input"
              type="number"
              min="1"
              value={item.paidCases}
              onChange={(e) => onQuantityChange(item._key, e.target.value)}
              aria-label="Paid cases"
            />
            <button
              className="ai-quantity-btn"
              onClick={() => onQuantityStep(item._key, 1)}
              aria-label="Increase quantity"
              type="button"
            >
              <Plus size={14} />
            </button>
            <span className="ai-quantity-unit">{item.unit === 'PIECE' ? 'pieces' : 'cases'}</span>
          </div>
        </div>
      )}

      {item.resolutionStatus === 'MATCHED' && item.product && !item.missingQuantity && (
        <div className="ai-confirm-group">
          {item.unitAmbiguous && <p className="ai-item-unmatched-hint">The message did not specify cases or pieces. Confirm you mean {item.unit === 'PIECE' ? 'pieces' : 'cases'}; no conversion is applied.</p>}
          <label className="ai-item-confirm"><input type="checkbox" checked={Boolean(item.confirmed)} onChange={(event) => onConfirm(item._key, event.target.checked)} />I reviewed this product and {item.unitAmbiguous ? `the ${item.unit === 'PIECE' ? 'piece' : 'case'} quantity` : `${item.unit === 'PIECE' ? 'piece' : 'case'} quantity`}.</label>
        </div>
      )}

      {/* Ambiguous — Candidate Selection */}
      {item.resolutionStatus === 'AMBIGUOUS' && item.candidates.length > 0 && (
        <div className="ai-item-ambiguous">
          <button
            className="ai-candidates-toggle"
            onClick={() => setCandidatesOpen(!candidatesOpen)}
            type="button"
          >
            <PackageSearch size={16} />
            {item.candidates.length} possible match{item.candidates.length !== 1 ? 'es' : ''}
            <ChevronDown
              size={14}
              className={candidatesOpen ? 'ai-chevron-open' : ''}
            />
          </button>
          {candidatesOpen && (
            <div className="ai-candidates-list">
              <p className="ai-candidates-hint">Select the correct product:</p>
              {item.candidates.map((candidate) => (
                <button
                  key={candidate.id}
                  className={`ai-candidate-option ${
                    item.selectedProductId === candidate.id
                      ? 'ai-candidate-selected'
                      : ''
                  }`}
                  onClick={() => onSelectCandidate(item._key, candidate)}
                  type="button"
                >
                  <div className="ai-candidate-info">
                    <span className="ai-candidate-name">{candidate.name}</span>
                    <span className="ai-candidate-sku">SKU: {candidate.sku}</span>
                  </div>
                  {item.selectedProductId === candidate.id && (
                    <Check size={16} className="ai-candidate-check" />
                  )}
                </button>
              ))}
              {/* Quantity control shows after selection */}
              {item.selectedProductId && (
                <div className="ai-quantity-control ai-quantity-after-select">
                  <button
                    className="ai-quantity-btn"
                    onClick={() => onQuantityStep(item._key, -1)}
                    disabled={typeof item.paidCases !== 'number' || item.paidCases <= 1}
                    aria-label="Decrease quantity"
                    type="button"
                  >
                    <Minus size={14} />
                  </button>
                  <input
                    className="ai-quantity-input"
                    type="number"
                    min="1"
                    value={item.paidCases}
                    onChange={(e) => onQuantityChange(item._key, e.target.value)}
                    aria-label="Paid cases"
                  />
                  <button
                    className="ai-quantity-btn"
                    onClick={() => onQuantityStep(item._key, 1)}
                    aria-label="Increase quantity"
                    type="button"
                  >
                    <Plus size={14} />
                  </button>
                  <span className="ai-quantity-unit">{item.unit === 'PIECE' ? 'pieces' : 'cases'}</span>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Unmatched */}
      {item.resolutionStatus === 'UNMATCHED' && (
        <div className="ai-item-unmatched">
          <p>{item.explanation || 'This product could not be identified in the catalogue.'}</p>
          <label className="ai-manual-select">Select product manually
            <select value={manualSelections[item._key] || ''} onChange={(event) => { onSelectManual(item._key, event.target.value) }}>
              <option value="">Choose from current catalogue</option>
              {catalogueProducts.map((product) => <option value={product.id} key={product.id}>{product.name} · {product.sku}</option>)}
            </select>
          </label>
          <p className="ai-item-unmatched-hint">
            Try rephrasing or remove this item.
          </p>
        </div>
      )}

      {/* Confidence Indicator (subtle) */}
      {item.confidence != null && item.confidence > 0 && item.confidence < 1 && (
        <div className="ai-item-confidence">
          Confidence: {Math.round(item.confidence * 100)}%
        </div>
      )}
    </div>
  )
}

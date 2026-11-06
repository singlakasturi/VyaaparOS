import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useOutletContext, useParams, useSearchParams } from 'react-router-dom'
import { AlertCircle, ArrowLeft, ArrowRight, Check, ChevronDown, Minus, Package, Plus, RefreshCw, Search, ShoppingBag, ShoppingCart, Sparkles, Tag, Trash2, Wifi, WifiOff } from 'lucide-react'
import { fetchCategories, fetchProducts } from '../../services/catalog.js'
import { addToCart, fetchCart, removeCartItem, updateCartQuantity } from '../../services/cart.js'
import { fetchOrder, fetchOrders, placeOrder } from '../../services/order.js'
import { addRetailerCartProduct, createOfflineRetailerOrder, createOrderSubmitter, getIndicativeCartTotals, getIndicativeFreeCases, getOrderHistoryRows, isCartLineAvailable, loadRetailerCart, loadRetailerCatalogue, removeRetailerCartItem, updateOfflineCartItems, updateRetailerCartItem } from '../../services/retailerOrdering.js'
import { cacheCatalog, getCachedCatalog, saveDraft, savePendingRetailerOrder, getDraftsByRetailer, deleteDraft, DRAFT_STATUS } from '../../services/db.js'
import { getEligibleRetailerOrders, getPendingRetailerOrders, syncAllDrafts, syncDraft } from '../../services/sync.js'
import { checkBackendReachable } from '../../services/api.js'

const money = (amount, currency = 'INR') => amount == null ? 'Price unavailable' : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount))
const dateTime = (value) => value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
const schemeText = (scheme) => {
  if (!scheme?.schemeAvailable) return null
  if (scheme.schemeType === 'BUY_X_GET_Y' || (scheme.buyQuantity && scheme.freeQuantity)) return `${scheme.schemeName || 'Offer'} · Buy ${scheme.buyQuantity}, get ${scheme.freeQuantity} free`
  if (scheme.discountPercentage != null) return `${scheme.schemeName || 'Offer'} · ${scheme.discountPercentage}% off`
  return scheme.schemeName || 'Offer available'
}

function PageHeading({ eyebrow, title, children }) {
  return <div className="retailer-page-heading"><div><span className="retailer-eyebrow">{eyebrow}</span><h1>{title}</h1></div>{children}</div>
}

function StateCard({ title, children, action, retry }) {
  return <section className="retailer-state-card"><span className="retailer-state-icon"><Package size={24} /></span><h2>{title}</h2><p>{children}</p>{action || (retry && <button className="retailer-button retailer-button-outline" type="button" onClick={retry}>Try again</button>)}</section>
}

function SchemeBadge({ scheme }) {
  const text = schemeText(scheme)
  return text ? <span className="retailer-scheme"><Tag size={14} />{text}</span> : null
}

function RetailerCatalogue({ retailer, retailerId, cartCount, setCartCount, userId }) {
  const [categories, setCategories] = useState([])
  const [category, setCategory] = useState('')
  const [query, setQuery] = useState('')
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [quantities, setQuantities] = useState({})
  const [cartQuantities, setCartQuantities] = useState({})
  const [adding, setAdding] = useState('')
  const [notice, setNotice] = useState('')
  const [expanded, setExpanded] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)
  const [stale, setStale] = useState(false)
  const requestSequence = useRef(0)

  const load = useCallback(async () => {
    const requestId = ++requestSequence.current
    setLoading(true); setError('')
    try {
      const result = await loadRetailerCatalogue({ retailerId, category, query, fetchCategories, fetchProducts, cacheCatalog, getCachedCatalog })
      if (requestId !== requestSequence.current) return
      setCategories(result.categories)
      setProducts(result.products)
      setStale(result.stale)
      if (navigator.onLine && !result.stale) {
        try {
          const currentCart = await fetchCart(retailerId)
          if (requestId === requestSequence.current) setCartQuantities(Object.fromEntries((currentCart?.items || []).map((item) => [item.productId, Number(item.paidCases || 0)])))
        } catch { if (requestId === requestSequence.current) setCartQuantities({}) }
      } else setCartQuantities({})
    } catch (err) { if (requestId === requestSequence.current) setError(err.message || 'Catalogue could not be loaded.') }
    finally { if (requestId === requestSequence.current) setLoading(false) }
  }, [retailerId, category, query])

  useEffect(() => { const timer = window.setTimeout(load, 180); return () => window.clearTimeout(timer) }, [load])
  useEffect(() => {
    const update = () => { setOnline(navigator.onLine); if (navigator.onLine) load() }
    window.addEventListener('online', update); window.addEventListener('offline', update)
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update) }
  }, [load])

  const add = async (product) => {
    const qty = quantities[product.id] || 1
    const stockConfirmed = online && !stale
    if (stockConfirmed && product.availableCases <= 0) { setNotice(`${product.name} is out of stock and was not added.`); return }
    if (stockConfirmed && !product.priceAvailable) { setNotice(`Price is unavailable for ${product.name}; it was not added.`); return }
    setAdding(product.id); setNotice('')
    try {
      const saveToOfflineCart = async () => {
        const drafts = await getDraftsByRetailer(retailerId, userId)
        let draft = drafts.find((item) => item.isOfflineWorkingCart && item.userId === userId)
        const items = updateOfflineCartItems(draft?.items || [], { ...product, stockVerified: false, scheme: { schemeAvailable: product.schemeAvailable, schemeType: product.schemeType, schemeName: product.schemeName, buyQuantity: product.buyQuantity, freeQuantity: product.freeQuantity, minimumQuantity: product.minimumQuantity, discountPercentage: product.discountPercentage } }, qty, { incrementExisting: true })
        draft = draft ? { ...draft, items } : { id: crypto.randomUUID(), userId, salespersonId: userId, retailerId, retailerName: retailer.businessName, status: DRAFT_STATUS.SAVED_LOCALLY, items, notes: '', isOfflineWorkingCart: true }
        await saveDraft(draft)
        setCartCount(items.reduce((sum, item) => sum + item.paidCases, 0))
        setNotice(`${product.name} added to your offline cart. Review the complete order before placing it.`)
      }
      if (!online || stale) {
        await saveToOfflineCart()
        return
      }
      const existingCart = await fetchCart(retailerId)
      const currentQuantity = (existingCart?.items || []).filter((item) => item.productId === product.id).reduce((sum, item) => sum + Number(item.paidCases || 0), 0)
      const result = await addRetailerCartProduct({ retailerId, product, quantity: qty, currentQuantity, addToCart })
      setCartQuantities(Object.fromEntries((result.cart?.items || []).map((item) => [item.productId, Number(item.paidCases || 0)])))
      setCartCount(result.count); setNotice(`${product.name} added to your cart.`)
    } catch (err) {
      if (err.status === 0) {
        try {
          const drafts = await getDraftsByRetailer(retailerId, userId)
          const draft = drafts.find((item) => item.isOfflineWorkingCart && item.userId === userId)
          const scheme = { schemeAvailable: product.schemeAvailable, schemeType: product.schemeType, schemeName: product.schemeName, buyQuantity: product.buyQuantity, freeQuantity: product.freeQuantity, minimumQuantity: product.minimumQuantity, discountPercentage: product.discountPercentage }
          const items = updateOfflineCartItems(draft?.items || [], { ...product, stockVerified: false, scheme }, qty, { incrementExisting: true })
          await saveDraft(draft ? { ...draft, items } : { id: crypto.randomUUID(), userId, salespersonId: userId, retailerId, retailerName: retailer.businessName, status: DRAFT_STATUS.SAVED_LOCALLY, items, notes: '', isOfflineWorkingCart: true })
          setCartCount(items.reduce((sum, item) => sum + item.paidCases, 0))
          setNotice(`${product.name} added to your offline cart. Review the complete order before placing it.`)
        } catch (saveError) { setNotice(saveError.message || 'Could not save this product to your offline cart.') }
      } else setNotice(err.message || 'Could not add this product. Please retry.')
    }
    finally { setAdding('') }
  }

  return <div className="retailer-page">
    <PageHeading eyebrow="ORDERING" title="Catalogue">
      <Link className="retailer-button retailer-button-primary" to="/retailer/cart"><ShoppingCart size={17} />View cart <span className="retailer-count-pill">{cartCount}</span></Link>
    </PageHeading>
    <section className="retailer-welcome"><div><span className="retailer-eyebrow">ORDERING FOR</span><h2>{retailer.businessName}</h2><p>{[retailer.city, retailer.state].filter(Boolean).join(', ') || 'Your business account'}</p></div><Link className="retailer-ai-link" to="/retailer/ai-assistant"><Sparkles size={17} />Quick order with AI<ArrowRight size={15} /></Link></section>
    <section className="retailer-catalog-tools" aria-label="Catalogue filters">
      <label className="retailer-search"><Search size={18} /><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by product name or SKU" aria-label="Search products" /></label>
      <label className="retailer-category"><span className="sr-only">Filter category</span><select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Filter category"><option value="">All categories</option>{categories.map((item) => <option key={item.id || item.slug || item.name} value={item.slug || item.name}>{item.name}</option>)}</select><ChevronDown size={16} /></label>
    </section>
    {notice && <div className={`retailer-notice ${/available|stock|exceed/i.test(notice) ? 'retailer-draft-error' : ''}`} role={/available|stock|exceed/i.test(notice) ? 'alert' : 'status'}>{/available|stock|exceed/i.test(notice) ? <AlertCircle size={17} /> : <Check size={17} />}{notice}</div>}
    {!online && <p className="retailer-offline-note" role="status">Showing cached catalogue data. Prices, stock, and indicative scheme estimates may be out of date. Nothing is submitted until you place the order and synchronization is confirmed.</p>}
    {online && stale && <p className="retailer-offline-note" role="status">The backend could not be reached. Showing cached catalogue data; prices and stock may be out of date.</p>}
    {loading ? <div className="retailer-product-grid">{Array.from({ length: 6 }, (_, index) => <div className="retailer-skeleton" key={index} />)}</div>
      : error ? <StateCard title="We couldn't load the catalogue" retry={load}>{error}</StateCard>
        : products.length === 0 ? <StateCard title="No products found">Try a different search or category.</StateCard>
          : <div className="retailer-product-grid">{products.map((product) => {
            const quantity = quantities[product.id] || 1
            const alreadyInCart = cartQuantities[product.id] || 0
            const noStock = !stale && online && product.availableCases <= 0
            const exceedsStock = !stale && online && quantity + alreadyInCart + getIndicativeFreeCases(product, quantity + alreadyInCart) > product.availableCases
            return <article className="retailer-product-card" key={product.id}>
              <div className="retailer-product-top"><span className="retailer-product-category">{product.category?.name || product.brand || 'Product'}</span><span className={`retailer-stock ${noStock ? 'retailer-stock-out' : ''}`}>{noStock ? 'Out of stock' : online && !stale ? `${product.availableCases} cases available` : `Stock unverified · cached ${product.availableCases ?? '—'} cases`}</span></div>
              <h2>{product.name}</h2><p className="retailer-sku">SKU {product.sku || '—'}</p>
              <p className="retailer-pack">{product.caseDescription || `${product.unitsPerCase || '—'} units per case`}{product.unitQuantity && product.unitName ? ` · ${product.unitQuantity} ${product.unitName}` : ''}</p>
              <div className="retailer-product-price">{product.priceAvailable ? money(product.pricePerCase, product.currency) : <span className="retailer-unavailable">Price unavailable</span>}<small>per case</small></div>
              <SchemeBadge scheme={{ schemeAvailable: product.schemeAvailable, schemeType: product.schemeType, schemeName: product.schemeName, buyQuantity: product.buyQuantity, freeQuantity: product.freeQuantity, discountPercentage: product.discountPercentage }} />
              {expanded === product.id && <p className="retailer-product-description">{product.description || `Brand: ${product.brand || '—'}. Price and any offer are confirmed by the server at checkout.`}</p>}
              <div className="retailer-product-actions"><div className="retailer-quantity" aria-label={`Quantity for ${product.name}`}><button type="button" aria-label="Decrease quantity" onClick={() => setQuantities((prev) => ({ ...prev, [product.id]: Math.max(1, quantity - 1) }))}><Minus size={15} /></button><span>{quantity}</span><button type="button" aria-label="Increase quantity" onClick={() => { const next = quantity + 1; const required = alreadyInCart + next + getIndicativeFreeCases(product, alreadyInCart + next); if (online && !stale && required > product.availableCases) setNotice(`Only ${product.availableCases} cases available; ${alreadyInCart} already in your cart. Requested quantity would need ${required} including free cases.`); else setQuantities((prev) => ({ ...prev, [product.id]: next })) }} disabled={noStock}><Plus size={15} /></button></div><button className="retailer-button retailer-button-primary retailer-add-button" type="button" onClick={() => add(product)} disabled={noStock || (online && !stale && (!product.priceAvailable || exceedsStock)) || adding === product.id}>{adding === product.id ? 'Adding…' : 'Add to cart'}</button></div>
              {exceedsStock && <p className="retailer-draft-error" role="alert">Only {product.availableCases} cases available; {alreadyInCart} already in your cart.</p>}
              <button className="retailer-details-toggle" type="button" aria-expanded={expanded === product.id} onClick={() => setExpanded(expanded === product.id ? '' : product.id)}>{expanded === product.id ? 'Less details' : 'Product details'}</button>
            </article>
          })}</div>}
  </div>
}

function CartTotals({ items = [], compact = false }) {
  const totals = getIndicativeCartTotals(items)
  if (!items.length) return null
  const unverified = items.filter((item) => item.stockVerified === false).length
  return <div className={`retailer-total-card ${compact ? 'retailer-total-card-compact' : ''}`} aria-label="Indicative cart totals">
    <div><span>Subtotal</span><strong>{money(totals.subtotal)}</strong></div>
    <div><span>Discounts</span><strong>−{money(totals.discount)}</strong></div>
    <div><span>Free cases</span><strong>{totals.freeCases}</strong></div>
    <div className="retailer-order-total"><span>Estimated total</span><strong>{totals.priceUnavailable ? 'Partial · ' : ''}{money(totals.estimatedTotal)}</strong></div>
    {totals.priceUnavailable > 0 && <p className="retailer-summary-note">{totals.priceUnavailable} line{totals.priceUnavailable === 1 ? '' : 's'} have no available price and are not included.</p>}
    {unverified > 0 && <p className="retailer-draft-error" role="status">{unverified} line{unverified === 1 ? ' has' : 's have'} unverified stock and {unverified === 1 ? 'is' : 'are'} excluded from this estimate.</p>}
    <p className="retailer-summary-note">Indicative only. Current prices, schemes, stock, and credit are checked by the server at checkout.</p>
  </div>
}

function RetailerCart({ retailerId, cartCount, setCartCount, userId }) {
  const [cart, setCart] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [busy, setBusy] = useState('')
  const load = useCallback(async () => { setLoading(true); setError(''); try { setCart(await loadRetailerCart({ retailerId, userId, fetchCart, getDraftsByRetailer, fetchProducts, isOnline: navigator.onLine })) } catch (err) { setError(err.message || 'Cart could not be loaded.') } finally { setLoading(false) } }, [retailerId, userId])
  useEffect(() => { load() }, [load])
  useEffect(() => { const reconnect = () => { if (navigator.onLine) load() }; window.addEventListener('online', reconnect); return () => window.removeEventListener('online', reconnect) }, [load])
  const setUpdatedCart = (next) => { setCart(next); setCartCount((next?.items || []).reduce((sum, item) => sum + item.paidCases, 0)) }
  const change = async (item, qty) => { if (qty < 1) return; setBusy(item.id); try { if (cart?.offline) { const drafts = await getDraftsByRetailer(retailerId, userId); const draft = drafts.find((row) => row.id === cart.id); const items = updateOfflineCartItems(draft.items, item, qty); await saveDraft({ ...draft, items }); setUpdatedCart({ ...cart, items: items.map((row) => ({ ...row, id: row.productId, name: row.productName })) }) } else setUpdatedCart(await updateRetailerCartItem({ retailerId, itemId: item.id, quantity: qty, availableCases: item.availableCases, freeCases: item.indicativeFreeCases || 0, scheme: item.scheme, updateCartQuantity, fetchCart })) } catch (err) { setError(err.message || 'Quantity could not be updated.') } finally { setBusy('') } }
  const remove = async (item) => { setBusy(item.id); try { if (cart?.offline) { const drafts = await getDraftsByRetailer(retailerId, userId); const draft = drafts.find((row) => row.id === cart.id); const items = draft.items.filter((row) => row.productId !== item.productId); await saveDraft({ ...draft, items }); setUpdatedCart({ ...cart, items: items.map((row) => ({ ...row, id: row.productId, name: row.productName })) }) } else setUpdatedCart(await removeRetailerCartItem({ retailerId, itemId: item.id, removeCartItem, fetchCart })) } catch (err) { setError(err.message || 'Item could not be removed.') } finally { setBusy('') } }

  return <div className="retailer-page"><PageHeading eyebrow="YOUR ORDER" title="Shopping cart"><Link className="retailer-text-link" to="/retailer"><ArrowLeft size={16} />Continue shopping</Link></PageHeading>
    {loading ? <div className="retailer-state"><RefreshCw className="retailer-spin" />Loading your cart…</div> : error ? <StateCard title="Cart needs a refresh" retry={load}>{error}</StateCard> : !cart?.items?.length ? <StateCard title="Your cart is empty" action={<Link className="retailer-button retailer-button-primary" to="/retailer">Browse catalogue<ArrowRight size={16} /></Link>}>Add products to start an order for {retailerId ? 'your business' : 'your account'}.</StateCard> : <div className="retailer-cart-layout"><section className="retailer-cart-list">{cart.items.map((item) => { const invalid = Boolean(item.stockInvalid); return <article className={`retailer-cart-row ${invalid ? 'retailer-cart-row-invalid' : ''}`} key={item.id}><div className="retailer-cart-product"><h2>{item.name}</h2><p>SKU {item.sku || '—'} · {item.stockVerified === false ? 'Stock unverified' : `${item.availableCases} cases available`}</p>{item.stockVerified === false && <p className="retailer-draft-error" role="status">Stock and price are unverified while offline. Your saved quantities are preserved.</p>}{invalid && <p className="retailer-draft-error" role="alert">{item.stockIssue || `Unavailable: ${item.availableCases ?? 0} cases available for ${item.paidCases} paid cases plus ${item.indicativeFreeCases || 0} free.`} Remove this line or reduce its quantity before checkout.</p>}<SchemeBadge scheme={item.scheme} />{item.indicativeFreeCases > 0 && <p>{item.indicativeFreeCases} free case{item.indicativeFreeCases === 1 ? '' : 's'} at current scheme</p>}</div><div className="retailer-cart-unit-price">{money(item.pricePerCase)}<small>per paid case · {item.stockVerified === false ? 'unverified offline estimate' : 'indicative'}</small></div><div className="retailer-quantity"><button disabled={busy === item.id || item.paidCases <= 1} onClick={() => change(item, item.paidCases - 1)} type="button" aria-label={`Decrease ${item.name} quantity`}><Minus size={15} /></button><span>{item.paidCases}</span><button disabled={busy === item.id || (item.stockVerified !== false && item.paidCases + (item.indicativeFreeCases || 0) >= item.availableCases)} onClick={() => change(item, item.paidCases + 1)} type="button" aria-label={`Increase ${item.name} quantity`}><Plus size={15} /></button></div><button className="retailer-icon-button" disabled={busy === item.id} onClick={() => remove(item)} type="button" aria-label={`Remove ${item.name}`}><Trash2 size={18} /></button></article>})}</section><aside className="retailer-order-summary"><span className="retailer-eyebrow">ORDER REVIEW</span><h2>{cart.items.length} product{cart.items.length === 1 ? '' : 's'}</h2><p>{cartCount} paid cases in your cart</p><CartTotals items={cart.items} />{navigator.onLine && cart.items.some((item) => !isCartLineAvailable(item)) && <p className="retailer-draft-error" role="alert">Resolve unavailable or unverified lines before checkout.</p>}<Link aria-disabled={navigator.onLine && cart.items.some((item) => !isCartLineAvailable(item))} className="retailer-button retailer-button-primary retailer-wide" to={navigator.onLine && cart.items.some((item) => !isCartLineAvailable(item)) ? '#' : '/retailer/checkout'} onClick={(event) => { if (navigator.onLine && cart.items.some((item) => !isCartLineAvailable(item))) event.preventDefault() }}>Review order<ArrowRight size={17} /></Link></aside></div>}
  </div>
}

function RetailerCheckout({ retailer, retailerId, setCartCount, userId }) {
  const navigate = useNavigate()
  const [cart, setCart] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [notes, setNotes] = useState(''); const [submitting, setSubmitting] = useState(false)
  const [online, setOnline] = useState(() => navigator.onLine)
  const submitOrder = useMemo(() => createOrderSubmitter({ retailerId, place: placeOrder, storage: window.sessionStorage, uuid: () => crypto.randomUUID(), onCartCleared: () => setCartCount(0) }), [retailerId, setCartCount])
  const load = useCallback(async () => { setLoading(true); setError(''); try { setCart(await loadRetailerCart({ retailerId, userId, fetchCart, getDraftsByRetailer, fetchProducts, isOnline: navigator.onLine })) } catch (err) { setError(err.message || 'Could not refresh your cart.') } finally { setLoading(false) } }, [retailerId, userId])
  useEffect(() => { load() }, [load])
  useEffect(() => { const update = () => { setOnline(navigator.onLine); if (navigator.onLine) load() }; window.addEventListener('online', update); window.addEventListener('offline', update); return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update) } }, [load])
  useEffect(() => {
    const onSynced = (event) => {
      if (event.detail?.userId === userId && event.detail?.retailerId === retailerId) load()
    }
    window.addEventListener('vyapaaros:offline-order-synced', onSynced)
    return () => window.removeEventListener('vyapaaros:offline-order-synced', onSynced)
  }, [load, retailerId, userId])
  const submit = async (event) => {
    event.preventDefault(); if (submitting || !cart?.items?.length) return
    if (online && cart.items.some((item) => item.stockInvalid || item.stockVerified === false)) {
      setError('Refresh stock and resolve unavailable lines before placing this order. Your items remain in the cart.')
      return
    }
    setSubmitting(true); setError('')
    try {
      const order = await submitOrder({ items: cart.items, cartId: cart.id, notes })
      setCart({ ...cart, items: [] })
      navigate(`/retailer/orders/${order.id}?confirmed=1`, { state: { order } })
    } catch (err) {
      const prefix = err.status === 0 ? 'We could not confirm whether the request completed. Retry to safely check the same order. ' : ''
      if (err.data?.code === 'INSUFFICIENT_STOCK' && Array.isArray(err.data.items)) {
        setCart((current) => current && ({
          ...current,
          items: current.items.map((line) => {
            const issue = err.data.items.find((candidate) => candidate.sku === line.sku)
            return issue ? {
              ...line,
              availableCases: issue.availableCases,
              stockVerified: true,
              stockInvalid: true,
              stockIssue: `${issue.sku} (${issue.productName}): requested ${issue.requestedCases} cases, ${issue.availableCases} available.`,
            } : line
          }),
        }))
      }
      setError(`${prefix}${err.message || 'The order could not be placed.'}`)
    } finally { setSubmitting(false) }
  }
  const saveOfflineDraft = async () => {
    if (!cart?.items?.length) return
    try {
      const order = createOfflineRetailerOrder({ retailerId, retailerName: retailer.businessName, userId, items: cart.items, notes, uuid: () => crypto.randomUUID(), id: cart.id || undefined })
      await savePendingRetailerOrder(order)
      setCart({ ...cart, items: [] })
      setCartCount(0)
      setNotice('Offline order saved as Pending sync. Cached prices, stock, and schemes may be stale; it has not reached the server.')
    } catch (saveError) {
      setError(saveError.message || 'Offline order could not be saved locally. Keep this page open and retry.')
    }
  }
  return <div className="retailer-page"><PageHeading eyebrow="CHECKOUT" title="Review your order"><Link className="retailer-text-link" to="/retailer/cart"><ArrowLeft size={16} />Back to cart</Link></PageHeading>
    {notice && <p className="retailer-notice" role="status">{notice}</p>}
    {loading ? <div className="retailer-state"><RefreshCw className="retailer-spin" />Refreshing server cart…</div> : error && !cart ? <StateCard title="We couldn't refresh the cart" retry={load}>{error}</StateCard> : !cart?.items?.length ? <StateCard title="Your cart is empty" action={<Link className="retailer-button retailer-button-primary" to="/retailer">Browse catalogue</Link>}>Add products before checkout.</StateCard> : <form className="retailer-checkout-layout" onSubmit={submit}><section className="retailer-checkout-main"><div className="retailer-review-card"><span className="retailer-eyebrow">DELIVERING TO</span><h2>{retailer.businessName}</h2><p>{[retailer.address, retailer.city, retailer.state, retailer.pincode].filter(Boolean).join(', ') || 'Business address on file'}</p></div><div className="retailer-review-card"><span className="retailer-eyebrow">ITEMS · {cart.items.length}</span>{cart.items.map((item) => <div className="retailer-review-item" key={item.id}><div><strong>{item.name}</strong><small>{item.paidCases} paid cases · {money(item.pricePerCase)} per case · {item.indicativeFreeCases || 0} indicative free</small><SchemeBadge scheme={item.scheme} />{item.stockVerified === false && <small className="retailer-draft-error">Stock and price unverified while offline; quantity preserved.</small>}{item.stockInvalid && <small className="retailer-draft-error">{item.stockIssue} Remove this line from your cart or adjust quantity before checkout.</small>}</div><span>{item.stockVerified === false ? 'Unverified' : item.stockInvalid ? 'Unavailable' : 'Ready'}</span></div>)}</div><CartTotals items={cart.items} /><label className="retailer-notes"><span>Order note <small>Optional</small></span><textarea maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Add a delivery or order note" /></label></section><aside className="retailer-order-summary retailer-checkout-summary"><span className="retailer-eyebrow">FINAL VALIDATION</span><h2>{online && !cart.offline ? 'Server-validated order' : 'Offline order review'}</h2><p>The amounts above are indicative. {online && !cart.offline ? 'We’ll recheck live stock, retailer pricing, applicable schemes, and credit before confirming.' : 'Cached prices, stock, and schemes may be stale. Placing offline saves this complete order on this device; it is not submitted until sync is confirmed.'} The final amount will be provided by the server after synchronization.</p><div className="retailer-summary-rule" /><p className="retailer-credit-context">Retailer: <strong>{retailer.businessName}</strong></p>{error && <div className="retailer-inline-error" role="alert"><AlertCircle size={16} />{error}</div>}{online && !cart.offline ? <button className="retailer-button retailer-button-primary retailer-wide" disabled={submitting || !cart.items.length || cart.items.some((item) => item.stockInvalid || item.stockVerified === false)} type="submit">{submitting ? 'Placing order…' : 'Place order'}</button> : <button className="retailer-button retailer-button-primary retailer-wide" disabled={submitting || !cart.items.length} type="button" onClick={saveOfflineDraft}>{submitting ? 'Saving…' : 'Place order offline'}<ArrowRight size={17} /></button>}{online && cart.items.some((item) => item.stockInvalid) && <p className="retailer-draft-error" role="alert">One or more lines exceed current stock. <Link to="/retailer/cart">Return to cart to adjust or remove them</Link>.</p>}<p className="retailer-fine-print">Order subject to server-side stock, pricing, scheme, and credit validation.</p></aside></form>}
  </div>
}

function OrderStatus({ status }) { const hold = status === 'CREDIT_HOLD'; return <span className={`retailer-status ${hold ? 'retailer-status-hold' : ''}`}>{String(status || 'UNKNOWN').replaceAll('_', ' ')}</span> }

function RetailerOrders({ retailerId, userId }) {
  const [orders, setOrders] = useState([])
  const [drafts, setDrafts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [syncFeedback, setSyncFeedback] = useState('')
  const [syncProgress, setSyncProgress] = useState(null)
  const [backendReachable, setBackendReachable] = useState(null)
  const [expanded, setExpanded] = useState('')
  const [editing, setEditing] = useState('')
  const [editItems, setEditItems] = useState([])
  const [editNotes, setEditNotes] = useState('')
  const [busy, setBusy] = useState('')
  const [online, setOnline] = useState(() => navigator.onLine)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const [orderResult, draftResult] = await Promise.allSettled([fetchOrders(retailerId), getDraftsByRetailer(retailerId, userId)])
    if (orderResult.status === 'fulfilled') setOrders(orderResult.value?.content || [])
    else setError(orderResult.reason?.message || 'Placed orders could not be refreshed. Locally saved drafts remain available below.')
    if (draftResult.status === 'fulfilled') {
      const localDrafts = draftResult.value.filter((draft) => draft.status !== DRAFT_STATUS.SUBMITTED && draft.status !== DRAFT_STATUS.CREDIT_HOLD)
      setDrafts(localDrafts)
      if (navigator.onLine) setBackendReachable(await checkBackendReachable())
      else setBackendReachable(false)
    } else setError((current) => [current, draftResult.reason?.message].filter(Boolean).join(' '))
    setLoading(false)
  }, [retailerId, userId])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    const updateConnectivity = () => {
      const isOnline = navigator.onLine
      setOnline(isOnline)
      if (!isOnline) setBackendReachable(false)
      else checkBackendReachable({ force: true }).then(setBackendReachable)
    }
    const updateBackend = (event) => setBackendReachable(Boolean(event.detail?.reachable))
    const updateSync = (event) => {
      if (event.detail?.scope !== 'retailer') return
      const detail = event.detail
      if (detail.state === 'started' || detail.state === 'syncing') {
        setSyncProgress(detail)
        setSyncFeedback('')
      } else {
        setSyncProgress(null)
        if (detail.state === 'completed') setSyncFeedback('All eligible orders synced.')
        else if (detail.state === 'partial') setSyncFeedback(`${detail.succeeded} order(s) synced; ${detail.failed} need attention. Review the saved orders below.`)
        else if (detail.state === 'failed') setSyncFeedback(`${detail.failed} order(s) need attention. Review each saved order for its reason.`)
        load()
      }
    }
    window.addEventListener('online', updateConnectivity)
    window.addEventListener('offline', updateConnectivity)
    window.addEventListener('vyapaaros:backend-status', updateBackend)
    window.addEventListener('vyapaaros:sync-status', updateSync)
    return () => {
      window.removeEventListener('online', updateConnectivity)
      window.removeEventListener('offline', updateConnectivity)
      window.removeEventListener('vyapaaros:backend-status', updateBackend)
      window.removeEventListener('vyapaaros:sync-status', updateSync)
    }
  }, [load])

  const startEdit = (draft) => { setExpanded(draft.id); setEditing(draft.id); setEditItems((draft.items || []).map((item) => ({ ...item }))); setEditNotes(draft.notes || '') }
  const saveEdit = async (draft) => {
    const items = editItems.filter((item) => Number(item.paidCases) > 0).map((item) => ({ ...item, paidCases: Number(item.paidCases) }))
    await saveDraft({ ...draft, items, notes: editNotes, status: DRAFT_STATUS.SAVED_LOCALLY, syncError: null })
    setEditing(''); await load()
  }
  const retry = async (draft) => {
    if (!online) { setError('Your browser is offline. The draft remains saved; retry after reconnecting.'); return }
    setBusy(draft.id); setError('')
    try {
      const reachable = await checkBackendReachable({ force: true })
      setBackendReachable(reachable)
      if (!reachable) throw new Error('The backend cannot be reached right now. Your order remains saved for retry.')
      await syncDraft(draft.id)
      await load()
    }
    catch (err) { setError(`Draft remains saved. Synchronization failed: ${err.message || 'Please retry.'}`); await load() }
    finally { setBusy('') }
  }
  const retryAll = async () => {
    if (!online) { setSyncFeedback('Offline · orders remain saved on this device.'); return }
    setBusy('all'); setError(''); setSyncFeedback('')
    try {
      const reachable = await checkBackendReachable({ force: true })
      setBackendReachable(reachable)
      if (!reachable) {
        setSyncFeedback('Backend unreachable · orders remain saved on this device. Try again when the server is reachable.')
        return
      }
      const result = await syncAllDrafts(drafts, { retailerOrdersOnly: true })
      await load()
      if (result.failed) setSyncFeedback(`${result.succeeded} order(s) synced; ${result.failed} need attention. Review each saved order for its reason.`)
      else if (result.total) setSyncFeedback('All eligible orders synced.')
      else setSyncFeedback('There are no orders ready to sync. Review orders marked as needing attention.')
    } catch (err) { setError(err.message || 'Synchronization could not start.') }
    finally { setBusy('') }
  }
  const removeDraft = async (draft) => {
    if (!window.confirm('Delete this local draft? It has not been submitted.')) return
    await deleteDraft(draft.id); await load()
  }
  const canRetry = (draft) => !draft.isOfflineWorkingCart && [DRAFT_STATUS.PENDING_SYNC, DRAFT_STATUS.SAVED_LOCALLY, DRAFT_STATUS.WAITING_FOR_CONNECTION, DRAFT_STATUS.READY_TO_SYNC, DRAFT_STATUS.SYNC_UNCERTAIN, DRAFT_STATUS.SYNCING].includes(draft.status)
  const statusLabel = (status) => String(status || DRAFT_STATUS.SAVED_LOCALLY).toLowerCase().replaceAll('_', ' ')

  const pendingOrders = getPendingRetailerOrders(drafts)
  const eligibleOrders = getEligibleRetailerOrders(drafts)
  const needsAttention = pendingOrders.filter((draft) => draft.status === DRAFT_STATUS.NEEDS_REVIEW || draft.status === DRAFT_STATUS.SYNC_UNCERTAIN).length
  const lastSyncedAt = drafts.map((draft) => draft.lastSyncedAt).filter(Boolean).sort().at(-1)
  const backendLabel = !online ? 'unreachable' : backendReachable === null ? 'checking' : backendReachable ? 'reachable' : 'unreachable'
  const syncLabel = syncProgress && ['started', 'syncing'].includes(syncProgress.state)
    ? `Syncing ${syncProgress.current} of ${syncProgress.total}`
    : !online
      ? `Offline · ${pendingOrders.length} order(s) waiting`
      : backendReachable === false
        ? `Online · backend unreachable · ${pendingOrders.length} order(s) waiting`
        : pendingOrders.length
          ? `Online · ${pendingOrders.length} order(s) to sync`
          : syncFeedback || 'All orders synced'
  const isSyncing = Boolean(busy) || Boolean(syncProgress && ['started', 'syncing'].includes(syncProgress.state))
  const syncButton = <button className="retailer-button retailer-button-primary" type="button" disabled={!online || !eligibleOrders.length || isSyncing} onClick={retryAll}>{isSyncing ? <><RefreshCw size={16} className="retailer-spin" />Syncing…</> : <><RefreshCw size={16} />Sync now</>}</button>
  return <div className="retailer-page"><PageHeading eyebrow="YOUR BUSINESS" title="Orders"><Link className="retailer-button retailer-button-primary" to="/retailer"><ShoppingBag size={17} />New order</Link></PageHeading>
    <section className="retailer-review-card retailer-sync-card" aria-live="polite"><span className="retailer-eyebrow">CONNECTIVITY & SYNC</span><div className="retailer-sync-status">{online ? <Wifi size={18} aria-hidden="true" /> : <WifiOff size={18} aria-hidden="true" />}<strong>{syncLabel}</strong></div><p>Browser: {online ? 'Online' : 'Offline'} · Backend: {backendLabel} · Pending orders: {pendingOrders.length}{needsAttention ? ` · ${needsAttention} need attention` : ''}</p>{lastSyncedAt && <small>Last successful sync: {dateTime(lastSyncedAt)}</small>}{!online && <p className="retailer-summary-note">Orders stay on this device until your connection returns.</p>}{online && backendReachable === false && <p className="retailer-summary-note">The backend health check failed. Sync is paused; saved orders are safe to retry.</p>}{syncFeedback && <p className={needsAttention ? 'retailer-draft-error' : 'retailer-summary-note'} role="status">{syncFeedback}</p>}{pendingOrders.length > 0 && syncButton}</section>
    {loading && <div className="retailer-state"><RefreshCw className="retailer-spin" />Loading orders and local drafts…</div>}
    {error && <div className="retailer-inline-error" role="alert">{error}<button className="retailer-button retailer-button-outline" onClick={load} type="button">Refresh</button></div>}
    {!loading && <>
      <section className="retailer-review-card"><span className="retailer-eyebrow">PLACED ORDERS</span><h2>Confirmed by server</h2>
        {!orders.length ? <p>Your placed orders will appear here. A local draft is not a placed order.</p> : <div className="retailer-orders-list">{getOrderHistoryRows(orders).map((order) => <Link className="retailer-order-row" key={order.id} to={`/retailer/orders/${order.id}`}><div><strong>{order.reference}</strong><span>{dateTime(order.createdAt)} · {order.itemCount} items</span></div><OrderStatus status={order.status} /><strong>{money(order.amount, order.currency)}</strong><ArrowRight className="retailer-order-arrow" size={17} /></Link>)}</div>}
      </section>
      <section className="retailer-review-card retailer-drafts-card"><div className="retailer-drafts-heading"><div><span className="retailer-eyebrow">SAVED ON THIS DEVICE</span><h2>Offline orders & drafts <span className="retailer-draft-count">{drafts.length}</span></h2></div>{pendingOrders.length > 0 && syncButton}</div>
        {!drafts.length ? <p>No local orders or drafts. Orders placed offline will appear here for review and retry.</p> : <div className="retailer-drafts-list">{drafts.map((draft) => <article className="retailer-draft-row" key={draft.id}>
          <button className="retailer-draft-heading" type="button" aria-expanded={expanded === draft.id} onClick={() => setExpanded(expanded === draft.id ? '' : draft.id)}><span><strong>{draft.retailerName || 'Retailer order draft'} · {String(draft.id).slice(0, 8)}</strong><small>Created {dateTime(draft.createdAt)} · {(draft.items || []).length} products · {(draft.items || []).reduce((sum, item) => sum + Number(item.paidCases || 0), 0)} paid cases{draft.indicativeTotals ? ` · estimated ${money(draft.indicativeTotals.estimatedTotal)}` : ''}</small></span><span className="retailer-draft-status">{statusLabel(draft.status)}</span></button>
          {expanded === draft.id && <div className="retailer-draft-detail"><p>{draft.explicitlyPlacedOffline ? 'Pending sync. This order is saved locally and has not reached the server. Cached prices, stock, and schemes may be stale.' : 'Local draft. It has not been submitted.'}</p>{(editing === draft.id ? editItems : draft.items || []).map((item, index) => <div className="retailer-draft-item" key={`${draft.id}-${item.productId}`}><span>{item.productName || item.sku || item.productId}</span>{editing === draft.id ? <><input aria-label={`Cases for ${item.productName || item.sku || 'product'}`} type="number" min="1" value={item.paidCases} onChange={(event) => setEditItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, paidCases: event.target.value } : row))} /><button className="retailer-icon-button" type="button" aria-label="Remove draft item" onClick={() => setEditItems((current) => current.filter((_, rowIndex) => rowIndex !== index))}><Trash2 size={16} /></button></> : <strong>{item.paidCases} cases</strong>}</div>)}
            {editing === draft.id && <label className="retailer-notes"><span>Order note</span><textarea maxLength={1000} value={editNotes} onChange={(event) => setEditNotes(event.target.value)} /></label>}
            {draft.syncError && <p className="retailer-draft-error" role="status">{draft.syncError}</p>}
            <div className="retailer-draft-actions">{editing === draft.id ? <button className="retailer-button retailer-button-primary" type="button" onClick={() => saveEdit(draft)}>Save changes</button> : <button className="retailer-button retailer-button-outline" type="button" disabled={[DRAFT_STATUS.SYNC_UNCERTAIN, DRAFT_STATUS.SYNCING].includes(draft.status)} onClick={() => startEdit(draft)}>Edit draft</button>}{canRetry(draft) && <button className="retailer-button retailer-button-primary" type="button" disabled={busy === draft.id || !online || !draft.items?.length} onClick={() => retry(draft)}>{busy === draft.id ? 'Retrying…' : 'Retry sync'}</button>}<button className="retailer-button retailer-button-outline" type="button" onClick={() => removeDraft(draft)}>Discard local order</button></div>
          </div>}
        </article>)}</div>}
      </section>
    </>}
  </div>
}

function RetailerOrderDetail() {
  const { orderId } = useParams(); const [search] = useSearchParams(); const nav = useNavigate()
  const [order, setOrder] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState('')
  const load = useCallback(async () => { setLoading(true); setError(''); try { setOrder(await fetchOrder(orderId)) } catch (err) { setError(err.message || 'Order details could not be loaded.') } finally { setLoading(false) } }, [orderId])
  useEffect(() => { load() }, [load])
  return <div className="retailer-page"><PageHeading eyebrow={search.get('confirmed') ? 'ORDER CONFIRMED' : 'ORDER DETAILS'} title={order?.orderNumber || 'Order details'}><Link className="retailer-text-link" to="/retailer/orders"><ArrowLeft size={16} />All orders</Link></PageHeading>
    {loading ? <div className="retailer-state"><RefreshCw className="retailer-spin" />Loading order details…</div> : error ? <StateCard title="Order could not be loaded" retry={load}>{error}</StateCard> : order && <><div className={`retailer-confirmation ${order.status === 'CREDIT_HOLD' ? 'retailer-confirmation-hold' : ''}`}><span className="retailer-confirmation-mark">{order.status === 'CREDIT_HOLD' ? <AlertCircle size={22} /> : <Check size={22} />}</span><div><h2>{order.status === 'CREDIT_HOLD' ? 'Order placed · credit review required' : 'Order placed successfully'}</h2><p>{order.status === 'CREDIT_HOLD' ? order.creditHoldReason || 'This order is awaiting credit review.' : 'Your order has been received and is subject to fulfillment.'}</p></div><OrderStatus status={order.status} /></div><div className="retailer-order-summary-grid"><section className="retailer-review-card"><span className="retailer-eyebrow">ORDER INFO</span><dl><div><dt>Reference</dt><dd>{order.orderNumber || order.id}</dd></div><div><dt>Placed</dt><dd>{dateTime(order.createdAt)}</dd></div><div><dt>Status</dt><dd><OrderStatus status={order.status} /></dd></div>{order.creditHoldReason && <div><dt>Credit review</dt><dd>{order.creditHoldReason}</dd></div>}</dl></section><section className="retailer-review-card retailer-total-card"><span className="retailer-eyebrow">SERVER TOTAL</span><div><span>Subtotal</span><strong>{money(order.baseAmount, order.currency)}</strong></div><div><span>Discount</span><strong>−{money(order.discountAmount, order.currency)}</strong></div><div className="retailer-order-total"><span>Final amount</span><strong>{money(order.finalAmount, order.currency)}</strong></div></section></div><section className="retailer-review-card retailer-snapshot-card"><span className="retailer-eyebrow">ITEM PRICE SNAPSHOTS</span>{(order.items || []).map((item, index) => <div className="retailer-snapshot-row" key={`${item.productId}-${index}`}><div><strong>{item.name}</strong><small>SKU {item.sku || '—'} · {item.paidCases} paid + {item.freeCases} free = {item.totalCases} cases</small>{item.schemeName && <span className="retailer-scheme"><Tag size={14} />{item.schemeName}{item.schemeType ? ` · ${String(item.schemeType).replaceAll('_', ' ')}` : ''}</span>}</div><div><span>{money(item.unitPricePerCase, item.currency)} / case</span><strong>{money(item.finalAmount, item.currency)}</strong></div></div>)}</section><button className="retailer-button retailer-button-outline" onClick={() => nav('/retailer')} type="button">Back to catalogue</button></>}
  </div>
}

export function RetailerAccount() {
  const { retailer } = useOutletContext()
  return <div className="retailer-page"><PageHeading eyebrow="BUSINESS PROFILE" title="Account" /><section className="retailer-review-card retailer-account-card"><span className="retailer-eyebrow">RETAILER DETAILS</span><h2>{retailer.businessName}</h2><dl>{[['Owner', retailer.ownerName], ['Email', retailer.contactEmail], ['Phone', retailer.contactPhone], ['Address', [retailer.address, retailer.city, retailer.state, retailer.pincode].filter(Boolean).join(', ')], ['Account status', retailer.status], ['Credit limit', money(retailer.creditLimit)], ['Outstanding balance', money(retailer.outstandingBalance)]].filter(([, value]) => value).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></section></div>
}

export default function RetailerOrdering({ view }) {
  const context = useOutletContext()
  if (view === 'catalogue') return <RetailerCatalogue {...context} />
  if (view === 'cart') return <RetailerCart {...context} />
  if (view === 'checkout') return <RetailerCheckout {...context} />
  if (view === 'orders') return <RetailerOrders {...context} />
  if (view === 'detail') return <RetailerOrderDetail {...context} />
  return <StateCard title="Page not found">Go back to your catalogue.</StateCard>
}

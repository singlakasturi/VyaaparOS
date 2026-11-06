export async function loadRetailerCatalogue({ retailerId, category, query, fetchCategories, fetchProducts, cacheCatalog, getCachedCatalog }) {
  try {
    const [categories, page] = await Promise.all([
      fetchCategories(retailerId),
      fetchProducts({ retailerId, category: category || undefined, q: query.trim() || undefined, size: 100 }),
    ])
    const result = { categories: Array.isArray(categories) ? categories : [], products: page?.content || [] }
    if (cacheCatalog) await cacheCatalog(retailerId, result.products, result.categories)
    return result
  } catch (error) {
    if (!getCachedCatalog) throw error
    const cached = await getCachedCatalog(retailerId)
    if (!cached) throw error
    const normalizedQuery = query.trim().toLowerCase()
    const products = (cached.products || []).filter((product) => {
      const normalizedCategory = String(category || '').toLowerCase()
      const matchesCategory = !category || product.category?.slug?.toLowerCase() === normalizedCategory || product.category?.name?.toLowerCase() === normalizedCategory
      const matchesQuery = !normalizedQuery || `${product.name || ''} ${product.sku || ''}`.toLowerCase().includes(normalizedQuery)
      return matchesCategory && matchesQuery
    })
    return { categories: cached.categories || [], products, stale: true }
  }
}

export async function addRetailerCartProduct({ retailerId, product, quantity, currentQuantity = 0, addToCart }) {
  if (product.availableCases <= 0) throw new Error('This product is out of stock.')
  if (!product.priceAvailable) throw new Error('A price is not available for this product.')
  if (!Number.isInteger(quantity) || quantity < 1) throw new Error('Quantity must be at least one case.')
  const requestedPaidCases = quantity + currentQuantity
  const freeCases = getIndicativeFreeCases(product, requestedPaidCases)
  if (requestedPaidCases + freeCases > product.availableCases) throw new Error(`Only ${product.availableCases} cases are available; ${currentQuantity} already in your cart and this quantity needs ${requestedPaidCases + freeCases} including free cases.`)
  const cart = await addToCart(retailerId, product.id, quantity + currentQuantity)
  return { cart, count: (cart?.items || []).reduce((sum, item) => sum + item.paidCases, 0) }
}

export async function updateRetailerCartItem({ retailerId, itemId, quantity, availableCases, freeCases = 0, scheme, updateCartQuantity, fetchCart }) {
  if (!Number.isInteger(quantity) || quantity < 1) throw new Error('Quantity must be at least one case.')
  const requiredCases = quantity + (scheme ? getIndicativeFreeCases({ scheme }, quantity) : freeCases)
  if (Number.isInteger(availableCases) && requiredCases > availableCases) throw new Error(`Only ${availableCases} cases are available including free cases.`)
  await updateCartQuantity(itemId, quantity)
  return fetchCart(retailerId)
}

export function getIndicativeFreeCases(product, paidCases) {
  const scheme = product.scheme || product
  const type = String(scheme.schemeType || '').toUpperCase()
  const buy = Number(scheme.buyQuantity || 0)
  const free = Number(scheme.freeQuantity || 0)
  return type.includes('BUY_X_GET_Y') && buy > 0 ? Math.floor(Number(paidCases) / buy) * free : 0
}

export async function removeRetailerCartItem({ retailerId, itemId, removeCartItem, fetchCart }) {
  await removeCartItem(itemId)
  return fetchCart(retailerId)
}

export function createOrderSubmitter({ retailerId, place, storage, uuid, onCartCleared }) {
  let inFlight = null
  return ({ items, notes = '', cartId = '' }) => {
    if (inFlight) return inFlight
    const fingerprint = JSON.stringify([cartId, items.map(({ productId, paidCases }) => [productId, paidCases]).sort()])
    const key = `vyapaaros.checkout.${retailerId}.${fingerprint}`
    let clientOrderId = storage.getItem(key)
    if (!clientOrderId) {
      clientOrderId = uuid()
      storage.setItem(key, clientOrderId)
    }
    inFlight = Promise.resolve().then(() => place(retailerId, clientOrderId, notes))
      .then(async (order) => {
        storage.removeItem(key)
        if (onCartCleared) onCartCleared()
        return order
      })
      .finally(() => { inFlight = null })
    return inFlight
  }
}

export function getOrderHistoryRows(orders) {
  return (orders || []).map((order) => ({
    id: order.id,
    reference: order.orderNumber || `Order ${String(order.id).slice(0, 8)}`,
    status: order.status || 'UNKNOWN',
    createdAt: order.createdAt,
    itemCount: order.items?.length || 0,
    amount: order.finalAmount,
    currency: order.currency,
  }))
}

export function getIndicativeCartTotals(items = []) {
  return items.filter(isCartLineAvailable).reduce((totals, item) => {
    if (!item.priceAvailable || item.indicativeFinalAmount == null) {
      totals.priceUnavailable += 1
    } else {
      totals.subtotal += Number(item.indicativeBaseAmount || 0)
      totals.discount += Number(item.indicativeDiscountAmount || 0)
      totals.estimatedTotal += Number(item.indicativeFinalAmount || 0)
    }
    totals.freeCases += Number(item.indicativeFreeCases || 0)
    return totals
  }, { subtotal: 0, discount: 0, estimatedTotal: 0, freeCases: 0, priceUnavailable: 0 })
}

export function isCartLineAvailable(item) {
  return item.stockVerified !== false
    && !(Number.isInteger(item.availableCases) && Number(item.availableCases) < Number(item.paidCases || 0) + Number(item.indicativeFreeCases || 0))
}

export function validateCartAgainstCatalogue(cart, products, { verified = true } = {}) {
  const byId = new Map((products || []).map((product) => [product.id || product.productId, product]))
  return {
    ...cart,
    stockVerified: verified,
    items: (cart?.items || []).map((item) => {
      const product = byId.get(item.productId)
      if (!product) {
        const stockKnown = Number.isInteger(item.availableCases)
        const availableCases = stockKnown ? item.availableCases : null
        const requestedCases = Number(item.paidCases || 0) + Number(item.indicativeFreeCases || 0)
        const stockIssue = !verified
          ? 'Stock is unverified while offline.'
          : stockKnown && availableCases < requestedCases
            ? `Only ${availableCases} case${availableCases === 1 ? '' : 's'} available; this line needs ${requestedCases} including free cases.`
            : 'Product is no longer available in the current catalogue.'
        return { ...item, availableCases, stockVerified: verified && stockKnown, stockIssue, stockInvalid: Boolean(verified && (!stockKnown || availableCases < requestedCases)) }
      }
      const availableCases = Number(product.availableCases ?? 0)
      const requestedCases = Number(item.paidCases || 0) + Number(item.indicativeFreeCases || 0)
      const stockIssue = availableCases < requestedCases
        ? `Only ${availableCases} case${availableCases === 1 ? '' : 's'} available; this line needs ${requestedCases} including free cases.`
        : ''
      return {
        ...item,
        availableCases,
        stockVerified: verified,
        stockIssue: verified ? stockIssue : 'Stock is unverified while offline.',
        stockInvalid: Boolean(verified && stockIssue),
        name: product.name || item.name,
        sku: product.sku || item.sku,
        pricePerCase: product.pricePerCase ?? item.pricePerCase,
        priceAvailable: product.priceAvailable ?? item.priceAvailable,
        scheme: product.scheme || item.scheme,
      }
    }),
  }
}

export function createOfflineRetailerOrder({ retailerId, retailerName, userId, items, notes = '', uuid, id, now = () => new Date().toISOString() }) {
  if (!retailerId || !userId || !Array.isArray(items) || items.length === 0) {
    throw new Error('Add at least one product before placing an offline order.')
  }
  const lines = items.map((item, index) => ({
    productId: item.productId,
    position: Number.isInteger(item.position) ? item.position : index,
    productName: item.name || item.productName,
    sku: item.sku,
    paidCases: Number(item.paidCases),
    cachedPricePerCase: item.pricePerCase ?? item.cachedPricePerCase ?? null,
    priceAvailable: Boolean(item.priceAvailable),
    indicativeFreeCases: Number(item.indicativeFreeCases || 0),
    indicativeBaseAmount: item.indicativeBaseAmount ?? null,
    indicativeDiscountAmount: item.indicativeDiscountAmount ?? null,
    indicativeFinalAmount: item.indicativeFinalAmount ?? null,
    scheme: item.scheme || null,
  }))
  if (lines.some((item) => !item.productId || !Number.isInteger(item.paidCases) || item.paidCases < 1)) {
    throw new Error('Review product quantities before placing this offline order.')
  }
  const totals = getIndicativeCartTotals(items)
  return {
    id: id || uuid(),
    userId,
    salespersonId: userId,
    retailerId,
    retailerName,
    items: lines,
    notes,
    createdAt: now(),
    indicativeTotals: totals,
    pricingSnapshotAt: now(),
  }
}

export function updateOfflineCartItems(items, product, quantity, { incrementExisting = false } = {}) {
  const current = (items || [])
    .map((item, index) => ({ ...item, position: Number.isInteger(item.position) ? item.position : index }))
    .sort((left, right) => left.position - right.position)
  const index = current.findIndex((item) => item.productId === product.productId || item.productId === product.id)
  const line = {
    productId: product.productId || product.id,
    position: index >= 0 ? current[index].position : current.reduce((max, item) => Math.max(max, item.position), -1) + 1,
    productName: product.productName || product.name,
    sku: product.sku,
    paidCases: Number(quantity) + (incrementExisting && index >= 0 ? Number(current[index].paidCases || 0) : 0),
    pricePerCase: product.pricePerCase ?? product.cachedPricePerCase ?? null,
    priceAvailable: product.priceAvailable !== false,
    indicativeFreeCases: Number(product.indicativeFreeCases || 0),
    indicativeBaseAmount: product.indicativeBaseAmount ?? null,
    indicativeDiscountAmount: product.indicativeDiscountAmount ?? null,
    indicativeFinalAmount: product.indicativeFinalAmount ?? null,
    availableCases: product.availableCases,
    scheme: product.scheme || null,
  }
  if (!Number.isInteger(line.paidCases) || line.paidCases < 1) throw new Error('Quantity must be at least one case.')
  if (line.priceAvailable && line.pricePerCase != null) {
    const base = Number(line.pricePerCase) * line.paidCases
    const schemeType = product.schemeType || product.scheme?.schemeType
    const buy = Number(product.buyQuantity || product.scheme?.buyQuantity || 0)
    const free = Number(product.freeQuantity || product.scheme?.freeQuantity || 0)
    const minimum = Number(product.minimumQuantity || product.scheme?.minimumQuantity || 0)
    const percentage = Number(product.discountPercentage || product.scheme?.discountPercentage || 0)
    line.indicativeBaseAmount = Number(base.toFixed(2))
    if ((schemeType === 'BUY_X_GET_Y_FREE' || schemeType === 'BUY_X_GET_Y') && buy > 0 && free > 0) {
      line.indicativeFreeCases = Math.floor(line.paidCases / buy) * free
      line.indicativeDiscountAmount = 0
      line.indicativeFinalAmount = line.indicativeBaseAmount
    } else if (schemeType === 'SLAB_DISCOUNT' && line.paidCases >= minimum && percentage > 0) {
      line.indicativeDiscountAmount = Number((base * percentage / 100).toFixed(2))
      line.indicativeFinalAmount = Number((base - line.indicativeDiscountAmount).toFixed(2))
    } else {
      line.indicativeDiscountAmount = 0
      line.indicativeFinalAmount = line.indicativeBaseAmount
    }
  }
  if (index >= 0) current[index] = line
  else current.push(line)
  return current
}

export async function loadRetailerCart({ retailerId, userId, fetchCart, getDraftsByRetailer, isOnline, fetchProducts }) {
  const localCart = async () => {
    const drafts = await getDraftsByRetailer(retailerId, userId)
    const draft = drafts.find((item) => item.isOfflineWorkingCart && item.userId === userId)
    return draft ? { id: draft.id, retailerId, offline: true, items: (draft.items || []).map((item, index) => ({ ...item, position: Number.isInteger(item.position) ? item.position : index, id: item.productId, name: item.productName, pricePerCase: item.pricePerCase, indicative: true })).sort((left, right) => left.position - right.position) } : null
  }
  if (isOnline) {
    try {
      const remote = await fetchCart(retailerId)
      const cart = remote?.items?.length ? remote : (await localCart()) || remote
      if (!cart) return cart
      if (fetchProducts) {
        try {
          const catalogue = await fetchProducts({ retailerId, size: 100 })
          return validateCartAgainstCatalogue(cart, catalogue?.content || catalogue || [], { verified: true })
        } catch {
          return validateCartAgainstCatalogue(cart, cart.items || [], { verified: !cart.offline })
        }
      }
      return validateCartAgainstCatalogue(cart, cart.items || [], { verified: true })
    } catch (error) {
      if (error?.status && error.status !== 0) throw error
      const cart = (await localCart()) || { id: null, retailerId, offline: true, items: [] }
      return validateCartAgainstCatalogue(cart, [], { verified: false })
    }
  }
  const cart = (await localCart()) || { id: null, retailerId, offline: true, items: [] }
  return validateCartAgainstCatalogue(cart, [], { verified: false })
}

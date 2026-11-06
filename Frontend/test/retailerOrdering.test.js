import test from 'node:test'
import assert from 'node:assert/strict'
import { addRetailerCartProduct, createOfflineRetailerOrder, createOrderSubmitter, getIndicativeCartTotals, getOrderHistoryRows, isCartLineAvailable, loadRetailerCart, loadRetailerCatalogue, removeRetailerCartItem, updateOfflineCartItems, updateRetailerCartItem, validateCartAgainstCatalogue } from '../src/services/retailerOrdering.js'

test('catalogue requests selected retailer, category and search and normalizes server pages', async () => {
  let request
  const result = await loadRetailerCatalogue({ retailerId: 'retailer-1', category: 'dairy', query: ' milk ', fetchCategories: async () => [{ id: 'dairy', name: 'Dairy' }], fetchProducts: async (options) => { request = options; return { content: [{ id: 'p1' }] } } })
  assert.deepEqual(request, { retailerId: 'retailer-1', category: 'dairy', q: 'milk', size: 100 })
  assert.equal(result.products[0].id, 'p1')
  assert.equal(result.categories[0].name, 'Dairy')
})

test('catalogue loading handles an empty server page', async () => {
  const result = await loadRetailerCatalogue({ retailerId: 'r', category: '', query: '', fetchCategories: async () => null, fetchProducts: async () => ({}) })
  assert.deepEqual(result, { categories: [], products: [] })
})

test('catalogue falls back to a cached catalogue when backend is unreachable', async () => {
  const result = await loadRetailerCatalogue({ retailerId: 'r', category: 'food', query: 'tea', fetchCategories: async () => { throw new Error('offline') }, fetchProducts: async () => [], getCachedCatalog: async () => ({ categories: [{ name: 'Food' }], products: [{ name: 'Tea 250 g', category: { name: 'Food' } }, { name: 'Rice', category: { name: 'Food' } }] }) })
  assert.equal(result.stale, true)
  assert.deepEqual(result.products.map((product) => product.name), ['Tea 250 g'])
})

test('indicative totals add server-calculated no-scheme, free-case and slab-discount lines', () => {
  const result = getIndicativeCartTotals([
    { priceAvailable: true, indicativeBaseAmount: 200, indicativeDiscountAmount: 0, indicativeFinalAmount: 200, indicativeFreeCases: 0 },
    { priceAvailable: true, indicativeBaseAmount: 1000, indicativeDiscountAmount: 100, indicativeFinalAmount: 900, indicativeFreeCases: 1 },
    { priceAvailable: true, indicativeBaseAmount: 500, indicativeDiscountAmount: 25, indicativeFinalAmount: 475, indicativeFreeCases: 0 },
    { priceAvailable: false, indicativeFinalAmount: null, indicativeFreeCases: 0 },
  ])
  assert.deepEqual(result, { subtotal: 1700, discount: 125, estimatedTotal: 1575, freeCases: 1, priceUnavailable: 1 })
  assert.deepEqual(getIndicativeCartTotals([]), { subtotal: 0, discount: 0, estimatedTotal: 0, freeCases: 0, priceUnavailable: 0 })
})

test('cart add follows server results and rejects unavailable products', async () => {
  let args
  const result = await addRetailerCartProduct({ retailerId: 'r', product: { id: 'p', availableCases: 4, priceAvailable: true }, quantity: 2, addToCart: async (...values) => { args = values; return { items: [{ paidCases: 2 }, { paidCases: 3 }] } } })
  assert.deepEqual(args, ['r', 'p', 2])
  assert.equal(result.count, 5)
  await assert.rejects(() => addRetailerCartProduct({ retailerId: 'r', product: { id: 'x', availableCases: 0, priceAvailable: true }, quantity: 1, addToCart: async () => assert.fail('must not add') }), /out of stock/)
  await assert.rejects(() => addRetailerCartProduct({ retailerId: 'r', product: { id: 'x', availableCases: 4, priceAvailable: true }, quantity: 3, currentQuantity: 2, addToCart: async () => assert.fail('must not add over stock') }), /Only 4 cases are available/)
  await assert.rejects(() => addRetailerCartProduct({ retailerId: 'r', product: { id: 'x', availableCases: 3, priceAvailable: true, schemeType: 'BUY_X_GET_Y_FREE', buyQuantity: 3, freeQuantity: 1 }, quantity: 2, currentQuantity: 1, addToCart: async () => assert.fail('must count free cases') }), /including free cases/)
  let requested
  await addRetailerCartProduct({ retailerId: 'r', product: { id: 'p', availableCases: 8, priceAvailable: true }, quantity: 2, currentQuantity: 3, addToCart: async (_retailer, _product, total) => { requested = total; return { items: [] } } })
  assert.equal(requested, 5)
})

test('cart quantity and remove actions refresh from server state', async () => {
  const calls = []
  const fetchCart = async (retailerId) => { calls.push(['refresh', retailerId]); return { retailerId, items: [] } }
  const updated = await updateRetailerCartItem({ retailerId: 'r', itemId: 'line', quantity: 3, updateCartQuantity: async (...args) => calls.push(['update', ...args]), fetchCart })
  const removed = await removeRetailerCartItem({ retailerId: 'r', itemId: 'line', removeCartItem: async (id) => calls.push(['remove', id]), fetchCart })
  assert.deepEqual(calls, [['update', 'line', 3], ['refresh', 'r'], ['remove', 'line'], ['refresh', 'r']])
  assert.equal(updated.retailerId, 'r')
  assert.equal(removed.retailerId, 'r')
  await assert.rejects(() => updateRetailerCartItem({ retailerId: 'r', itemId: 'line', quantity: 0, updateCartQuantity: async () => assert.fail(), fetchCart }), /at least one/)
  await assert.rejects(() => updateRetailerCartItem({ retailerId: 'r', itemId: 'line', quantity: 4, availableCases: 4, freeCases: 1, updateCartQuantity: async () => assert.fail(), fetchCart }), /including free cases/)
})

test('checkout suppresses concurrent duplicates and reuses the ID after a failed request', async () => {
  const values = new Map(); const storage = { getItem: (key) => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
  let calls = 0; const ids = []; let release
  const submit = createOrderSubmitter({ retailerId: 'r', storage, uuid: () => 'stable-id', place: async (_retailerId, id) => { calls += 1; ids.push(id); if (calls === 1) throw new Error('timeout'); await new Promise((resolve) => { release = resolve }); return { id: 'order-1' } } })
  const items = [{ productId: 'p', paidCases: 2 }]
  await assert.rejects(submit({ items }), /timeout/)
  const first = submit({ items }); const duplicate = submit({ items })
  assert.equal(first, duplicate)
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(calls, 2)
  assert.deepEqual(ids, ['stable-id', 'stable-id'])
  release()
  assert.deepEqual(await first, { id: 'order-1' })
  assert.equal(values.size, 0)
})

test('checkout clears the badge only after confirmed success and keys retries to the server cart', async () => {
  const values = new Map(); const storage = { getItem: (key) => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
  const cleared = []
  let shouldFail = true
  const submit = createOrderSubmitter({ retailerId: 'r', storage, uuid: () => `id-${values.size}`, onCartCleared: () => cleared.push(true), place: async () => { if (shouldFail) throw new Error('failed'); return { id: 'order' } } })
  const items = [{ productId: 'p', paidCases: 1 }]
  await assert.rejects(submit({ items, cartId: 'cart-1' }))
  assert.deepEqual(cleared, [])
  shouldFail = false
  await submit({ items, cartId: 'cart-1' })
  assert.deepEqual(cleared, [true])
  await submit({ items, cartId: 'cart-2' })
  assert.deepEqual(cleared, [true, true])
})

test('order history rows preserve server status, references and totals', () => {
  assert.deepEqual(getOrderHistoryRows([{ id: '12345678-abcd', status: 'CREDIT_HOLD', orderNumber: 'ORD-8', finalAmount: 250, currency: 'INR', items: [{}, {}] }]), [{ id: '12345678-abcd', reference: 'ORD-8', status: 'CREDIT_HOLD', createdAt: undefined, itemCount: 2, amount: 250, currency: 'INR' }])
})

test('offline retailer cart supports multiple products, quantity updates, and a complete stable local order', () => {
  let items = updateOfflineCartItems([], { id: 'p1', name: 'Rice', pricePerCase: 250, priceAvailable: true, indicativeBaseAmount: 500, indicativeFinalAmount: 500 }, 2)
  items = updateOfflineCartItems(items, { id: 'p2', name: 'Tea', pricePerCase: 100, priceAvailable: true, indicativeBaseAmount: 100, indicativeDiscountAmount: 10, indicativeFinalAmount: 90 }, 1)
  items = updateOfflineCartItems(items, items[0], 3)
  const order = createOfflineRetailerOrder({ retailerId: 'r1', retailerName: 'Shop', userId: 'u1', items, notes: 'Deliver morning', id: 'stable-order-id', uuid: () => 'unused', now: () => '2026-11-05T00:00:00.000Z' })
  assert.equal(order.id, 'stable-order-id')
  assert.equal(order.items.length, 2)
  assert.equal(order.items[0].paidCases, 3)
  assert.equal(order.notes, 'Deliver morning')
  assert.equal(order.indicativeTotals.estimatedTotal, 850)
  assert.equal(order.status, undefined)
})

test('offline indicative estimates follow the visible cached buy/get and slab rules without treating them as authoritative', () => {
  const buyGet = updateOfflineCartItems([], { id: 'p1', name: 'Rice', pricePerCase: 100, priceAvailable: true, schemeType: 'BUY_X_GET_Y_FREE', buyQuantity: 3, freeQuantity: 1 }, 6)[0]
  assert.equal(buyGet.indicativeFreeCases, 2)
  assert.equal(buyGet.indicativeFinalAmount, 600)
  const slab = updateOfflineCartItems([], { id: 'p2', name: 'Tea', pricePerCase: 100, priceAvailable: true, schemeType: 'SLAB_DISCOUNT', minimumQuantity: 4, discountPercentage: 10 }, 5)[0]
  assert.equal(slab.indicativeDiscountAmount, 50)
  assert.equal(slab.indicativeFinalAmount, 450)
})

test('offline working cart remains visible after reconnect when the server cart is empty', async () => {
  const cart = await loadRetailerCart({ retailerId: 'r', userId: 'u', isOnline: true, fetchCart: async () => ({ id: 'server-cart', items: [] }), getDraftsByRetailer: async () => [{ id: 'local-cart', userId: 'u', retailerId: 'r', isOfflineWorkingCart: true, items: [{ productId: 'p', productName: 'Tea', paidCases: 2 }] }] })
  assert.equal(cart.id, 'local-cart')
  assert.equal(cart.offline, true)
  assert.equal(cart.items[0].name, 'Tea')
})

test('existing cart lines that became unavailable remain visible, invalidate the bill, and can be removed', () => {
  const cart = validateCartAgainstCatalogue({ items: [{ id: 'line', productId: 'tea', paidCases: 15, indicativeFreeCases: 1, indicativeBaseAmount: 1770, indicativeDiscountAmount: 100, indicativeFinalAmount: 1670 }] }, [{ id: 'tea', name: 'Tea leaf 250 g', sku: 'SKU-TE-250', availableCases: 0 }])
  assert.equal(cart.items.length, 1)
  assert.equal(cart.items[0].stockInvalid, true)
  assert.match(cart.items[0].stockIssue, /0 cases available/)
  assert.equal(isCartLineAvailable(cart.items[0]), false)
  assert.deepEqual(getIndicativeCartTotals(cart.items), { subtotal: 0, discount: 0, estimatedTotal: 0, freeCases: 0, priceUnavailable: 0 })
})

test('offline cart preserves intended quantity and does not count cached stock or price as verified', async () => {
  const items = updateOfflineCartItems([], { id: 'tea', name: 'Tea leaf 250 g', sku: 'SKU-TE-250', availableCases: 0, pricePerCase: 118, priceAvailable: true, stockVerified: false }, 15)
  const cart = await loadRetailerCart({ retailerId: 'r', userId: 'u', isOnline: false, fetchCart: async () => assert.fail('must not use server while offline'), getDraftsByRetailer: async () => [{ id: 'offline', userId: 'u', isOfflineWorkingCart: true, items }] })
  assert.equal(cart.items[0].paidCases, 15)
  assert.equal(cart.items[0].stockVerified, false)
  assert.equal(getIndicativeCartTotals(cart.items).estimatedTotal, 0)
})

test('adding the same product to an offline catalogue cart adds quantity while cart edit replaces quantity', () => {
  const first = updateOfflineCartItems([], { id: 'p', name: 'Tea', pricePerCase: 100 }, 2, { incrementExisting: true })
  const second = updateOfflineCartItems(first, { id: 'p', name: 'Tea', pricePerCase: 100 }, 2, { incrementExisting: true })
  assert.equal(second[0].paidCases, 4)
  const edited = updateOfflineCartItems(second.filter((item) => item.productId !== 'p'), second[0], 3)
  assert.equal(edited[0].paidCases, 3)
})

test('quantity changes preserve explicit item order and new products append after existing lines', () => {
  let items = []
  for (const id of ['A', 'B', 'C']) {
    items = updateOfflineCartItems(items, { id, name: id, pricePerCase: 10 }, 1)
  }
  items = updateOfflineCartItems(items, items[2], 2)
  items = updateOfflineCartItems(items, items[0], 3)
  assert.deepEqual(items.map((item) => item.productId), ['A', 'B', 'C'])
  assert.deepEqual(items.map((item) => item.position), [0, 1, 2])
  assert.deepEqual(items.map((item) => item.paidCases), [3, 1, 2])

  items = items.filter((item) => item.productId !== 'B')
  items = updateOfflineCartItems(items, { id: 'D', name: 'D', pricePerCase: 10 }, 1)
  assert.deepEqual(items.map((item) => item.productId), ['A', 'C', 'D'])
  assert.deepEqual(items.map((item) => item.position), [0, 2, 3])
})

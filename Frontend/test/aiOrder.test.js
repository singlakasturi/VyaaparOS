import { describe, it, beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { parseOrderText, applyOrderItems, fetchCart, getAcceptedAiOrderItems } from '../src/services/aiOrder.js'
import { ApiError } from '../src/services/api.js'

describe('AI Order Assistant Services and Business Logic', () => {
  const dummyRetailerId = '11111111-2222-3333-4444-555555555555'

  beforeEach(() => {
    // Clear any token or fetch mocks
    mock.reset()
    // Setup mock sessionStorage
    globalThis.window = {
      sessionStorage: {
        store: {},
        getItem(key) { return this.store[key] || null },
        setItem(key, val) { this.store[key] = String(val) },
        removeItem(key) { delete this.store[key] }
      },
      dispatchEvent: () => {}
    }
  })

  it('requires explicit user confirmation before parsed items can be applied', () => {
    const items = [
      { selectedProductId: 'p1', paidCases: 5, resolutionStatus: 'MATCHED', confirmed: false },
      { selectedProductId: 'p2', paidCases: 2, resolutionStatus: 'MATCHED', confirmed: true, unit: 'CASE' },
      { selectedProductId: 'p5', paidCases: 1, resolutionStatus: 'MATCHED', confirmed: true },
      { selectedProductId: 'p3', paidCases: 1, resolutionStatus: 'AMBIGUOUS', confirmed: true },
      { selectedProductId: 'p4', paidCases: 3, resolutionStatus: 'MATCHED', confirmed: true, removed: true },
    ]
    assert.deepEqual(getAcceptedAiOrderItems(items), [items[1]])
  })

  it('1. parseOrderText calls POST /api/ai/orders/parse with retailerId and text', async () => {
    let capturedUrl = null
    let capturedOptions = null

    globalThis.fetch = mock.fn(async (url, options) => {
      capturedUrl = url
      capturedOptions = options
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          parseRequestId: 'req-123',
          originalText: '5 peti biscuit',
          status: 'PARSED',
          items: [
            {
              originalText: '5 peti biscuit',
              resolutionStatus: 'MATCHED',
              product: { id: 'p-1', sku: 'SKU-BS-075', name: 'Glucose biscuit 75 g' },
              candidates: [],
              paidCases: 5,
              unit: 'CASE',
              confidence: 0.94,
              explanation: 'Product matched against the active catalogue.'
            }
          ],
          missingInformation: []
        })
      }
    })

    const result = await parseOrderText(dummyRetailerId, '5 peti biscuit')

    assert.equal(capturedUrl, 'http://localhost:8080/api/ai/orders/parse')
    assert.equal(capturedOptions.method, 'POST')
    const body = JSON.parse(capturedOptions.body)
    assert.equal(body.retailerId, dummyRetailerId)
    assert.equal(body.text, '5 peti biscuit')
    assert.equal(result.status, 'PARSED')
    assert.equal(result.items.length, 1)
    assert.equal(result.items[0].product.name, 'Glucose biscuit 75 g')
    assert.equal(result.items[0].paidCases, 5)
  })

  it('2. parseOrderText handles 503 AI provider unavailable error', async () => {
    globalThis.fetch = mock.fn(async () => ({
      ok: false,
      status: 503,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({
        message: 'AI provider is unavailable.',
        status: 503
      })
    }))

    await assert.rejects(
      async () => await parseOrderText(dummyRetailerId, '5 peti biscuit'),
      (err) => {
        assert.ok(err instanceof ApiError)
        assert.equal(err.status, 503)
        assert.equal(err.message, 'AI provider is unavailable.')
        return true
      }
    )
  })

  it('3. parseOrderText handles 401 unauthorized / session expired', async () => {
    globalThis.fetch = mock.fn(async () => ({
      ok: false,
      status: 401,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({
        message: 'Authentication required.',
        status: 401
      })
    }))

    await assert.rejects(
      async () => await parseOrderText(dummyRetailerId, '5 peti biscuit'),
      (err) => {
        assert.ok(err instanceof ApiError)
        assert.equal(err.status, 401)
        return true
      }
    )
  })

  it('4. parseOrderText parses multiple items with mixed resolution statuses', async () => {
    globalThis.fetch = mock.fn(async () => ({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({
        parseRequestId: 'req-456',
        originalText: '5 peti biscuit aur 2 peti detergent aur kuch chocolate',
        status: 'PARTIALLY_PARSED',
        items: [
          {
            originalText: '5 peti biscuit',
            resolutionStatus: 'MATCHED',
            product: { id: 'p-1', sku: 'SKU-BS-075', name: 'Glucose biscuit 75 g' },
            candidates: [],
            paidCases: 5,
            unit: 'CASE',
            confidence: 0.95,
            explanation: 'Product matched.'
          },
          {
            originalText: '2 peti detergent',
            resolutionStatus: 'AMBIGUOUS',
            product: null,
            candidates: [
              { id: 'p-2', sku: 'SKU-DET-1K', name: 'Washing powder 1 kg' },
              { id: 'p-3', sku: 'SKU-DET-500', name: 'Washing powder 500 g' }
            ],
            paidCases: 2,
            unit: 'CASE',
            confidence: 0.85,
            explanation: 'Multiple active catalogue products match.'
          },
          {
            originalText: 'kuch chocolate',
            resolutionStatus: 'UNMATCHED',
            product: null,
            candidates: [],
            paidCases: null,
            unit: 'CASE',
            confidence: 0.2,
            explanation: 'No active catalogue product matched this description.'
          }
        ],
        missingInformation: ['No active catalogue product matched this description.']
      })
    }))

    const result = await parseOrderText(dummyRetailerId, '5 peti biscuit aur 2 peti detergent aur kuch chocolate')

    assert.equal(result.status, 'PARTIALLY_PARSED')
    assert.equal(result.items.length, 3)

    // Matched item
    assert.equal(result.items[0].resolutionStatus, 'MATCHED')
    assert.equal(result.items[0].product.sku, 'SKU-BS-075')
    assert.equal(result.items[0].paidCases, 5)

    // Ambiguous item
    assert.equal(result.items[1].resolutionStatus, 'AMBIGUOUS')
    assert.equal(result.items[1].candidates.length, 2)
    assert.equal(result.items[1].paidCases, 2)

    // Unmatched item
    assert.equal(result.items[2].resolutionStatus, 'UNMATCHED')
    assert.equal(result.items[2].product, null)
    assert.equal(result.items[2].paidCases, null)
  })

  it('5. applyOrderItems calls POST /api/ai/orders/apply with accepted items only', async () => {
    let capturedUrl = null
    let capturedOptions = null

    globalThis.fetch = mock.fn(async (url, options) => {
      capturedUrl = url
      capturedOptions = options
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          id: 'cart-1',
          retailerId: dummyRetailerId,
          items: [
            {
              id: 'ci-1',
              productId: 'p-1',
              sku: 'SKU-BS-075',
              name: 'Glucose biscuit 75 g',
              paidCases: 5,
              availableCases: 20,
              pricePerCase: 450.00,
              priceSource: 'BASE_PRICE',
              scheme: {
                schemeAvailable: true,
                schemeType: 'BUY_N_GET_M_FREE',
                schemeName: 'Buy 5 Get 1 Free',
                buyQuantity: 5,
                freeQuantity: 1
              },
              indicative: true
            }
          ]
        })
      }
    })

    const acceptedItems = [
      { productId: 'p-1', paidCases: 5 }
    ]

    const cart = await applyOrderItems(dummyRetailerId, acceptedItems)

    assert.equal(capturedUrl, 'http://localhost:8080/api/ai/orders/apply')
    assert.equal(capturedOptions.method, 'POST')
    const body = JSON.parse(capturedOptions.body)
    assert.equal(body.retailerId, dummyRetailerId)
    assert.deepEqual(body.items, [{ productId: 'p-1', paidCases: 5 }])
    assert.equal(cart.items.length, 1)
    assert.equal(cart.items[0].scheme.schemeAvailable, true)
    assert.equal(cart.items[0].scheme.schemeName, 'Buy 5 Get 1 Free')
  })

  it('6. fetchCart queries GET /api/cart?retailerId=... to refresh cart', async () => {
    let capturedUrl = null

    globalThis.fetch = mock.fn(async (url) => {
      capturedUrl = url
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          id: 'cart-1',
          retailerId: dummyRetailerId,
          items: []
        })
      }
    })

    await fetchCart(dummyRetailerId)
    assert.equal(capturedUrl, `http://localhost:8080/api/cart?retailerId=${dummyRetailerId}`)
  })

  it('7. Parsing does NOT call cart apply or modify cart', async () => {
    let calledEndpoints = []

    globalThis.fetch = mock.fn(async (url) => {
      calledEndpoints.push(url)
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          parseRequestId: 'req-789',
          originalText: '2 peti biscuit',
          status: 'PARSED',
          items: [],
          missingInformation: []
        })
      }
    })

    await parseOrderText(dummyRetailerId, '2 peti biscuit')

    assert.equal(calledEndpoints.length, 1)
    assert.ok(calledEndpoints[0].includes('/api/ai/orders/parse'))
    assert.ok(!calledEndpoints.some(url => url.includes('/apply')))
    assert.ok(!calledEndpoints.some(url => url.includes('/api/cart')))
  })

  it('8. Duplicate product IDs are prevented when building apply payload', () => {
    const rawItems = [
      { selectedProductId: 'prod-A', paidCases: 3, removed: false, resolutionStatus: 'MATCHED' },
      { selectedProductId: 'prod-A', paidCases: 2, removed: false, resolutionStatus: 'MATCHED' },
      { selectedProductId: 'prod-B', paidCases: 5, removed: false, resolutionStatus: 'MATCHED' }
    ]

    // Deduplication logic test
    const productMap = new Map()
    for (const item of rawItems) {
      if (item.removed || !item.selectedProductId || item.paidCases <= 0 || item.resolutionStatus !== 'MATCHED') continue
      if (productMap.has(item.selectedProductId)) {
        // combine quantities or keep first
        productMap.set(item.selectedProductId, productMap.get(item.selectedProductId) + item.paidCases)
      } else {
        productMap.set(item.selectedProductId, item.paidCases)
      }
    }

    const uniqueApplyItems = Array.from(productMap.entries()).map(([productId, paidCases]) => ({
      productId,
      paidCases
    }))

    assert.equal(uniqueApplyItems.length, 2)
    assert.equal(uniqueApplyItems.find(i => i.productId === 'prod-A').paidCases, 5)
    assert.equal(uniqueApplyItems.find(i => i.productId === 'prod-B').paidCases, 5)
  })

  it('9. Missing quantity items are excluded from apply until resolved', () => {
    const items = [
      { selectedProductId: 'prod-1', paidCases: null, removed: false, resolutionStatus: 'MATCHED', missingQuantity: true },
      { selectedProductId: 'prod-2', paidCases: 0, removed: false, resolutionStatus: 'MATCHED', missingQuantity: true },
      { selectedProductId: 'prod-3', paidCases: 4, removed: false, resolutionStatus: 'MATCHED', missingQuantity: false }
    ]

    const accepted = items.filter(
      item => !item.removed && item.selectedProductId && item.paidCases > 0 && item.resolutionStatus === 'MATCHED'
    )

    assert.equal(accepted.length, 1)
    assert.equal(accepted[0].selectedProductId, 'prod-3')
    assert.equal(accepted[0].paidCases, 4)
  })

  it('10. Ambiguous items are excluded from apply until a candidate is selected', () => {
    const items = [
      { selectedProductId: null, candidates: [{ id: 'cand-1', name: 'Item 1' }], paidCases: 2, removed: false, resolutionStatus: 'AMBIGUOUS' },
      { selectedProductId: 'cand-1', candidates: [{ id: 'cand-1', name: 'Item 1' }], paidCases: 2, removed: false, resolutionStatus: 'MATCHED' }
    ]

    const accepted = items.filter(
      item => !item.removed && item.selectedProductId && item.paidCases > 0 && item.resolutionStatus === 'MATCHED'
    )

    assert.equal(accepted.length, 1)
    assert.equal(accepted[0].selectedProductId, 'cand-1')
  })
})

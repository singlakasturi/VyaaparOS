import { describe, it, beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import {
  saveDraft,
  savePendingRetailerOrder,
  getDraft,
  getAllDrafts,
  getDraftsByRetailer,
  deleteDraft,
  updateDraftStatus,
  cacheAssignedRetailers,
  getCachedAssignedRetailers,
  cacheCatalog,
  getCachedCatalog,
  clearUserCache,
  DRAFT_STATUS,
} from '../src/services/db.js'
import { autoSyncRetailerOrders, getEligibleRetailerOrders, syncDraft, syncAllDrafts } from '../src/services/sync.js'

describe('Offline Ordering & Synchronization Suite', () => {
  const salespersonId = 'sp-offline-1234'
  const retailerId = 'ret-offline-5678'
  const dummyDraftId = 'c1234567-89ab-cdef-0123-456789abcdef'

  beforeEach(() => {
    mock.reset()
    try {
      Object.defineProperty(globalThis.navigator, 'onLine', {
        value: true,
        configurable: true,
        writable: true,
      })
    } catch {
      // Ignore if cannot redefine
    }
    globalThis.window = {
      sessionStorage: {
        store: {},
        getItem(key) { return this.store[key] || null },
        setItem(key, val) { this.store[key] = String(val) },
        removeItem(key) { delete this.store[key] },
      },
      dispatchEvent: () => {},
    }
  })

  it('1. saveDraft creates a local draft with stable UUID, timestamps, and default status', async () => {
    const draft = {
      id: dummyDraftId,
      salespersonId,
      retailerId,
      retailerName: 'Gupta Kirana',
      retailerCity: 'Kanpur',
      items: [
        {
          productId: 'prod-001',
          productName: 'Parle-G 70g',
          sku: 'SKU-PG-70',
          packSize: '70g',
          paidCases: 5,
          cachedPrice: 120,
        },
      ],
      notes: 'Urgent delivery by 10 AM',
    }

    const saved = await saveDraft(draft)
    assert.equal(saved.id, dummyDraftId)
    assert.equal(saved.status, DRAFT_STATUS.SAVED_LOCALLY)
    assert.ok(saved.createdAt)
    assert.ok(saved.updatedAt)
    assert.equal(saved.items.length, 1)
    assert.equal(saved.items[0].paidCases, 5)
  })

  it('2. getDraft retrieves an existing draft by its UUID', async () => {
    const draft = await getDraft(dummyDraftId)
    assert.ok(draft)
    assert.equal(draft.id, dummyDraftId)
    assert.equal(draft.retailerName, 'Gupta Kirana')
  })

  it('3. getAllDrafts filters by salespersonId and isolates other users drafts', async () => {
    const otherDraft = {
      id: 'other-draft-uuid',
      salespersonId: 'sp-different-user',
      retailerId: 'ret-9999',
      retailerName: 'Sharma Traders',
      items: [],
    }
    await saveDraft(otherDraft)

    const spDrafts = await getAllDrafts(salespersonId)
    assert.ok(spDrafts.every((d) => d.salespersonId === salespersonId))
    assert.ok(!spDrafts.some((d) => d.id === 'other-draft-uuid'))
  })

  it('retailer drafts persist under the owning account and are isolated by retailer', async () => {
    await saveDraft({ id: 'retailer-draft-1', salespersonId, userId: salespersonId, retailerId, items: [{ productId: 'p1', paidCases: 2 }] })
    await saveDraft({ id: 'retailer-draft-2', salespersonId, retailerId: 'another-retailer', items: [{ productId: 'p2', paidCases: 1 }] })
    const drafts = await getDraftsByRetailer(retailerId, salespersonId)
    assert.ok(drafts.some((draft) => draft.id === 'retailer-draft-1'))
    assert.ok(drafts.every((draft) => draft.retailerId === retailerId))
    assert.equal(drafts[0].status, DRAFT_STATUS.SAVED_LOCALLY)
    assert.ok(drafts[0].createdAt)
  })

  it('complete offline retailer order remains pending sync with its stable ID and owner', async () => {
    const order = await savePendingRetailerOrder({ id: 'offline-order-stable', userId: salespersonId, retailerId, retailerName: 'Gupta Kirana', items: [{ productId: 'p1', paidCases: 2 }], notes: 'Morning delivery' })
    assert.equal(order.status, DRAFT_STATUS.PENDING_SYNC)
    assert.equal(order.id, 'offline-order-stable')
    assert.equal(order.explicitlyPlacedOffline, true)
    assert.equal(order.notes, 'Morning delivery')
    assert.equal((await getDraft(order.id)).userId, salespersonId)
  })

  it('offline cart line order and positions survive draft save and reload', async () => {
    const id = 'ordered-offline-cart'
    const items = ['A', 'B', 'C'].map((productId, position) => ({ productId, position, paidCases: position + 1 }))
    await saveDraft({ id, userId: salespersonId, retailerId, isOfflineWorkingCart: true, items })
    const afterReload = await getDraft(id)
    assert.deepEqual(afterReload.items.map((item) => item.productId), ['A', 'B', 'C'])
    assert.deepEqual(afterReload.items.map((item) => item.position), [0, 1, 2])
  })

  it('a retailer working cart cannot sync until the retailer explicitly places the offline order', async () => {
    const id = 'retailer-working-cart-not-submit'
    await saveDraft({ id, userId: salespersonId, salespersonId, retailerId, isOfflineWorkingCart: true, status: DRAFT_STATUS.SAVED_LOCALLY, items: [{ productId: 'p1', paidCases: 1 }] })
    globalThis.fetch = mock.fn(async () => assert.fail('working cart must not call the order API'))
    await assert.rejects(() => syncDraft(id), /choose Place order/)
  })

  it('4. updateDraftStatus records sync attempt, error, and server response', async () => {
    const updated = await updateDraftStatus(
      dummyDraftId,
      DRAFT_STATUS.NEEDS_REVIEW,
      'Insufficient inventory for SKU-PG-70.',
      null
    )

    assert.equal(updated.status, DRAFT_STATUS.NEEDS_REVIEW)
    assert.equal(updated.syncError, 'Insufficient inventory for SKU-PG-70.')
    assert.ok(updated.lastSyncAttemptAt)
  })

  it('5. Caches assigned retailers and product catalogues with timestamps for offline browsing', async () => {
    const retailers = [
      { id: retailerId, businessName: 'Gupta Kirana', city: 'Kanpur', status: 'ACTIVE' },
    ]
    await cacheAssignedRetailers(salespersonId, retailers)

    const cachedRet = await getCachedAssignedRetailers(salespersonId)
    assert.ok(cachedRet)
    assert.equal(cachedRet.retailers.length, 1)
    assert.equal(cachedRet.retailers[0].businessName, 'Gupta Kirana')
    assert.ok(cachedRet.cachedAt)

    const products = [{ id: 'p1', name: 'Parle-G', resolvedPrice: 120 }]
    const categories = [{ id: 'c1', name: 'Biscuits' }]
    await cacheCatalog(retailerId, products, categories)

    const cachedCat = await getCachedCatalog(retailerId)
    assert.ok(cachedCat)
    assert.equal(cachedCat.products.length, 1)
    assert.equal(cachedCat.categories.length, 1)
    assert.ok(cachedCat.cachedAt)
  })

  it('6. syncDraft synchronizes successfully using stable clientOrderId and records server order', async () => {
    let capturedUrl = null
    let capturedBody = null

    globalThis.fetch = mock.fn(async (url, options) => {
      capturedUrl = url
      capturedBody = JSON.parse(options.body)
      return {
        ok: true,
        status: 201,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          id: 'server-order-uuid-999',
          orderNumber: 'ORD-2026-888',
          status: 'SUBMITTED',
          retailerId,
          finalAmount: 600,
          baseAmount: 600,
          discountAmount: 0,
          createdAt: new Date().toISOString(),
        }),
      }
    })

    const synced = await syncDraft(dummyDraftId)
    assert.equal(capturedUrl, 'http://localhost:8080/api/orders')
    // Crucial requirement: identical draft.id is preserved as clientOrderId
    assert.equal(capturedBody.clientOrderId, dummyDraftId)
    assert.equal(capturedBody.retailerId, retailerId)
    assert.equal(capturedBody.items.length, 1)
    assert.equal(capturedBody.items[0].productId, 'prod-001')
    assert.equal(capturedBody.items[0].paidCases, 5)

    assert.equal(synced.status, DRAFT_STATUS.SUBMITTED)
    assert.ok(synced.serverOrder)
    assert.equal(synced.serverOrder.orderNumber, 'ORD-2026-888')
  })

  it('7. syncDraft handles CREDIT_HOLD as a successful order with hold status', async () => {
    const holdDraftId = 'hold-draft-uuid-555'
    await saveDraft({
      id: holdDraftId,
      salespersonId,
      retailerId,
      retailerName: 'Gupta Kirana',
      items: [{ productId: 'p1', paidCases: 10, cachedPrice: 1000 }],
      notes: '',
    })

    globalThis.fetch = mock.fn(async () => ({
      ok: true,
      status: 201,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({
        id: 'ord-hold-777',
        orderNumber: 'ORD-HOLD-001',
        status: 'CREDIT_HOLD',
        creditHoldReason: 'Order exceeds retailer credit limit.',
        retailerId,
        finalAmount: 10000,
      }),
    }))

    const result = await syncDraft(holdDraftId)
    assert.equal(result.status, DRAFT_STATUS.CREDIT_HOLD)
    assert.ok(result.serverOrder)
    assert.equal(result.serverOrder.status, 'CREDIT_HOLD')
    assert.equal(result.serverOrder.creditHoldReason, 'Order exceeds retailer credit limit.')
  })

  it('8. syncDraft marks 409 conflict as NEEDS_REVIEW without deleting draft', async () => {
    const conflictDraftId = 'conflict-draft-uuid-409'
    await saveDraft({
      id: conflictDraftId,
      salespersonId,
      retailerId,
      retailerName: 'Gupta Kirana',
      items: [{ productId: 'p1', paidCases: 50 }],
    })

    globalThis.fetch = mock.fn(async () => ({
      ok: false,
      status: 409,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({
        status: 409,
        error: 'Conflict',
        message: 'Insufficient stock for SKU-PG-70.',
      }),
    }))

    await assert.rejects(async () => {
      await syncDraft(conflictDraftId)
    })

    const draftAfter = await getDraft(conflictDraftId)
    assert.ok(draftAfter) // Not deleted!
    assert.equal(draftAfter.status, DRAFT_STATUS.NEEDS_REVIEW)
    assert.ok(draftAfter.syncError.includes('Insufficient stock'))
    // Preserves original quantities
    assert.equal(draftAfter.items[0].paidCases, 50)
  })

  it('9. syncDraft handles network timeout as SYNC_UNCERTAIN without deleting draft', async () => {
    const timeoutDraftId = 'timeout-draft-uuid-999'
    await saveDraft({
      id: timeoutDraftId,
      salespersonId,
      retailerId,
      retailerName: 'Gupta Kirana',
      items: [{ productId: 'p1', paidCases: 2 }],
    })

    globalThis.fetch = mock.fn(async () => {
      throw new Error('Failed to fetch: Connection timed out')
    })

    await assert.rejects(async () => {
      await syncDraft(timeoutDraftId)
    })

    const draftAfter = await getDraft(timeoutDraftId)
    assert.ok(draftAfter)
    assert.equal(draftAfter.status, DRAFT_STATUS.SYNC_UNCERTAIN)
    assert.ok(draftAfter.syncError.includes('uncertain'))
  })

  it('10. syncAllDrafts summarizes batch sync results', async () => {
    const d1 = { id: 'batch-1', salespersonId, retailerId, status: DRAFT_STATUS.SAVED_LOCALLY, items: [] }
    const d2 = { id: 'batch-2', salespersonId, retailerId, status: DRAFT_STATUS.SAVED_LOCALLY, items: [] }
    await saveDraft(d1)
    await saveDraft(d2)

    let callCount = 0
    globalThis.fetch = mock.fn(async () => {
      callCount += 1
      if (callCount === 1) {
        return {
          ok: true,
          status: 201,
          headers: new Map([['content-type', 'application/json']]),
          json: async () => ({ id: 'o-1', orderNumber: 'ORD-1', status: 'SUBMITTED' }),
        }
      }
      return {
        ok: false,
        status: 409,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({ message: 'Price unavailable.' }),
      }
    })

    const summary = await syncAllDrafts([d1, d2])
    assert.equal(summary.total, 2)
    assert.equal(summary.succeeded, 1)
    assert.equal(summary.failed, 1)
    assert.equal(summary.errors.length, 1)
  })

  it('auto sync waits for backend readiness and keeps orders when browser is offline or backend is unreachable', async () => {
    const id = 'auto-sync-waiting-order'
    await savePendingRetailerOrder({ id, userId: salespersonId, retailerId, createdAt: '2026-11-01T00:00:00.000Z', items: [{ productId: 'p1', paidCases: 1 }] })
    let healthChecks = 0
    globalThis.fetch = mock.fn(async () => assert.fail('must not post an order while offline or backend is unreachable'))

    const offline = await autoSyncRetailerOrders([await getDraft(id)], {
      isOnline: false,
      healthCheck: async () => { healthChecks += 1; return true },
    })
    const unreachable = await autoSyncRetailerOrders([await getDraft(id)], {
      isOnline: true,
      healthCheck: async () => { healthChecks += 1; return false },
    })

    assert.equal(offline.state, 'offline')
    assert.equal(unreachable.state, 'backend-unreachable')
    assert.equal(healthChecks, 1)
    assert.equal((await getDraft(id)).status, DRAFT_STATUS.PENDING_SYNC)
  })

  it('auto sync sends eligible retailer orders oldest first and confirms only server responses', async () => {
    const olderId = 'auto-order-older'
    const newerId = 'auto-order-newer'
    const older = await savePendingRetailerOrder({ id: olderId, userId: salespersonId, retailerId, createdAt: '2026-11-01T00:00:00.000Z', items: [{ productId: 'A', position: 0, paidCases: 2 }, { productId: 'B', position: 1, paidCases: 1 }] })
    const newer = await savePendingRetailerOrder({ id: newerId, userId: salespersonId, retailerId, createdAt: '2026-11-02T00:00:00.000Z', items: [{ productId: 'C', position: 0, paidCases: 1 }] })
    assert.deepEqual(getEligibleRetailerOrders([newer, older]).map((draft) => draft.id), [olderId, newerId])
    const submittedIds = []
    const submittedItems = []
    globalThis.fetch = mock.fn(async (url, options = {}) => {
      if (!options.method || options.method === 'GET') {
        return { ok: true, status: 200, headers: new Map([['content-type', 'application/json']]), json: async () => ({ id: 'product', sku: 'SKU-OK', name: 'Current item', availableCases: 50, schemeType: null }) }
      }
      const body = JSON.parse(options.body)
      submittedIds.push(body.clientOrderId)
      submittedItems.push(body.items.map((item) => item.productId))
      return {
        ok: true,
        status: 201,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({ id: `server-${body.clientOrderId}`, orderNumber: `ORD-${body.clientOrderId}`, status: 'SUBMITTED' }),
      }
    })

    const firstRun = autoSyncRetailerOrders([newer, older], { isOnline: true, healthCheck: async () => true })
    const overlappingRun = syncAllDrafts([older, newer], { retailerOrdersOnly: true })
    const [result] = await Promise.all([firstRun, overlappingRun])

    assert.equal(result.state, 'synced')
    assert.equal(result.summary.succeeded, 2)
    assert.deepEqual(submittedIds, [olderId, newerId])
    assert.deepEqual(submittedItems[0], ['A', 'B'])
    assert.equal((await getDraft(olderId)).status, DRAFT_STATUS.SUBMITTED)
    assert.ok((await getDraft(olderId)).lastSyncedAt)
  })

  it('retains an offline order for review when refreshed stock cannot cover the saved quantity', async () => {
    const id = 'auto-order-stock-changed'
    await savePendingRetailerOrder({ id, userId: salespersonId, retailerId, items: [{ productId: 'tea', sku: 'SKU-TE-250', productName: 'Tea leaf 250 g', paidCases: 15 }] })
    globalThis.fetch = mock.fn(async () => ({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({ id: 'tea', sku: 'SKU-TE-250', name: 'Tea leaf 250 g', availableCases: 0 }),
    }))
    await assert.rejects(() => syncDraft(id), /requested 15 cases.*0 available/)
    const retained = await getDraft(id)
    assert.equal(retained.status, DRAFT_STATUS.NEEDS_REVIEW)
    assert.equal(retained.items[0].paidCases, 15)
    assert.match(retained.syncError, /SKU-TE-250/)
  })

  it('11. deleteDraft removes a draft cleanly', async () => {
    await deleteDraft(dummyDraftId)
    const check = await getDraft(dummyDraftId)
    assert.equal(check, null)
  })

  it('12. clearUserCache removes cached retailers without deleting drafts', async () => {
    const testDraft = { id: 'surviving-draft', salespersonId, items: [] }
    await saveDraft(testDraft)
    await cacheAssignedRetailers(salespersonId, [{ id: 'ret-1' }])

    await clearUserCache(salespersonId)

    const cachedRet = await getCachedAssignedRetailers(salespersonId)
    assert.equal(cachedRet, null)

    const draft = await getDraft('surviving-draft')
    assert.ok(draft)
  })
})

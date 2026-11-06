import { placeOrder } from './order.js'
import { getDraft, updateDraftStatus, DRAFT_STATUS } from './db.js'
import { ApiError, checkBackendReachable } from './api.js'
import { fetchProduct } from './catalog.js'

/**
 * Synchronizes an offline draft with the server.
 * Retains the draft's stable UUID (id) as the client_order_id to guarantee idempotency.
 * Handles credit holds, conflicts, assignment revocations, and network timeouts.
 */
const syncingDraftIds = new Set()
let syncAllPromise = null

const retryableStatuses = new Set([
  DRAFT_STATUS.SAVED_LOCALLY,
  DRAFT_STATUS.PENDING_SYNC,
  DRAFT_STATUS.WAITING_FOR_CONNECTION,
  DRAFT_STATUS.READY_TO_SYNC,
  DRAFT_STATUS.SYNC_UNCERTAIN,
  DRAFT_STATUS.SYNCING,
])

async function validateRetailerDraftStock(draft) {
  if (!draft.explicitlyPlacedOffline || !draft.retailerId) return
  const products = await Promise.all((draft.items || []).map(async (item) => {
    try { return await fetchProduct(item.productId, draft.retailerId) }
    catch (error) {
      if (error.status === 404) return null
      throw error
    }
  }))
  const issues = (draft.items || []).flatMap((item, index) => {
    const product = products[index]
    if (!product) return [`${item.sku || item.productName || item.productId}: product is unavailable in the current catalogue.`]
    const paidCases = Number(item.paidCases || 0)
    const buy = Number(product.buyQuantity || 0)
    const free = Number(product.freeQuantity || 0)
    const scheme = String(product.schemeType || '').toUpperCase()
    const freeCases = (scheme.includes('BUY_X_GET_Y') && buy > 0) ? Math.floor(paidCases / buy) * free : 0
    const requestedCases = paidCases + freeCases
    const availableCases = Number(product.availableCases || 0)
    return requestedCases > availableCases
      ? [`${product.sku || item.sku || item.productId} (${product.name || item.productName || 'product'}): requested ${requestedCases} cases including free cases, ${availableCases} available.`]
      : []
  })
  if (issues.length) {
    const error = new Error(`Stock changed while this order was offline. ${issues.join(' ')}`)
    error.stockValidation = true
    throw error
  }
}

function dispatchSyncStatus(detail) {
  if (typeof window !== 'undefined' && typeof CustomEvent !== 'undefined') {
    window.dispatchEvent(new CustomEvent('vyapaaros:sync-status', { detail }))
  }
}

export function getPendingRetailerOrders(drafts) {
  return (drafts || []).filter((draft) => draft.explicitlyPlacedOffline
    && !draft.isOfflineWorkingCart
    && draft.status !== DRAFT_STATUS.SUBMITTED
    && draft.status !== DRAFT_STATUS.CREDIT_HOLD)
}

export function getEligibleRetailerOrders(drafts) {
  return getPendingRetailerOrders(drafts)
    .filter((draft) => retryableStatuses.has(draft.status))
    .sort((left, right) => {
      const leftCreated = Date.parse(left.createdAt || '') || 0
      const rightCreated = Date.parse(right.createdAt || '') || 0
      return leftCreated - rightCreated || String(left.id).localeCompare(String(right.id))
    })
}

export async function autoSyncRetailerOrders(drafts, {
  isOnline = typeof navigator === 'undefined' ? true : navigator.onLine,
  healthCheck = checkBackendReachable,
} = {}) {
  if (!isOnline) return { state: 'offline', reachable: false, summary: null }
  const eligible = getEligibleRetailerOrders(drafts)
  if (!eligible.length) return { state: 'nothing-to-sync', reachable: null, summary: null }
  const reachable = await healthCheck()
  if (!reachable) return { state: 'backend-unreachable', reachable: false, summary: null }
  return {
    state: 'synced',
    reachable: true,
    summary: await syncAllDrafts(eligible, { retailerOrdersOnly: true }),
  }
}

async function syncDraftOnce(draftId) {
  const draft = await getDraft(draftId)
  if (!draft) {
    throw new Error(`Draft with ID ${draftId} not found.`)
  }

  if (draft.isOfflineWorkingCart) {
    throw new Error('Review this offline cart and choose Place order before synchronizing it.')
  }

  // If already confirmed, do not resubmit
  if (draft.status === DRAFT_STATUS.SUBMITTED || draft.status === DRAFT_STATUS.CREDIT_HOLD) {
    return draft
  }

  // Check network connectivity first
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    await updateDraftStatus(draft.id, DRAFT_STATUS.WAITING_FOR_CONNECTION, 'Waiting for internet connection.')
    throw new Error('Device is offline. Cannot synchronize draft right now.')
  }

  try {
    await validateRetailerDraftStock(draft)
  } catch (error) {
    await updateDraftStatus(
      draft.id,
      error.stockValidation ? DRAFT_STATUS.NEEDS_REVIEW : DRAFT_STATUS.READY_TO_SYNC,
      error.message || 'Current catalogue and stock could not be refreshed. The order remains saved; retry when the server is available.'
    )
    throw error
  }

  // Mark as in-flight
  await updateDraftStatus(draft.id, DRAFT_STATUS.SYNCING, null)

  const lineItems = (draft.items || []).map((item) => ({
    productId: item.productId,
    paidCases: Number(item.paidCases || 1),
  }))

  try {
    const serverOrder = await placeOrder(
      draft.retailerId,
      draft.id, // Preserves identical UUID across retries for backend idempotency
      draft.notes || '',
      lineItems
    )

    // The order was accepted by the server
    const isCreditHold = serverOrder.status === 'CREDIT_HOLD'
    const newStatus = isCreditHold ? DRAFT_STATUS.CREDIT_HOLD : DRAFT_STATUS.SUBMITTED

    const orderSnapshot = {
      id: serverOrder.id,
      orderNumber: serverOrder.orderNumber,
      status: serverOrder.status,
      finalAmount: serverOrder.finalAmount,
      baseAmount: serverOrder.baseAmount,
      discountAmount: serverOrder.discountAmount,
      creditHoldReason: serverOrder.creditHoldReason || null,
      createdAt: serverOrder.createdAt,
    }

    const updated = await updateDraftStatus(
      draft.id,
      newStatus,
      isCreditHold ? `Order placed on credit hold: ${serverOrder.creditHoldReason || 'Exceeds credit limit'}` : null,
      orderSnapshot
    )

    if (typeof window !== 'undefined' && typeof CustomEvent !== 'undefined') {
      window.dispatchEvent(new CustomEvent('vyapaaros:offline-order-synced', { detail: { userId: draft.userId || draft.salespersonId, retailerId: draft.retailerId, draftId: draft.id, serverOrder: orderSnapshot } }))
    }

    return updated
  } catch (error) {
    // Distinct classification of failures
    if (error instanceof ApiError) {
      if (error.status === 401) {
        // Expired auth session
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.READY_TO_SYNC,
          'Authentication expired. Please log in again to sync this order.'
        )
      } else if (error.status === 403) {
        // Revoked assignment or unauthorized
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.NEEDS_REVIEW,
          'Retailer assignment revoked or unauthorized. Cannot place order for this retailer.'
        )
      } else if (error.status === 409) {
        // Inventory, price change, scheme, or active status conflict
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.NEEDS_REVIEW,
          error.message || 'Server validation conflict. Please review items and stock before retrying.'
        )
      } else if (error.status === 400) {
        // Validation failure
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.NEEDS_REVIEW,
          error.message || 'Invalid draft data rejected by server. Please review and update.'
        )
      } else if (error.status === 0) {
        // Network timeout or connection loss from apiRequest
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.SYNC_UNCERTAIN,
          'Network error or timeout. The submission outcome is uncertain; retry will use the same idempotency key.'
        )
      } else {
        // 5xx or server-side error
        await updateDraftStatus(
          draft.id,
          DRAFT_STATUS.SYNC_UNCERTAIN,
          error.message || 'Server error encountered. Retry safely with the same draft ID.'
        )
      }
    } else {
      // Network failure, browser timeout, or offline drop
      await updateDraftStatus(
        draft.id,
        DRAFT_STATUS.SYNC_UNCERTAIN,
        'Network error or timeout. The submission outcome is uncertain; retry will use the same idempotency key.'
      )
    }

    throw error
  }
}

export async function syncDraft(draftId) {
  if (syncingDraftIds.has(draftId)) {
    throw new Error('This draft is already synchronizing. Wait for the current attempt to finish.')
  }
  syncingDraftIds.add(draftId)
  try {
    return await syncDraftOnce(draftId)
  } finally {
    syncingDraftIds.delete(draftId)
  }
}

/**
 * Synchronizes all eligible drafts for a salesperson.
 * Does not silently fail; returns summary of successes and failures.
 */
async function syncAllDraftsOnce(drafts, { retailerOrdersOnly = false } = {}) {
  const eligible = (retailerOrdersOnly ? getEligibleRetailerOrders(drafts) : (drafts || [])
    .filter((draft) => retryableStatuses.has(draft.status) && !draft.isOfflineWorkingCart))
    .filter((draft) => !syncingDraftIds.has(draft.id))

  const results = {
    total: eligible.length,
    succeeded: 0,
    failed: 0,
    errors: [],
  }

  const scope = retailerOrdersOnly ? 'retailer' : 'all'
  if (eligible.length) dispatchSyncStatus({ scope, state: 'started', total: eligible.length, current: 0 })

  for (const [index, draft] of eligible.entries()) {
    dispatchSyncStatus({ scope, state: 'syncing', total: eligible.length, current: index + 1, draftId: draft.id })
    try {
      await syncDraft(draft.id)
      results.succeeded += 1
    } catch (err) {
      results.failed += 1
      results.errors.push({
        draftId: draft.id,
        retailerName: draft.retailerName,
        message: err.message,
      })
    }
  }

  if (eligible.length) {
    dispatchSyncStatus({
      scope,
      state: results.failed ? (results.succeeded ? 'partial' : 'failed') : 'completed',
      total: eligible.length,
      succeeded: results.succeeded,
      failed: results.failed,
    })
  }

  return results
}

export function syncAllDrafts(drafts, options = {}) {
  if (syncAllPromise) return syncAllPromise
  syncAllPromise = syncAllDraftsOnce(drafts, options).finally(() => {
    syncAllPromise = null
  })
  return syncAllPromise
}

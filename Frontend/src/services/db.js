/**
 * VyapaarOS Persistent Offline Storage via IndexedDB
 * Handles offline drafts, cached retailers, and cached product catalogues.
 * In environments without IndexedDB (e.g. Node tests), seamlessly falls back to memory storage.
 */

const DB_NAME = 'vyapaaros_offline_db'
const DB_VERSION = 1

export const DRAFT_STATUS = {
  SAVED_LOCALLY: 'SAVED_LOCALLY',
  PENDING_SYNC: 'PENDING_SYNC',
  WAITING_FOR_CONNECTION: 'WAITING_FOR_CONNECTION',
  READY_TO_SYNC: 'READY_TO_SYNC',
  SYNCING: 'SYNCING',
  NEEDS_REVIEW: 'NEEDS_REVIEW',
  SYNC_UNCERTAIN: 'SYNC_UNCERTAIN',
  SUBMITTED: 'SUBMITTED',
  CREDIT_HOLD: 'CREDIT_HOLD',
}

// In-memory fallback for test environments or browsers without IndexedDB
const memoryStore = {
  drafts: new Map(),
  retailers: new Map(),
  catalogs: new Map(),
}

function hasIndexedDB() {
  return typeof window !== 'undefined' && typeof window.indexedDB !== 'undefined'
}

function openDB() {
  if (!hasIndexedDB()) return Promise.resolve(null)

  return new Promise((resolve, reject) => {
    const request = window.indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = (event) => {
      const db = event.target.result

      // Store: drafts (orders drafted locally)
      if (!db.objectStoreNames.contains('drafts')) {
        const draftStore = db.createObjectStore('drafts', { keyPath: 'id' })
        draftStore.createIndex('salespersonId', 'salespersonId', { unique: false })
        draftStore.createIndex('retailerId', 'retailerId', { unique: false })
        draftStore.createIndex('status', 'status', { unique: false })
        draftStore.createIndex('updatedAt', 'updatedAt', { unique: false })
      }

      // Store: retailers cache
      if (!db.objectStoreNames.contains('retailers_cache')) {
        db.createObjectStore('retailers_cache', { keyPath: 'id' })
      }

      // Store: catalog cache
      if (!db.objectStoreNames.contains('catalog_cache')) {
        db.createObjectStore('catalog_cache', { keyPath: 'id' })
      }
    }

    request.onsuccess = (event) => resolve(event.target.result)
    request.onerror = (event) => reject(event.target.error)
  })
}

// ==========================================
// DRAFTS API
// ==========================================

export async function saveDraft(draft) {
  if (!draft || !draft.id) {
    throw new Error('Draft must contain a valid id (UUID).')
  }

  const now = new Date().toISOString()
  const record = {
    ...draft,
    status: draft.status || DRAFT_STATUS.SAVED_LOCALLY,
    createdAt: draft.createdAt || now,
    updatedAt: now,
  }

  const db = await openDB()
  if (!db) {
    memoryStore.drafts.set(record.id, record)
    return record
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', 'readwrite')
    const store = tx.objectStore('drafts')
    const request = store.put(record)

    request.onsuccess = () => resolve(record)
    request.onerror = (e) => reject(e.target.error)
  })
}

// Persist a complete retailer order for explicit later submission. The local UUID
// is also the backend idempotency key and must remain stable across every retry.
export async function savePendingRetailerOrder(order) {
  if (!order?.id || !order.userId || !order.retailerId || !Array.isArray(order.items) || !order.items.length) {
    throw new Error('Offline order requires an owner, retailer, stable ID, and at least one item.')
  }
  return saveDraft({ ...order, status: DRAFT_STATUS.PENDING_SYNC, explicitlyPlacedOffline: true })
}

export async function getDraft(id) {
  const db = await openDB()
  if (!db) {
    return memoryStore.drafts.get(id) || null
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', 'readonly')
    const store = tx.objectStore('drafts')
    const request = store.get(id)

    request.onsuccess = () => resolve(request.result || null)
    request.onerror = (e) => reject(e.target.error)
  })
}

export async function getAllDrafts(salespersonId = null) {
  const db = await openDB()
  if (!db) {
    const all = Array.from(memoryStore.drafts.values())
    if (!salespersonId) return all.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
    return all
      .filter((d) => d.salespersonId === salespersonId)
      .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', 'readonly')
    const store = tx.objectStore('drafts')
    const request = store.getAll()

    request.onsuccess = () => {
      let results = request.result || []
      if (salespersonId) {
        results = results.filter((d) => d.salespersonId === salespersonId)
      }
      results.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
      resolve(results)
    }
    request.onerror = (e) => reject(e.target.error)
  })
}

export async function getDraftsByRetailer(retailerId, salespersonId = null) {
  const all = await getAllDrafts(salespersonId)
  return all.filter((d) => d.retailerId === retailerId)
}

export async function deleteDraft(id) {
  const db = await openDB()
  if (!db) {
    memoryStore.drafts.delete(id)
    return true
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', 'readwrite')
    const store = tx.objectStore('drafts')
    const request = store.delete(id)

    request.onsuccess = () => resolve(true)
    request.onerror = (e) => reject(e.target.error)
  })
}

export async function updateDraftStatus(id, status, syncError = null, serverOrder = null) {
  const existing = await getDraft(id)
  if (!existing) {
    throw new Error(`Draft ${id} not found to update status.`)
  }

  const updated = {
    ...existing,
    status,
    lastSyncAttemptAt: new Date().toISOString(),
    syncError: syncError !== undefined ? syncError : existing.syncError,
    serverOrder: serverOrder !== undefined ? serverOrder : existing.serverOrder,
    updatedAt: new Date().toISOString(),
  }
  if (status === DRAFT_STATUS.SUBMITTED || status === DRAFT_STATUS.CREDIT_HOLD) {
    updated.lastSyncedAt = updated.updatedAt
  }

  return saveDraft(updated)
}

// ==========================================
// RETAILERS CACHE API
// ==========================================

export async function cacheAssignedRetailers(salespersonId, retailers) {
  if (!salespersonId) return
  const record = {
    id: salespersonId,
    retailers: retailers || [],
    cachedAt: new Date().toISOString(),
  }

  const db = await openDB()
  if (!db) {
    memoryStore.retailers.set(salespersonId, record)
    return record
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('retailers_cache', 'readwrite')
    const store = tx.objectStore('retailers_cache')
    const request = store.put(record)

    request.onsuccess = () => resolve(record)
    request.onerror = (e) => reject(e.target.error)
  })
}

export async function getCachedAssignedRetailers(salespersonId) {
  if (!salespersonId) return null
  const db = await openDB()
  if (!db) {
    return memoryStore.retailers.get(salespersonId) || null
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('retailers_cache', 'readonly')
    const store = tx.objectStore('retailers_cache')
    const request = store.get(salespersonId)

    request.onsuccess = () => resolve(request.result || null)
    request.onerror = (e) => reject(e.target.error)
  })
}

// ==========================================
// CATALOG CACHE API
// ==========================================

export async function cacheCatalog(retailerId, products, categories) {
  const cacheKey = retailerId || 'global'
  const record = {
    id: cacheKey,
    retailerId,
    products: products || [],
    categories: categories || [],
    cachedAt: new Date().toISOString(),
  }

  const db = await openDB()
  if (!db) {
    memoryStore.catalogs.set(cacheKey, record)
    return record
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('catalog_cache', 'readwrite')
    const store = tx.objectStore('catalog_cache')
    const request = store.put(record)

    request.onsuccess = () => resolve(record)
    request.onerror = (e) => reject(e.target.error)
  })
}

export async function getCachedCatalog(retailerId) {
  const cacheKey = retailerId || 'global'
  const db = await openDB()
  if (!db) {
    return memoryStore.catalogs.get(cacheKey) || null
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction('catalog_cache', 'readonly')
    const store = tx.objectStore('catalog_cache')
    const request = store.get(cacheKey)

    request.onsuccess = () => resolve(request.result || null)
    request.onerror = (e) => reject(e.target.error)
  })
}

// ==========================================
// SAFE LOGOUT CLEANUP
// ==========================================

/**
 * Clears user-specific retailer and catalogue caches on logout or account switch.
 * Does NOT delete unsynchronised drafts — those belong to the user and must be
 * preserved to allow synchronisation after re-login.
 *
 * @param {string} salespersonId - The authenticated user's identity reference.
 */
export async function clearUserCache(salespersonId) {
  const db = await openDB()
  if (!db) {
    if (salespersonId) {
      memoryStore.retailers.delete(salespersonId)
      // Clear all catalog entries for this user (keyed by retailerId, not salesperson)
      // We clear all catalog entries since we cannot associate them to a specific user
      // without storing user identity in every catalog record.
      memoryStore.catalogs.clear()
    }
    return
  }

  return new Promise((resolve) => {
    try {
      const stores = ['retailers_cache', 'catalog_cache']
      const tx = db.transaction(stores, 'readwrite')
      if (salespersonId) {
        tx.objectStore('retailers_cache').delete(salespersonId)
      }
      // Clear all catalog cache entries: they may contain retailer-scoped pricing
      // accessible only to the authenticated user, and we cannot safely attribute
      // each catalog entry to a specific user without storing user identity in them.
      const catalogReq = tx.objectStore('catalog_cache').openCursor()
      catalogReq.onsuccess = (e) => {
        const cursor = e.target.result
        if (cursor) {
          cursor.delete()
          cursor.continue()
        }
      }
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
    } catch {
      resolve()
    }
  })
}

/**
 * Returns pending (unsynchronised) drafts for a given salesperson.
 * Used to warn users before logout.
 *
 * @param {string} salespersonId
 * @returns {Promise<Array>} Drafts that have not yet been confirmed by the server.
 */
export async function getPendingDrafts(salespersonId) {
  const all = await getAllDrafts(salespersonId)
  return all.filter(
    (d) =>
      d.status !== DRAFT_STATUS.SUBMITTED &&
      d.status !== DRAFT_STATUS.CREDIT_HOLD &&
      (d.userId || d.salespersonId) === salespersonId
  )
}

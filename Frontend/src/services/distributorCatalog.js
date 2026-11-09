import { apiDownload, apiRequest } from './api.js'

export function fetchManagedCategories() {
  return apiRequest('/api/distributor/me/catalog/categories')
}

export function createManagedCategory(category) {
  return apiRequest('/api/distributor/me/catalog/categories', { method: 'POST', body: category })
}

export function fetchManagedProducts({ q = '', active } = {}) {
  const params = new URLSearchParams()
  if (q.trim()) params.set('q', q.trim())
  if (active !== undefined && active !== '') params.set('active', String(active))
  const suffix = params.size ? `?${params.toString()}` : ''
  return apiRequest(`/api/distributor/me/catalog/products${suffix}`)
}

export function createManagedProduct(product) {
  return apiRequest('/api/distributor/me/catalog/products', { method: 'POST', body: product })
}

export function updateManagedProduct(id, product) {
  return apiRequest(`/api/distributor/me/catalog/products/${encodeURIComponent(id)}`, { method: 'PUT', body: product })
}

export function setManagedProductActive(id, active) {
  return apiRequest(`/api/distributor/me/catalog/products/${encodeURIComponent(id)}/active`, { method: 'PATCH', body: { active } })
}

export function fetchDistributorInventory({ q = '', lowStock = false, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q.trim()) params.set('q', q.trim())
  if (lowStock) params.set('lowStock', 'true')
  return apiRequest(`/api/distributor/me/inventory?${params}`)
}

export function fetchDistributorInventoryProduct(productId) {
  return apiRequest(`/api/distributor/me/inventory/products/${encodeURIComponent(productId)}`)
}

export function adjustDistributorInventory(productId, quantityDelta, reason) {
  return apiRequest(`/api/distributor/me/inventory/products/${encodeURIComponent(productId)}/adjustments`, {
    method: 'POST', body: { quantityDelta: Number(quantityDelta), reason },
  })
}

export function updateDistributorInventoryThreshold(productId, lowStockThreshold) {
  return apiRequest(`/api/distributor/me/inventory/products/${encodeURIComponent(productId)}/threshold`, {
    method: 'PUT', body: { lowStockThreshold: Number(lowStockThreshold) },
  })
}

export function fetchInventoryMovements({ productId, type, from, until, orderReference, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (productId) params.set('productId', productId)
  if (type) params.set('type', type)
  if (from) params.set('from', from)
  if (until) params.set('until', until)
  if (orderReference?.trim()) params.set('orderReference', orderReference.trim())
  return apiRequest(`/api/distributor/me/inventory/movements?${params}`)
}

export function fetchInventoryReconciliation(productId) {
  return apiRequest(`/api/distributor/me/inventory/products/${encodeURIComponent(productId)}/reconciliation`)
}

export function fetchDistributorReports({ fromDate, toDate, topProductMetric = 'quantity' } = {}) {
  const params = new URLSearchParams({ topProductMetric })
  if (fromDate) params.set('fromDate', fromDate)
  if (toDate) params.set('toDate', toDate)
  return apiRequest(`/api/distributor/me/reports?${params}`)
}

export function exportDistributorReports({ fromDate, toDate, topProductMetric = 'quantity', report = 'combined' } = {}) {
  const params = new URLSearchParams({ topProductMetric, report })
  if (fromDate) params.set('fromDate', fromDate)
  if (toDate) params.set('toDate', toDate)
  return apiDownload(`/api/distributor/me/reports/export?${params}`)
}

export function triggerCsvDownload(download, { documentRef = globalThis.document, urlApi = globalThis.URL, windowRef = globalThis.window } = {}) {
  if (!download?.blob) throw new Error('The CSV download is incomplete.')
  const filename = download?.filename || 'distributor-report.csv'
  const objectUrl = urlApi.createObjectURL(download.blob)
  const link = documentRef.createElement('a')
  link.href = objectUrl
  link.download = filename
  documentRef.body.append(link)
  link.click()
  link.remove()
  windowRef.setTimeout(() => urlApi.revokeObjectURL(objectUrl), 1000)
}

export function validateReportDateRange(fromDate, toDate, today = new Date().toISOString().slice(0, 10)) {
  if (!fromDate || !toDate || fromDate > toDate || toDate > today) return false
  const start = new Date(`${fromDate}T00:00:00Z`)
  const end = new Date(`${toDate}T00:00:00Z`)
  const days = Math.floor((end - start) / 86400000) + 1
  return Number.isInteger(days) && days <= 366
}

export function validateLowStockThreshold(value) {
  return Number.isInteger(Number(value)) && Number(value) >= 0
}

export function validateStockAdjustment(quantityDelta, reason) {
  const delta = Number(quantityDelta)
  return Number.isInteger(delta) && delta !== 0 && String(reason || '').trim().length > 0
}

export function validateProductForm(product) {
  return Boolean(String(product?.sku || '').trim()
    && String(product?.name || '').trim()
    && String(product?.categoryId || '').trim()
    && String(product?.unitName || '').trim()
    && Number.isFinite(Number(product?.unitQuantity)) && Number(product.unitQuantity) > 0
    && Number.isInteger(Number(product?.unitsPerCase)) && Number(product.unitsPerCase) > 0)
}

export function fetchManagedPrices() {
  return apiRequest('/api/pricing/me/defaults')
}

export function upsertManagedPrice(productId, price) {
  return apiRequest(`/api/pricing/products/${encodeURIComponent(productId)}/default`, { method: 'PUT', body: price })
}

export function deactivateManagedPrice(productId) {
  return apiRequest(`/api/pricing/products/${encodeURIComponent(productId)}/default`, { method: 'DELETE' })
}

export function validatePriceForm(price) {
  const amount = Number(price?.pricePerCase)
  return Number.isFinite(amount) && amount >= 0 && /^\d{1,10}(\.\d{1,2})?$/.test(String(price?.pricePerCase ?? '').trim())
}

export function fetchManagedSchemes({ q = '', active } = {}) {
  const params = new URLSearchParams()
  if (q.trim()) params.set('q', q.trim())
  if (active !== undefined && active !== '') params.set('active', String(active))
  const suffix = params.size ? `?${params.toString()}` : ''
  return apiRequest(`/api/schemes/me${suffix}`)
}

export function createManagedScheme(scheme) { return apiRequest('/api/schemes', { method: 'POST', body: scheme }) }
export function updateManagedScheme(id, scheme) { return apiRequest(`/api/schemes/${encodeURIComponent(id)}`, { method: 'PUT', body: scheme }) }
export function setManagedSchemeActive(id, active) { return apiRequest(`/api/schemes/${encodeURIComponent(id)}/active`, { method: 'PATCH', body: { active } }) }
export function fetchRetailerSchemes(retailerId) { return apiRequest(`/api/schemes/retailer/me?retailerId=${encodeURIComponent(retailerId)}`) }

export function validateSchemeForm(scheme) {
  if (!String(scheme?.name || '').trim() || !['BUY_X_GET_Y_FREE', 'SLAB_DISCOUNT'].includes(scheme?.schemeType)) return false
  const from = scheme?.startsAt ? Date.parse(scheme.startsAt) : null
  const until = scheme?.endsAt ? Date.parse(scheme.endsAt) : null
  if ((from != null && Number.isNaN(from)) || (until != null && Number.isNaN(until)) || (from != null && until != null && until <= from)) return false
  if (scheme.schemeType === 'BUY_X_GET_Y_FREE') return Number.isInteger(Number(scheme.buyQuantity)) && Number(scheme.buyQuantity) > 0 && Number.isInteger(Number(scheme.freeQuantity)) && Number(scheme.freeQuantity) > 0
  return Number.isInteger(Number(scheme.minimumQuantity)) && Number(scheme.minimumQuantity) > 0 && Number(scheme.discountPercentage) > 0 && Number(scheme.discountPercentage) <= 100
}

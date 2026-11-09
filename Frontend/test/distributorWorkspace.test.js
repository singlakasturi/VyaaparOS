import test, { beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { addDistributorStaff, canManageDistributorStaff, fetchCurrentDistributor, fetchDistributorStaff, getDistributorStaffListState, getWorkspaceAvailability, revokeDistributorStaff, updateCurrentDistributor, validateDistributorBusinessName } from '../src/services/distributor.js'
import { createRelationshipRequest, fetchDistributorRequests, relationshipViewState } from '../src/services/relationships.js'
import { adjustDistributorInventory, createManagedProduct, fetchDistributorInventory, fetchInventoryMovements, fetchInventoryReconciliation, fetchDistributorReports, exportDistributorReports, triggerCsvDownload, validateReportDateRange, fetchManagedProducts, setManagedProductActive, updateDistributorInventoryThreshold, validateLowStockThreshold, validateProductForm, validateStockAdjustment } from '../src/services/distributorCatalog.js'

beforeEach(() => {
  mock.reset()
  globalThis.window = {
    sessionStorage: {
      values: new Map(),
      getItem(key) { return this.values.get(key) || null },
      setItem(key, value) { this.values.set(key, String(value)) },
      removeItem(key) { this.values.delete(key) },
    },
    dispatchEvent: () => {},
  }
  globalThis.fetch = mock.fn(async () => ({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ id: 'business-1', businessName: 'North Distribution', canManageStaff: true }),
  }))
})

test('staff management capability is shown only when backend grants owner capability', () => {
  assert.equal(canManageDistributorStaff({ canManageStaff: true }), true)
  assert.equal(canManageDistributorStaff({ canManageStaff: false }), false)
  assert.equal(canManageDistributorStaff({ owner: true }), false)
  assert.equal(canManageDistributorStaff(null), false)
})

test('business name validation rejects blanks and values longer than the supported field', () => {
  assert.equal(validateDistributorBusinessName(' North Distribution '), true)
  assert.equal(validateDistributorBusinessName('   '), false)
  assert.equal(validateDistributorBusinessName('N'.repeat(201)), false)
})

test('workspace availability supports distributor, retailer, combined, and empty accounts', () => {
  assert.deepEqual(getWorkspaceAvailability({ distributor: { id: 'd' }, retailers: [] }), { distributor: true, retailer: false, empty: false })
  assert.deepEqual(getWorkspaceAvailability({ distributor: null, retailers: [{ id: 'r' }] }), { distributor: false, retailer: true, empty: false })
  assert.deepEqual(getWorkspaceAvailability({ distributor: { id: 'd' }, retailers: [{ id: 'r' }] }), { distributor: true, retailer: true, empty: false })
  assert.deepEqual(getWorkspaceAvailability({ distributor: null, retailers: [] }), { distributor: false, retailer: false, empty: true })
})

test('staff UI state covers loading, error, empty, and populated success states', () => {
  assert.equal(getDistributorStaffListState({ loading: true, error: '', staff: [] }), 'loading')
  assert.equal(getDistributorStaffListState({ loading: false, error: 'offline', staff: [] }), 'error')
  assert.equal(getDistributorStaffListState({ loading: false, error: '', staff: [] }), 'empty')
  assert.equal(getDistributorStaffListState({ loading: false, error: '', staff: [{ userId: 'u1' }] }), 'success')
})

test('business details use server-resolved current business endpoint', async () => {
  const result = await fetchCurrentDistributor()
  assert.equal(result.businessName, 'North Distribution')
  assert.equal(globalThis.fetch.mock.calls[0].arguments[0], 'http://localhost:8080/api/distributor/me')
})

test('business update sends only editable details', async () => {
  await updateCurrentDistributor({ businessName: 'Updated Distribution' })
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  assert.equal(url, 'http://localhost:8080/api/distributor/me')
  assert.equal(options.method, 'PUT')
  assert.deepEqual(JSON.parse(options.body), { businessName: 'Updated Distribution' })
})

test('staff listing, add, and revoke call the corresponding authenticated APIs', async () => {
  await fetchDistributorStaff()
  await addDistributorStaff('staff@example.com')
  await revokeDistributorStaff('user-1')
  assert.deepEqual(globalThis.fetch.mock.calls.map((call) => [call.arguments[0], call.arguments[1].method]), [
    ['http://localhost:8080/api/distributor/me/staff', 'GET'],
    ['http://localhost:8080/api/distributor/me/staff', 'POST'],
    ['http://localhost:8080/api/distributor/me/staff/user-1', 'DELETE'],
  ])
})

test('backend denial is surfaced for revoked workspace access', async () => {
  globalThis.fetch = mock.fn(async () => ({
    ok: false,
    status: 403,
    headers: { get: () => 'application/json' },
    json: async () => ({ message: 'Access denied.' }),
  }))
  await assert.rejects(fetchCurrentDistributor(), { status: 403, message: 'Access denied.' })
})

test('retailer relationship state covers loading, error, unlinked, pending, and active states', () => {
  assert.equal(relationshipViewState({ loading: true }), 'loading')
  assert.equal(relationshipViewState({ loading: false, error: 'offline' }), 'error')
  assert.equal(relationshipViewState({ loading: false, requests: [] }), 'unlinked')
  assert.equal(relationshipViewState({ loading: false, requests: [{ status: 'PENDING' }] }), 'pending')
  assert.equal(relationshipViewState({ loading: false, activeRelationship: { id: 'd1' }, requests: [] }), 'active')
})

test('relationship request submits retailer selected in authenticated workspace and scoped destination', async () => {
  await createRelationshipRequest({ retailerId: 'retailer-1', distributorId: 'distributor-2', type: 'SWITCH' })
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  assert.equal(url, 'http://localhost:8080/api/retailer-relationships/requests')
  assert.equal(options.method, 'POST')
  assert.deepEqual(JSON.parse(options.body), { retailerId: 'retailer-1', distributorId: 'distributor-2', type: 'SWITCH' })
})

test('distributor request list is fetched from server-resolved own business endpoint', async () => {
  await fetchDistributorRequests()
  assert.equal(globalThis.fetch.mock.calls[0].arguments[0], 'http://localhost:8080/api/distributor/me/retailer-requests')
})

test('catalog search and product lifecycle use current distributor endpoints', async () => {
  await fetchManagedProducts({ q: 'Tea 250', active: false })
  await createManagedProduct({ sku: 'T-250', name: 'Tea', categoryId: 'cat-1' })
  await setManagedProductActive('prod-1', false)
  assert.deepEqual(globalThis.fetch.mock.calls.map(call => [call.arguments[0], call.arguments[1]?.method || 'GET']), [
    ['http://localhost:8080/api/distributor/me/catalog/products?q=Tea+250&active=false', 'GET'],
    ['http://localhost:8080/api/distributor/me/catalog/products', 'POST'],
    ['http://localhost:8080/api/distributor/me/catalog/products/prod-1/active', 'PATCH'],
  ])
})

test('inventory adjustments require a non-zero integer and a reason', async () => {
  assert.equal(validateStockAdjustment(4, 'Counted cases'), true)
  assert.equal(validateStockAdjustment(-2, 'Damaged cases'), true)
  assert.equal(validateStockAdjustment(0, 'Correction'), false)
  assert.equal(validateStockAdjustment(1.5, 'Correction'), false)
  assert.equal(validateStockAdjustment(2, '   '), false)
  assert.equal(validateLowStockThreshold(0), true)
  assert.equal(validateLowStockThreshold(2), true)
  assert.equal(validateLowStockThreshold(-1), false)
  assert.equal(validateLowStockThreshold(1.5), false)
  await fetchDistributorInventory({ q: 'tea', lowStock: true, page: 1, size: 10 })
  await adjustDistributorInventory('prod-1', -2, 'Damaged cases')
  await updateDistributorInventoryThreshold('prod-1', 8)
  const [listUrl] = globalThis.fetch.mock.calls[0].arguments
  const params = new URL(listUrl).searchParams
  assert.equal(params.get('q'), 'tea')
  assert.equal(params.get('lowStock'), 'true')
  assert.equal(params.get('page'), '1')
  const [url, options] = globalThis.fetch.mock.calls[1].arguments
  assert.equal(url, 'http://localhost:8080/api/distributor/me/inventory/products/prod-1/adjustments')
  assert.equal(options.method, 'POST')
  assert.deepEqual(JSON.parse(options.body), { quantityDelta: -2, reason: 'Damaged cases' })
  const [thresholdUrl, thresholdOptions] = globalThis.fetch.mock.calls[2].arguments
  assert.equal(thresholdUrl, 'http://localhost:8080/api/distributor/me/inventory/products/prod-1/threshold')
  assert.equal(thresholdOptions.method, 'PUT')
  assert.deepEqual(JSON.parse(thresholdOptions.body), { lowStockThreshold: 8 })
})

test('inventory movement history and reconciliation use authenticated scoped endpoints', async () => {
  globalThis.window.sessionStorage.setItem('vyapaaros.accessToken', 'test-token')
  await fetchInventoryMovements({ productId: 'prod-1', type: 'DELIVERY_CONSUMPTION', from: '2026-01-01T00:00:00Z', until: '2026-02-01T00:00:00Z', orderReference: 'ORD-8', page: 2, size: 10 })
  await fetchInventoryReconciliation('prod-1')
  const [historyUrl, historyOptions] = globalThis.fetch.mock.calls[0].arguments
  const history = new URL(historyUrl)
  assert.equal(history.pathname, '/api/distributor/me/inventory/movements')
  assert.equal(history.searchParams.get('productId'), 'prod-1')
  assert.equal(history.searchParams.get('type'), 'DELIVERY_CONSUMPTION')
  assert.equal(history.searchParams.get('from'), '2026-01-01T00:00:00Z')
  assert.equal(history.searchParams.get('until'), '2026-02-01T00:00:00Z')
  assert.equal(history.searchParams.get('orderReference'), 'ORD-8')
  assert.equal(history.searchParams.get('page'), '2')
  assert.equal(historyOptions.headers.Authorization, 'Bearer test-token')
  assert.equal(globalThis.fetch.mock.calls[1].arguments[0], 'http://localhost:8080/api/distributor/me/inventory/products/prod-1/reconciliation')
})

test('distributor reports use authenticated date and ranking parameters and validate the maximum range', async () => {
  globalThis.window.sessionStorage.setItem('vyapaaros.accessToken', 'test-token')
  await fetchDistributorReports({ fromDate: '2026-01-01', toDate: '2026-01-31', topProductMetric: 'value' })
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  const request = new URL(url)
  assert.equal(request.pathname, '/api/distributor/me/reports')
  assert.equal(request.searchParams.get('fromDate'), '2026-01-01')
  assert.equal(request.searchParams.get('toDate'), '2026-01-31')
  assert.equal(request.searchParams.get('topProductMetric'), 'value')
  assert.equal(options.headers.Authorization, 'Bearer test-token')
  assert.equal(validateReportDateRange('2024-01-01', '2024-12-31', '2026-01-01'), true)
  assert.equal(validateReportDateRange('2024-01-01', '2025-01-01', '2026-01-01'), false)
  assert.equal(validateReportDateRange('2025-01-01', '2026-01-02', '2026-01-02'), false)
  assert.equal(validateReportDateRange('2026-02-02', '2026-02-01', '2026-02-03'), false)
  assert.equal(validateReportDateRange('2026-01-01', '2026-01-02', '2026-01-01'), false)
})

test('report export uses the authenticated download API with the current report filters and server filename', async () => {
  globalThis.window.sessionStorage.setItem('vyapaaros.accessToken', 'report-token')
  globalThis.fetch = mock.fn(async () => ({
    ok: true, status: 200,
    headers: { get: name => name === 'content-disposition' ? 'attachment; filename="vyapaaros-report-top_products-2026-01-01-to-2026-01-31.csv"' : 'text/csv;charset=UTF-8' },
    blob: async () => new Blob(['name\r\nTea\r\n'], { type: 'text/csv' }),
  }))
  const download = await exportDistributorReports({ fromDate: '2026-01-01', toDate: '2026-01-31', topProductMetric: 'value', report: 'top_products' })
  const [url, options] = globalThis.fetch.mock.calls[0].arguments
  const request = new URL(url)
  assert.equal(request.pathname, '/api/distributor/me/reports/export')
  assert.equal(request.searchParams.get('fromDate'), '2026-01-01')
  assert.equal(request.searchParams.get('toDate'), '2026-01-31')
  assert.equal(request.searchParams.get('topProductMetric'), 'value')
  assert.equal(request.searchParams.get('report'), 'top_products')
  assert.equal(options.headers.Authorization, 'Bearer report-token')
  assert.equal(download.filename, 'vyapaaros-report-top_products-2026-01-01-to-2026-01-31.csv')
  assert.equal(download.blob.type, 'text/csv')
})

test('CSV download creates and clicks a temporary download link and reports API errors', async () => {
  const events = []
  const link = { click: () => events.push('click'), remove: () => events.push('remove') }
  const documentRef = { body: { append: () => events.push('append') }, createElement: tag => { assert.equal(tag, 'a'); return link } }
  const urlApi = { createObjectURL: blob => { assert.equal(blob.size, 3); events.push('create'); return 'blob:report' }, revokeObjectURL: value => { assert.equal(value, 'blob:report'); events.push('revoke') } }
  triggerCsvDownload({ blob: new Blob(['csv']), filename: 'report.csv' }, { documentRef, urlApi, windowRef: { setTimeout: callback => callback() } })
  assert.deepEqual(events, ['create', 'append', 'click', 'remove', 'revoke'])

  globalThis.fetch = mock.fn(async () => ({ ok: false, status: 400, headers: { get: () => 'application/json' }, json: async () => ({ message: 'Date range is invalid.' }) }))
  await assert.rejects(exportDistributorReports({ report: 'combined' }), /Date range is invalid/)
})

test('product form rejects incomplete or invalid package details before save', () => {
  const valid = { sku: 'TEA-250', name: 'Tea', categoryId: 'cat-1', unitName: 'g', unitQuantity: '250', unitsPerCase: '40' }
  assert.equal(validateProductForm(valid), true)
  assert.equal(validateProductForm({ ...valid, sku: ' ' }), false)
  assert.equal(validateProductForm({ ...valid, categoryId: '' }), false)
  assert.equal(validateProductForm({ ...valid, unitQuantity: '0' }), false)
  assert.equal(validateProductForm({ ...valid, unitsPerCase: '1.5' }), false)
})

test('scheme helpers validate supported benefits and call scoped management endpoints', async () => {
  const { fetchManagedSchemes, createManagedScheme, validateSchemeForm } = await import('../src/services/distributorCatalog.js')
  assert.equal(validateSchemeForm({ name: 'Offer', schemeType: 'BUY_X_GET_Y_FREE', buyQuantity: 10, freeQuantity: 1 }), true)
  assert.equal(validateSchemeForm({ name: 'Offer', schemeType: 'SLAB_DISCOUNT', minimumQuantity: 3, discountPercentage: 100 }), true)
  assert.equal(validateSchemeForm({ name: 'Offer', schemeType: 'SLAB_DISCOUNT', minimumQuantity: 3, discountPercentage: 101 }), false)
  assert.equal(validateSchemeForm({ name: 'Offer', schemeType: 'BUY_X_GET_Y_FREE', buyQuantity: 10, freeQuantity: 1, startsAt: '2026-02-02T00:00', endsAt: '2026-02-01T00:00' }), false)
  globalThis.fetch = mock.fn(async () => ({ ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => [] }))
  await fetchManagedSchemes({ q: 'tea', active: true })
  await createManagedScheme({ name: 'Offer' })
  assert.equal(globalThis.fetch.mock.calls[0].arguments[0], 'http://localhost:8080/api/schemes/me?q=tea&active=true')
  assert.equal(globalThis.fetch.mock.calls[1].arguments[0], 'http://localhost:8080/api/schemes')
})



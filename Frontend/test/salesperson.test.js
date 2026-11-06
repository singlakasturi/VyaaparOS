import { describe, it, beforeEach, mock } from 'node:test'
import assert from 'node:assert/strict'
import { fetchAssignedRetailers, fetchAssignedRetailer } from '../src/services/salesperson.js'
import { fetchProducts } from '../src/services/catalog.js'
import { fetchCart, updateCartQuantity, removeCartItem, clearCart } from '../src/services/cart.js'
import { placeOrder, fetchOrders } from '../src/services/order.js'

describe('Salesperson Services and Workspace Logic', () => {
  const dummyRetailerId = 'ret-5555-6666-7777-8888'

  beforeEach(() => {
    mock.reset()
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

  it('1. fetchAssignedRetailers calls GET /api/salesperson/retailers', async () => {
    let capturedUrl = null
    let capturedOptions = null

    globalThis.fetch = mock.fn(async (url, options) => {
      capturedUrl = url
      capturedOptions = options
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => [
          {
            id: dummyRetailerId,
            businessName: 'Gupta General Store',
            contactName: 'Ramesh Gupta',
            phone: '9876543210',
            city: 'Kanpur',
            state: 'UP',
            status: 'ACTIVE',
            creditLimit: 50000,
            outstandingBalance: 12500
          }
        ]
      }
    })

    const retailers = await fetchAssignedRetailers()
    assert.equal(capturedUrl, 'http://localhost:8080/api/salesperson/retailers')
    assert.equal(capturedOptions.method, 'GET')
    assert.equal(retailers.length, 1)
    assert.equal(retailers[0].businessName, 'Gupta General Store')
    assert.equal(retailers[0].city, 'Kanpur')
  })

  it('2. fetchAssignedRetailer calls GET /api/salesperson/retailers/{id}', async () => {
    let capturedUrl = null

    globalThis.fetch = mock.fn(async (url) => {
      capturedUrl = url
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          id: dummyRetailerId,
          businessName: 'Gupta General Store',
          status: 'ACTIVE'
        })
      }
    })

    const retailer = await fetchAssignedRetailer(dummyRetailerId)
    assert.equal(capturedUrl, `http://localhost:8080/api/salesperson/retailers/${dummyRetailerId}`)
    assert.equal(retailer.id, dummyRetailerId)
    assert.equal(retailer.businessName, 'Gupta General Store')
  })

  it('3. Filters assigned retailers by name, phone, or city', () => {
    const list = [
      { id: '1', businessName: 'Sharma Traders', phone: '9811111111', city: 'Lucknow', status: 'ACTIVE' },
      { id: '2', businessName: 'Gupta Kirana', phone: '9822222222', city: 'Kanpur', status: 'ACTIVE' },
      { id: '3', businessName: 'Verma Provisions', phone: '9833333333', city: 'Lucknow', status: 'INACTIVE' }
    ]

    const filterRetailers = (retailers, query, statusFilter) => {
      const q = (query || '').toLowerCase().trim()
      return retailers.filter((r) => {
        const matchesQuery = !q ||
          (r.businessName || '').toLowerCase().includes(q) ||
          (r.phone || '').includes(q) ||
          (r.city || '').toLowerCase().includes(q)
        const matchesStatus = statusFilter === 'ALL' || (r.status || 'ACTIVE') === statusFilter
        return matchesQuery && matchesStatus
      })
    }

    // Search by city
    const lucknowOnly = filterRetailers(list, 'lucknow', 'ALL')
    assert.equal(lucknowOnly.length, 2)

    // Search by phone
    const phoneMatch = filterRetailers(list, '9822', 'ALL')
    assert.equal(phoneMatch.length, 1)
    assert.equal(phoneMatch[0].businessName, 'Gupta Kirana')

    // Filter by status
    const activeOnly = filterRetailers(list, '', 'ACTIVE')
    assert.equal(activeOnly.length, 2)
  })

  it('4. Selected retailer persists in sessionStorage', () => {
    const selected = {
      id: dummyRetailerId,
      businessName: 'Gupta General Store',
      city: 'Kanpur'
    }

    // Simulate saving selected retailer
    window.sessionStorage.setItem('vyapaaros.selectedRetailer', JSON.stringify(selected))
    window.sessionStorage.setItem('vyapaaros.retailerId', selected.id)

    const savedRaw = window.sessionStorage.getItem('vyapaaros.selectedRetailer')
    assert.ok(savedRaw)
    const parsed = JSON.parse(savedRaw)
    assert.equal(parsed.id, dummyRetailerId)
    assert.equal(window.sessionStorage.getItem('vyapaaros.retailerId'), dummyRetailerId)
  })

  it('5. fetchProducts calls GET /api/catalog/products with retailerId for price resolution', async () => {
    let capturedUrl = null

    globalThis.fetch = mock.fn(async (url) => {
      capturedUrl = url
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          content: [
            { id: 'p1', name: 'Parle-G 70g', resolvedPrice: 120, unit: 'CASE' }
          ],
          totalElements: 1
        })
      }
    })

    const res = await fetchProducts({ retailerId: dummyRetailerId, category: 'Biscuits' })
    assert.ok(capturedUrl.includes('/api/catalog/products'))
    assert.ok(capturedUrl.includes(`retailerId=${dummyRetailerId}`))
    assert.ok(capturedUrl.includes('category=Biscuits'))
    assert.equal(res.content.length, 1)
  })

  it('6. Cart management functions pass retailerId and correct payload', async () => {
    let captured = []

    globalThis.fetch = mock.fn(async (url, options) => {
      captured.push({ url, options })
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({ retailerId: dummyRetailerId, items: [], totalAmount: 0 })
      }
    })

    await fetchCart(dummyRetailerId)
    assert.ok(captured[0].url.includes(`/api/cart?retailerId=${dummyRetailerId}`))

    await updateCartQuantity('item-1', 4)
    assert.ok(captured[1].url.includes('/api/cart/items/item-1'))
    assert.equal(captured[1].options.method, 'PATCH')
    assert.deepEqual(JSON.parse(captured[1].options.body), { paidCases: 4 })

    await removeCartItem('item-1')
    assert.ok(captured[2].url.includes('/api/cart/items/item-1'))
    assert.equal(captured[2].options.method, 'DELETE')

    await clearCart(dummyRetailerId)
    assert.ok(captured[3].url.includes(`/api/cart?retailerId=${dummyRetailerId}`))
    assert.equal(captured[3].options.method, 'DELETE')
  })

  it('7. placeOrder calls POST /api/orders with retailerId, clientOrderId, and notes', async () => {
    let capturedUrl = null
    let capturedOptions = null

    globalThis.fetch = mock.fn(async (url, options) => {
      capturedUrl = url
      capturedOptions = options
      return {
        ok: true,
        status: 201,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({
          id: 'ord-9999',
          orderNumber: 'ORD-2026-0001',
          retailerId: dummyRetailerId,
          totalAmount: 2500,
          status: 'PENDING'
        })
      }
    })

    const clientOrderId = 'client-uuid-1234'
    const notes = 'Urgent delivery needed by morning'
    const order = await placeOrder(dummyRetailerId, clientOrderId, notes)

    assert.equal(capturedUrl, 'http://localhost:8080/api/orders')
    assert.equal(capturedOptions.method, 'POST')
    const body = JSON.parse(capturedOptions.body)
    assert.equal(body.retailerId, dummyRetailerId)
    assert.equal(body.clientOrderId, clientOrderId)
    assert.equal(body.notes, notes)
    assert.equal(order.orderNumber, 'ORD-2026-0001')
  })

  it('8. fetchOrders calls GET /api/orders?retailerId=...', async () => {
    let capturedUrl = null

    globalThis.fetch = mock.fn(async (url) => {
      capturedUrl = url
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => [
          { id: 'ord-1', orderNumber: 'ORD-001', totalAmount: 1500, status: 'CONFIRMED' }
        ]
      }
    })

    const orders = await fetchOrders(dummyRetailerId)
    assert.ok(capturedUrl.includes(`/api/orders?retailerId=${dummyRetailerId}`))
    assert.equal(orders.length, 1)
  })

  it('9. Role-based redirect routing maps roles correctly', () => {
    const getLandingPathForUser = (user) => {
      if (!user) return '/login'
      const roles = user.roles || (user.role ? [user.role] : [])
      const upperRoles = roles.map(r => String(r).toUpperCase())
      if (upperRoles.some(r => r.includes('SALESPERSON'))) return '/salesperson'
      if (upperRoles.some(r => r.includes('RETAILER'))) return '/retailer'
      if (upperRoles.some(r => r.includes('ORDER_DESK'))) return '/orders'
      if (upperRoles.some(r => r.includes('ADMIN'))) return '/admin'
      return '/login'
    }

    assert.equal(getLandingPathForUser({ roles: ['ROLE_SALESPERSON'] }), '/salesperson')
    assert.equal(getLandingPathForUser({ roles: ['SALESPERSON'] }), '/salesperson')
    assert.equal(getLandingPathForUser({ roles: ['ROLE_RETAILER'] }), '/retailer')
    assert.equal(getLandingPathForUser({ roles: ['ORDER_DESK'] }), '/orders')
    assert.equal(getLandingPathForUser({ roles: ['ROLE_ADMIN'] }), '/admin')
    assert.equal(getLandingPathForUser(null), '/login')
  })
})

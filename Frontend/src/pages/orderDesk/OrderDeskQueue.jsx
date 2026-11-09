import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, ArrowRight, CalendarDays, ChevronLeft, ChevronRight, RefreshCw, Search } from 'lucide-react'
import { fetchDeskOrders, fetchDeskOverview } from '../../services/orderDesk.js'

const STATUSES = ['SUBMITTED', 'CREDIT_HOLD', 'APPROVED', 'REJECTED', 'TALLY_EXPORTED', 'CANCELLED']
const EMPTY_ORDERS = []
const statusLabel = (status) => String(status || 'UNKNOWN').replaceAll('_', ' ')
const dateLabel = (value) => value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
const money = (amount, currency) => amount == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(Number(amount))

function dateBounds(from, to) {
  const startDate = from ? new Date(`${from}T00:00:00`).toISOString() : undefined
  const endDate = to ? new Date(new Date(`${to}T00:00:00`).getTime() + 86400000).toISOString() : undefined
  return { startDate, endDate }
}

export function OrderStatusBadge({ status }) {
  return <span className={`desk-status desk-status-${String(status || 'unknown').toLowerCase().replaceAll('_', '-')}`}>{statusLabel(status)}</span>
}

export default function OrderDeskQueue() {
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [result, setResult] = useState(null)
  const [overview, setOverview] = useState(null)
  const [overviewLoading, setOverviewLoading] = useState(true)
  const [overviewError, setOverviewError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    if (from && to && from > to) { setError('Start date must be on or before end date.'); setLoading(false); return }
    try {
      const range = dateBounds(from, to)
      setResult(await fetchDeskOrders({ status: status || undefined, q: query.trim() || undefined, ...range, page, size }))
    } catch (err) { setError(err.message || 'The order queue could not be loaded.') }
    finally { setLoading(false) }
  }, [status, from, to, page, size, query])

  const loadOverview = useCallback(async () => {
    setOverviewLoading(true); setOverviewError('')
    try { setOverview(await fetchDeskOverview()) }
    catch (err) { setOverviewError(err.message || 'Order summary could not be loaded.') }
    finally { setOverviewLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])
  useEffect(() => { loadOverview() }, [loadOverview])

  const orders = result?.content || EMPTY_ORDERS
  const visibleOrders = orders
  const totalPages = result?.totalPages || 0
  const totalElements = result?.totalElements || 0

  const updateFilter = (setter) => (event) => { setter(event.target.value); setPage(0) }

  return (
    <div className="desk-page">
      <header className="desk-page-header">
        <div><span className="desk-eyebrow">DISTRIBUTOR OPERATIONS</span><h1>Order queue</h1><p>Review incoming orders, manage credit holds, and record approval decisions.</p></div>
        <div className="desk-page-header-actions"><Link className="desk-button desk-button-secondary" to="/order-desk/exports">Tally exports</Link><button className="desk-button desk-button-secondary" type="button" onClick={() => { load(); loadOverview() }} disabled={loading}><RefreshCw size={16} className={loading ? 'desk-spin' : ''} />Refresh queue</button></div>
      </header>

      <section className="desk-operational-overview" aria-label="Order overview">
        <div className="desk-overview-heading"><div><span className="desk-eyebrow">OPERATIONAL OVERVIEW</span><h2>Orders at a glance</h2></div>{overview && <strong>{overview.totalOrders.toLocaleString('en-IN')} total</strong>}</div>
        {overviewLoading && !overview ? <div className="desk-overview-loading"><RefreshCw size={17} className="desk-spin" />Loading summary…</div>
          : overviewError ? <div className="desk-overview-error" role="alert">{overviewError}<button type="button" className="desk-text-button" onClick={loadOverview}>Retry</button></div>
            : overview && <><div className="desk-status-counts">{STATUSES.map((value) => <button type="button" className={`desk-status-count ${status === value ? 'desk-status-count-selected' : ''}`} key={value} onClick={() => { setStatus((current) => current === value ? '' : value); setPage(0) }}><span>{statusLabel(value)}</span><strong>{Number(overview.statusCounts?.[value] || 0).toLocaleString('en-IN')}</strong></button>)}</div>
              <div className="desk-recent-orders"><h3>Recent orders</h3>{overview.recentOrders?.length ? <div>{overview.recentOrders.map((order) => <Link className="desk-recent-order" key={order.id} to={`/order-desk/orders/${order.id}`}><span><strong>{order.orderNumber || order.id}</strong><small>{order.retailerName || 'Retailer'} · {dateLabel(order.createdAt)}</small></span><OrderStatusBadge status={order.status} /><strong>{money(order.finalAmount, order.currency)}</strong></Link>)}</div> : <p>No orders have been placed yet.</p>}</div></>}
      </section>

      <section className="desk-filter-panel" aria-label="Order filters">
        <label className="desk-search"><Search size={17} /><span className="desk-visually-hidden">Search all orders</span><input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Search orders: ID, reference, retailer, phone" /></label>
        <label className="desk-filter-select"><span>Status</span><select value={status} onChange={updateFilter(setStatus)}><option value="">All statuses</option>{STATUSES.map((value) => <option key={value} value={value}>{statusLabel(value)}</option>)}</select></label>
        <label className="desk-date-filter"><span><CalendarDays size={15} />From</span><input type="date" value={from} onChange={updateFilter(setFrom)} /></label>
        <label className="desk-date-filter"><span><CalendarDays size={15} />To</span><input type="date" value={to} onChange={updateFilter(setTo)} /></label>
        <label className="desk-filter-select desk-page-size"><span>Rows</span><select value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0) }}><option value={20}>20</option><option value={50}>50</option><option value={100}>100</option></select></label>
      </section>

      <section className="desk-queue-card" aria-labelledby="desk-queue-title">
        <div className="desk-section-heading"><div><h2 id="desk-queue-title">Orders</h2><p>{totalElements.toLocaleString('en-IN')} results{query ? ' matching this search' : ''}</p></div><span className="desk-page-indicator">Page {totalPages ? page + 1 : 0} of {totalPages}</span></div>
        {loading ? <div className="desk-loading"><RefreshCw className="desk-spin" size={22} />Loading orders…</div>
          : error ? <div className="desk-message desk-message-error" role="alert"><AlertCircle size={19} /><div><strong>Queue unavailable</strong><p>{error}</p><button type="button" className="desk-text-button" onClick={load}>Retry</button></div></div>
            : visibleOrders.length === 0 ? <div className="desk-empty"><span className="desk-empty-mark">✓</span><h3>{orders.length ? 'No orders match this search' : 'No orders found'}</h3><p>{orders.length ? 'Try a different order number, retailer, or phone.' : 'Change the status or date filters to see more orders.'}</p></div>
              : <div className="desk-table-wrap"><table className="desk-orders-table"><thead><tr><th>Order</th><th>Retailer</th><th>Submitted</th><th>Lines</th><th className="desk-align-right">Final total</th><th>Status</th><th><span className="desk-visually-hidden">Open order</span></th></tr></thead><tbody>{visibleOrders.map((order) => <tr key={order.id}>
                <td data-label="Order"><Link className="desk-order-reference" to={`/order-desk/orders/${order.id}`}>{order.orderNumber || order.id}</Link><small className="desk-order-id">{order.id}</small></td>
                <td data-label="Retailer"><strong>{order.retailerName || 'Retailer'}</strong><small>{order.retailerContact || 'No phone provided'}</small></td>
                <td data-label="Submitted">{dateLabel(order.createdAt)}</td><td data-label="Products">{order.items?.length || 0} products</td>
                <td data-label="Final total" className="desk-align-right desk-total-cell">{money(order.finalAmount, order.currency)}</td><td data-label="Status"><OrderStatusBadge status={order.status} /></td>
                <td data-label="Details"><Link className="desk-row-link" aria-label={`Open ${order.orderNumber || 'order'}`} to={`/order-desk/orders/${order.id}`}>Open <ArrowRight size={14} /></Link></td>
              </tr>)}</tbody></table></div>}
        {!loading && !error && totalPages > 1 && <nav className="desk-pagination" aria-label="Order pages"><span>Showing {Math.min(totalElements, page * size + 1)}–{Math.min(totalElements, (page + 1) * size)} of {totalElements}</span><div><button type="button" className="desk-page-button" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page <= 0}><ChevronLeft size={17} />Previous</button><button type="button" className="desk-page-button" onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))} disabled={page >= totalPages - 1}>Next<ChevronRight size={17} /></button></div></nav>}
      </section>
    </div>
  )
}

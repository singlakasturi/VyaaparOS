import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, ArrowDownToLine, CalendarDays, Check, ChevronLeft, ChevronRight, FileSpreadsheet, RefreshCw, Search } from 'lucide-react'
import { exportTallyCsv, fetchDeskOrders, fetchExportBatches } from '../../services/orderDesk.js'

const EMPTY_ORDERS = []
const dateBounds = (from, to) => ({
  startDate: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
  endDate: to ? new Date(new Date(`${to}T00:00:00`).getTime() + 86400000).toISOString() : undefined,
})
const money = (amount, currency) => amount == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(Number(amount))
const dateLabel = (value) => value ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'

function downloadFile({ blob, filename }) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'tally-orders.csv'
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export default function TallyExports() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [approved, setApproved] = useState(null)
  const [batches, setBatches] = useState(null)
  const [selected, setSelected] = useState(() => new Set())
  const [loading, setLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [confirmExport, setConfirmExport] = useState(false)
  const [error, setError] = useState('')
  const [historyError, setHistoryError] = useState('')
  const [notice, setNotice] = useState('')

  const loadApproved = useCallback(async () => {
    setLoading(true); setError('')
    if (from && to && from > to) { setError('Start date must be on or before end date.'); setLoading(false); return }
    try { setApproved(await fetchDeskOrders({ status: 'APPROVED', ...dateBounds(from, to), page, size: 50 })) }
    catch (err) { setError(err.message || 'Approved orders could not be loaded.') }
    finally { setLoading(false) }
  }, [from, to, page])

  const loadBatches = useCallback(async () => {
    setHistoryLoading(true); setHistoryError('')
    try { setBatches(await fetchExportBatches()) }
    catch (err) { setHistoryError(err.message || 'Export history could not be loaded.') }
    finally { setHistoryLoading(false) }
  }, [])

  useEffect(() => { loadApproved() }, [loadApproved])
  useEffect(() => { loadBatches() }, [loadBatches])

  const orders = approved?.content || EMPTY_ORDERS
  const visibleOrders = useMemo(() => {
    const term = query.trim().toLocaleLowerCase()
    return term ? orders.filter((order) => [order.orderNumber, order.id, order.retailerName].some((value) => String(value || '').toLocaleLowerCase().includes(term))) : orders
  }, [orders, query])
  const pageIds = visibleOrders.map((order) => order.id)
  const allVisibleSelected = pageIds.length > 0 && pageIds.every((id) => selected.has(id))
  const togglePage = () => setSelected((current) => {
    const next = new Set(current)
    if (allVisibleSelected) pageIds.forEach((id) => next.delete(id))
    else pageIds.slice(0, Math.max(0, 500 - next.size)).forEach((id) => next.add(id))
    return next
  })

  const runExport = async () => {
    if (busy || selected.size === 0) return
    setBusy(true); setError(''); setNotice(''); setConfirmExport(false)
    try {
      const response = await exportTallyCsv({ orderIds: Array.from(selected) })
      downloadFile(response)
      setNotice(`Tally CSV downloaded for ${selected.size} approved order${selected.size === 1 ? '' : 's'}. The exported orders are now marked TALLY EXPORTED.`)
      setSelected(new Set())
      await Promise.all([loadApproved(), loadBatches()])
    } catch (err) { setError(err.message || 'The Tally CSV export failed.') }
    finally { setBusy(false) }
  }

  const history = batches?.content || []
  const historyPages = batches?.totalPages || 0

  return (
    <div className="desk-page">
      <header className="desk-page-header"><div><span className="desk-eyebrow">ACCOUNTING HANDOFF</span><h1>Tally exports</h1><p>Select approved orders and download the backend-generated CSV staging file.</p></div><button className="desk-button desk-button-secondary" type="button" onClick={() => Promise.all([loadApproved(), loadBatches()])} disabled={loading || historyLoading}><RefreshCw size={16} className={loading || historyLoading ? 'desk-spin' : ''} />Refresh</button></header>
      {notice && <div className="desk-message desk-message-success" role="status"><Check size={18} />{notice}</div>}
      {error && <div className="desk-message desk-message-error" role="alert"><AlertCircle size={18} /><span>{error}</span><button className="desk-text-button" type="button" onClick={() => Promise.all([loadApproved(), loadBatches()])}>Retry</button></div>}

      <section className="desk-panel desk-export-panel"><div className="desk-panel-title"><div><span className="desk-eyebrow">ELIGIBLE ORDERS</span><h2>Approved orders</h2></div><span className="desk-export-selection">{selected.size} selected</span></div>
        <div className="desk-export-filters"><label className="desk-search"><Search size={16} /><span className="desk-visually-hidden">Search approved orders on this page</span><input type="search" placeholder="Search this page by order or retailer" value={query} onChange={(event) => setQuery(event.target.value)} /></label><label className="desk-date-filter"><span><CalendarDays size={15} />From</span><input type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0); setSelected(new Set()) }} /></label><label className="desk-date-filter"><span><CalendarDays size={15} />To</span><input type="date" value={to} onChange={(event) => { setTo(event.target.value); setPage(0); setSelected(new Set()) }} /></label></div>
        {loading ? <div className="desk-loading"><RefreshCw size={20} className="desk-spin" />Loading eligible orders…</div> : error ? <div className="desk-empty"><span className="desk-empty-mark"><AlertCircle size={20} /></span><h3>Approved orders unavailable</h3><p>Retry loading the eligible order list.</p><button className="desk-text-button" type="button" onClick={loadApproved}>Retry</button></div> : !visibleOrders.length ? <div className="desk-empty"><span className="desk-empty-mark"><FileSpreadsheet size={20} /></span><h3>No approved orders available</h3><p>Approved, unexported orders appear here. Exported orders leave this list automatically.</p></div> : <div className="desk-table-wrap"><table className="desk-orders-table desk-export-table"><thead><tr><th><input type="checkbox" aria-label="Select all visible approved orders" checked={allVisibleSelected} disabled={!allVisibleSelected && selected.size >= 500} onChange={togglePage} /></th><th>Order</th><th>Retailer</th><th>Approved total</th><th>Submitted</th></tr></thead><tbody>{visibleOrders.map((order) => <tr key={order.id}><td data-label="Select"><input type="checkbox" aria-label={`Select ${order.orderNumber || 'order'}`} checked={selected.has(order.id)} disabled={!selected.has(order.id) && selected.size >= 500} onChange={() => setSelected((current) => { const next = new Set(current); if (next.has(order.id)) { next.delete(order.id) } else { next.add(order.id) }; return next })} /></td><td data-label="Order"><Link className="desk-order-reference" to={`/order-desk/orders/${order.id}`}>{order.orderNumber || order.id}</Link><small className="desk-order-id">{order.id}</small></td><td data-label="Retailer"><strong>{order.retailerName || 'Retailer'}</strong><small>{order.retailerContact || 'No phone provided'}</small></td><td data-label="Approved total" className="desk-total-cell">{money(order.finalAmount, order.currency)}</td><td data-label="Submitted">{dateLabel(order.createdAt)}</td></tr>)}</tbody></table></div>}
        {selected.size >= 500 && <p className="desk-selection-limit" role="status">The backend allows at most 500 orders per export.</p>}
        <div className="desk-export-footer"><span>{approved?.totalElements || 0} eligible orders{from || to ? ' in selected date range' : ''}</span><div className="desk-export-page-controls">{(approved?.totalPages || 0) > 1 && <><button type="button" className="desk-page-button" disabled={page <= 0} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft size={16} />Prev</button><span>{page + 1} / {approved.totalPages}</span><button type="button" className="desk-page-button" disabled={page >= approved.totalPages - 1} onClick={() => setPage((value) => Math.min(approved.totalPages - 1, value + 1))}>Next<ChevronRight size={16} /></button></>}</div><button className="desk-button desk-button-primary" type="button" disabled={!selected.size || busy} onClick={() => setConfirmExport(true)}><ArrowDownToLine size={16} />{busy ? 'Preparing CSV…' : `Export ${selected.size || ''} selected`}</button></div>
        {confirmExport && <div className="desk-export-confirm" role="dialog" aria-modal="true" aria-labelledby="desk-export-confirm-title"><div><span className="desk-eyebrow">CONFIRM EXPORT</span><h3 id="desk-export-confirm-title">Export {selected.size} order{selected.size === 1 ? '' : 's'} to Tally?</h3><p>The server will create and download its CSV file. Selected orders will move from APPROVED to TALLY EXPORTED and cannot be exported again.</p></div><div><button className="desk-button desk-button-secondary" type="button" onClick={() => setConfirmExport(false)}>Cancel</button><button className="desk-button desk-button-primary" type="button" onClick={runExport} disabled={busy}>{busy ? 'Exporting…' : 'Confirm and download'}</button></div></div>}
      </section>

      <section className="desk-panel desk-export-history"><div className="desk-panel-title"><div><span className="desk-eyebrow">AUDITABLE BATCHES</span><h2>Export history</h2></div><span className="desk-page-indicator">Page {historyPages ? (batches?.number || 0) + 1 : 0} of {historyPages}</span></div>
        {historyLoading ? <div className="desk-loading"><RefreshCw size={19} className="desk-spin" />Loading export history…</div> : historyError ? <div className="desk-inline-error" role="alert">{historyError}<button type="button" className="desk-text-button" onClick={loadBatches}>Retry</button></div> : !history.length ? <p className="desk-subtle">No Tally exports have been created yet.</p> : <div className="desk-batch-list">{history.map((batch) => <Link className="desk-batch-row" key={batch.id} to={`/order-desk/exports/${batch.id}`}><span className="desk-batch-icon"><FileSpreadsheet size={18} /></span><span className="desk-batch-info"><strong>{batch.filename}</strong><small>{dateLabel(batch.createdAt)} · {batch.orderCount} orders</small></span><span className="desk-batch-orders">{batch.orderNumbers?.slice(0, 3).join(', ')}{batch.orderNumbers?.length > 3 ? ` +${batch.orderNumbers.length - 3} more` : ''}</span><ChevronRight size={17} /></Link>)}</div>}
        {historyPages > 1 && <div className="desk-pagination"><span>{batches.totalElements} batches</span><div><button className="desk-page-button" type="button" disabled={batches.first} onClick={async () => { setHistoryLoading(true); try { setBatches(await fetchExportBatches((batches.number || 0) - 1)) } catch (err) { setHistoryError(err.message) } finally { setHistoryLoading(false) } }}><ChevronLeft size={16} />Previous</button><button className="desk-page-button" type="button" disabled={batches.last} onClick={async () => { setHistoryLoading(true); try { setBatches(await fetchExportBatches((batches.number || 0) + 1)) } catch (err) { setHistoryError(err.message) } finally { setHistoryLoading(false) } }}>Next<ChevronRight size={16} /></button></div></div>}
      </section>
    </div>
  )
}

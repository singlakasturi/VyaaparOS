import { useEffect, useState } from 'react'
import { ArrowDownToLine, BarChart3, LoaderCircle, RefreshCw } from 'lucide-react'
import { exportDistributorReports, fetchDistributorReports, triggerCsvDownload, validateReportDateRange } from '../../services/distributorCatalog.js'
import './DistributorReports.css'

const todayUtc = () => new Date().toISOString().slice(0, 10)
const defaultRange = () => {
  const end = todayUtc()
  const start = new Date(`${end}T00:00:00Z`)
  start.setUTCDate(start.getUTCDate() - 29)
  return { fromDate: start.toISOString().slice(0, 10), toDate: end }
}
const number = value => new Intl.NumberFormat('en-IN').format(value || 0)
const money = (value, currency) => {
  try { return new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR', maximumFractionDigits: 2 }).format(value || 0) }
  catch { return `${currency || ''} ${number(value)}` }
}
const label = value => String(value || '').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())

export default function DistributorReports() {
  const [draft, setDraft] = useState(defaultRange)
  const [range, setRange] = useState(defaultRange)
  const [metric, setMetric] = useState('quantity')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [validation, setValidation] = useState('')
  const [exportType, setExportType] = useState('combined')
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState('')

  const load = async (activeRange = range, activeMetric = metric) => {
    setLoading(true); setError('')
    try { setData(await fetchDistributorReports({ ...activeRange, topProductMetric: activeMetric })) }
    catch (e) { setError(e.message || 'Reports could not be loaded.') }
    finally { setLoading(false) }
  }
  useEffect(() => { load(range, metric) }, [range, metric])

  const apply = event => {
    event.preventDefault()
    if (!validateReportDateRange(draft.fromDate, draft.toDate)) {
      setValidation('Choose a valid date range of up to 366 days ending today or earlier.')
      return
    }
    setValidation(''); setRange({ ...draft })
  }
  const exportCsv = async () => {
    if (exporting) return
    setExporting(true); setExportError('')
    try {
      const file = await exportDistributorReports({ ...range, topProductMetric: metric, report: exportType })
      triggerCsvDownload(file)
    } catch (e) { setExportError(e.message || 'Report export could not be downloaded.') }
    finally { setExporting(false) }
  }
  const movements = data?.inventoryMovements
  return <div className="desk-page reports-page">
    <header className="desk-page-header"><div><span className="desk-eyebrow">DISTRIBUTOR OPERATIONS</span><h1>Reports</h1><p>Order activity and inventory movements from persisted order and ledger records.</p></div></header>
    <form className="desk-filter-panel reports-controls" onSubmit={apply}>
      <label className="desk-date-filter">From<input type="date" value={draft.fromDate} max={draft.toDate} onChange={e => setDraft(v => ({ ...v, fromDate: e.target.value }))}/></label>
      <label className="desk-date-filter">Through<input type="date" value={draft.toDate} min={draft.fromDate} max={todayUtc()} onChange={e => setDraft(v => ({ ...v, toDate: e.target.value }))}/></label>
      <label className="desk-filter-select">Top products ranked by<select value={metric} onChange={e => setMetric(e.target.value)}><option value="quantity">Ordered cases</option><option value="value">Order value</option></select></label>
      <button className="desk-button desk-button-primary" type="submit">Apply dates</button>
      <label className="desk-filter-select">Export report<select value={exportType} onChange={e => setExportType(e.target.value)}><option value="combined">Combined report</option><option value="activity">Order activity</option><option value="top_products">Top products</option><option value="order_statuses">Order status summary</option><option value="inventory_movements">Inventory movements</option></select></label>
      <button className="desk-button desk-button-secondary" type="button" disabled={exporting||loading} onClick={exportCsv}>{exporting?<LoaderCircle className="desk-spin" size={16}/>:<ArrowDownToLine size={16}/>}Export CSV</button>
    </form>
    {validation && <div className="desk-message desk-message-error" role="alert">{validation}</div>}
    {exportError && <div className="desk-message desk-message-error" role="alert"><strong>{exportError}</strong><button className="desk-text-button" type="button" onClick={exportCsv}>Retry export</button></div>}
    {error && <div className="desk-message desk-message-error" role="alert"><strong>{error}</strong><button className="desk-text-button" onClick={() => load()} type="button"><RefreshCw size={15}/> Retry</button></div>}
    {loading ? <div className="desk-loading"><LoaderCircle className="desk-spin"/>Loading reports…</div> : data && <>
      <p className="reports-period">{data.fromDate} through {data.toDate} · {data.timezone} · {data.activityStatusRule}</p>
      <section className="reports-section"><div className="reports-heading"><div><h2>Order activity by day</h2><p>Order count and persisted order value, grouped by UTC order date and currency.</p></div></div>
        {!data.orderActivity.length ? <div className="reports-empty"><BarChart3/><p>No order activity in this date range.</p></div> : <div className="reports-table-wrap"><table><thead><tr><th>Date (UTC)</th><th>Currency</th><th>Orders</th><th>Order value</th></tr></thead><tbody>{data.orderActivity.map((row, i) => <tr key={`${row.day}-${row.currency}-${i}`}><td>{row.day}</td><td>{row.currency}</td><td>{number(row.orderCount)}</td><td>{money(row.orderValue, row.currency)}</td></tr>)}</tbody></table></div>}
      </section>
      <div className="reports-grid">
        <section className="reports-section"><div className="reports-heading"><div><h2>Top products</h2><p>Ranked by {data.topProductMetric === 'value' ? 'persisted item value' : 'total ordered cases, including free cases'}.</p></div></div>
          {!data.topProducts.length ? <div className="reports-empty"><p>No eligible order items in this date range.</p></div> : <div className="reports-table-wrap"><table><thead><tr><th>#</th><th>Product</th><th>Cases</th><th>Value</th></tr></thead><tbody>{data.topProducts.map(row => <tr key={`${row.rank}-${row.productName}-${row.currency}`}><td>{row.rank}</td><td>{row.productName}</td><td>{number(row.orderedCases)}</td><td>{money(row.orderValue, row.currency)}</td></tr>)}</tbody></table></div>}
        </section>
        <section className="reports-section"><div className="reports-heading"><div><h2>Order status totals</h2><p>All orders placed during the selected date range.</p></div></div><ul className="reports-status-list">{data.orderStatuses.map(row => <li key={row.status}><span>{label(row.status)}</span><strong>{number(row.orderCount)}</strong></li>)}</ul></section>
      </div>
      <section className="reports-section"><div className="reports-heading"><div><h2>Inventory movement summary</h2><p>Physical stock changes are separate from reservations. Units are cases; opening snapshots have zero movement.</p></div></div>
        <p className="reports-period">{data.movementCoverageRule}</p><div className="reports-movement-cards"><div><span>Physical inward</span><strong>{number(movements.physicalInwardCases)} cases</strong><small>Positive on-hand deltas</small></div><div><span>Physical outward</span><strong>{number(movements.physicalOutwardCases)} cases</strong><small>Negative on-hand deltas, including delivered consumption</small></div><div><span>Reserved</span><strong>{number(movements.reservationCreatedCases)} cases</strong><small>Order reservations created</small></div><div><span>Released</span><strong>{number(movements.reservationReleasedCases)} cases</strong><small>Reservation releases</small></div><div><span>Delivered</span><strong>{number(movements.deliveryConsumedCases)} cases</strong><small>Reserved cases consumed on delivery</small></div></div>
        <div className="reports-table-wrap"><table><thead><tr><th>Movement type</th><th>Events</th><th>On-hand change (cases)</th><th>Reserved change (cases)</th></tr></thead><tbody>{movements.byMovementType.map(row => <tr key={row.movementType}><td>{label(row.movementType)}</td><td>{number(row.movementCount)}</td><td>{number(row.onHandDeltaCases)}</td><td>{number(row.reservedDeltaCases)}</td></tr>)}</tbody></table></div>
      </section>
    </>}
  </div>
}

import { useCallback, useEffect, useMemo, useState } from 'react'
import { LoaderCircle, RefreshCw } from 'lucide-react'
import { useOutletContext } from 'react-router-dom'
import { fetchRetailerStatement, fetchRetailerStatementDistributor } from '../../services/retailerStatement.js'
import './RetailerStatement.css'

const todayUtc = () => new Date().toISOString().slice(0, 10)
const initialRange = () => {
  const toDate = todayUtc()
  const from = new Date(`${toDate}T00:00:00Z`)
  from.setUTCDate(from.getUTCDate() - 29)
  return { fromDate: from.toISOString().slice(0, 10), toDate }
}
const formatMoney = (value, currency) => {
  try { return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value || 0) }
  catch { return `${currency} ${Number(value || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}` }
}
const statusLabel = value => String(value || '').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
function validRange({ fromDate, toDate }) {
  if (!fromDate || !toDate || fromDate > toDate || toDate > todayUtc()) return false
  const days = (Date.parse(`${toDate}T00:00:00Z`) - Date.parse(`${fromDate}T00:00:00Z`)) / 86400000 + 1
  return days <= 366
}

export default function RetailerStatement() {
  const { retailerId } = useOutletContext()
  const [activeDistributor, setActiveDistributor] = useState(null)
  const [distributorLoading, setDistributorLoading] = useState(true)
  const [draft, setDraft] = useState(initialRange)
  const [range, setRange] = useState(initialRange)
  const [statement, setStatement] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [validation, setValidation] = useState('')

  const loadDistributor = useCallback(async () => {
    setDistributorLoading(true); setError('')
    try { setActiveDistributor(await fetchRetailerStatementDistributor(retailerId) || null) }
    catch (err) { setError(err.message || 'Your distributor could not be loaded.') }
    finally { setDistributorLoading(false) }
  }, [retailerId])

  useEffect(() => {
    loadDistributor()
  }, [loadDistributor])

  const load = async (selectedRange = range) => {
    if (!activeDistributor?.id || !retailerId) return
    setLoading(true); setError('')
    try {
      setStatement(await fetchRetailerStatement({ retailerId, distributorId: activeDistributor.id, ...selectedRange }))
    } catch (err) { setError(err.message || 'The statement could not be loaded.') }
    finally { setLoading(false) }
  }

  useEffect(() => { if (activeDistributor?.id) load(range) }, [activeDistributor?.id, retailerId, range.fromDate, range.toDate])

  const totals = useMemo(() => statement?.orderActivityTotals || [], [statement])
  const apply = event => {
    event.preventDefault()
    if (!validRange(draft)) { setValidation('Choose a valid date range of up to 366 days ending today or earlier.'); return }
    setValidation(''); setRange({ ...draft })
  }

  return <section className="retailer-statement-page">
    <header className="retailer-statement-header"><div><span className="retailer-statement-eyebrow">RETAILER ACTIVITY</span><h1>Account statement</h1><p>Order activity with your current distributor. This statement does not show payments or balances.</p></div></header>
    <div className="retailer-statement-controls">
      <label className="retailer-statement-field">Distributor
        <select aria-label="Select distributor" value={activeDistributor?.id || ''} disabled={distributorLoading || !activeDistributor}>
          {distributorLoading ? <option value="">Loading distributors…</option> : activeDistributor ? <option value={activeDistributor.id}>{activeDistributor.businessName}</option> : <option value="">No active distributor</option>}
        </select>
      </label>
      <form className="retailer-statement-dates" onSubmit={apply}>
        <label className="retailer-statement-field">From<input type="date" value={draft.fromDate} max={draft.toDate} onChange={e => setDraft(v => ({ ...v, fromDate: e.target.value }))}/></label>
        <label className="retailer-statement-field">Through<input type="date" value={draft.toDate} min={draft.fromDate} max={todayUtc()} onChange={e => setDraft(v => ({ ...v, toDate: e.target.value }))}/></label>
        <button className="retailer-statement-button" type="submit">Apply dates</button>
      </form>
    </div>
    {validation && <p className="retailer-statement-error" role="alert">{validation}</p>}
    {!distributorLoading && !activeDistributor && !error && <div className="retailer-statement-empty"><h2>No active distributor</h2><p>Link a distributor to view order activity.</p></div>}
    {error && <div className="retailer-statement-error" role="alert"><span>{error}</span><button type="button" onClick={() => activeDistributor ? load() : loadDistributor()}><RefreshCw size={15}/> Retry</button></div>}
    {(loading || distributorLoading) && <div className="retailer-statement-state"><LoaderCircle className="retailer-statement-spin"/>Loading statement…</div>}
    {statement && !loading && <>
      <p className="retailer-statement-period">{statement.distributorName} · {statement.fromDate} through {statement.toDate} ({statement.timezone})</p>
      <section className="retailer-statement-totals"><h2>Order activity total</h2><p>Persisted order values for submitted, credit hold, approved, and exported orders. Rejected and cancelled orders are excluded.</p>
        {totals.length ? <div className="retailer-statement-total-list">{totals.map(total => <div key={total.currency}><span>{total.currency}</span><strong>{formatMoney(total.orderActivityTotal, total.currency)}</strong></div>)}</div> : <div className="retailer-statement-total-list"><div><span>Eligible orders</span><strong>0</strong></div></div>}
      </section>
      <section className="retailer-statement-orders"><h2>Orders</h2>
        {!statement.orders.length ? <div className="retailer-statement-empty"><p>No orders in this date range.</p></div> : <div className="retailer-statement-table-wrap"><table><thead><tr><th>Order reference</th><th>Date (UTC)</th><th>Status</th><th>Items</th><th>Base</th><th>Discount</th><th>Order value</th></tr></thead><tbody>
          {statement.orders.map(order => <tr key={order.id}><td data-label="Order reference"><strong>{order.orderNumber}</strong></td><td data-label="Date (UTC)">{new Date(order.createdAt).toISOString().replace('T', ' ').slice(0, 16)}</td><td data-label="Status"><span className="retailer-statement-status">{statusLabel(order.status)}</span></td><td data-label="Items"><ul>{order.items.map((item, index) => <li key={`${item.productId}-${index}`}><strong>{item.name}</strong><small>{item.sku} · {item.paidCases} paid + {item.freeCases} free · {formatMoney(item.finalAmount, item.currency)}</small></li>)}</ul></td><td data-label="Base">{formatMoney(order.baseAmount, order.currency)}</td><td data-label="Discount">{formatMoney(order.discountAmount, order.currency)}</td><td data-label="Order value"><strong>{formatMoney(order.finalAmount, order.currency)}</strong></td></tr>)}
        </tbody></table></div>}
      </section>
    </>}
  </section>
}

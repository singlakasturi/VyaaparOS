import { useState } from 'react'
import { ArrowRight, LoaderCircle, Plus, ShoppingCart, Trash2 } from 'lucide-react'
import { Link, useOutletContext } from 'react-router-dom'
import { submitQuickOrder } from '../../services/quickOrder.js'
import './RetailerQuickOrder.css'

const makeRow = () => ({ id: crypto.randomUUID(), productCode: '', quantity: '1' })

export default function RetailerQuickOrder() {
  const { retailerId, setCartCount } = useOutletContext()
  const [rows, setRows] = useState(() => [makeRow(), makeRow(), makeRow()])
  const [submittedRows, setSubmittedRows] = useState([])
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [validation, setValidation] = useState('')

  const update = (id, field, value) => {
    setRows(current => current.map(row => row.id === id ? { ...row, [field]: value } : row))
    setResult(null); setValidation('')
  }

  const addRow = () => {
    if (rows.length >= 100) { setValidation('Quick Order supports up to 100 rows at a time.'); return }
    setRows(current => [...current, makeRow()]); setResult(null); setValidation('')
  }

  const removeRow = id => {
    setRows(current => current.length > 1 ? current.filter(row => row.id !== id) : [makeRow()])
    setResult(null); setValidation('')
  }

  const submit = async event => {
    event.preventDefault(); setError(''); setValidation(''); setResult(null)
    const entered = rows.filter(row => row.productCode.trim() || row.quantity.trim() !== '1')
    if (!entered.length) { setValidation('Enter a product code and quantity to add items to your cart.'); return }
    setLoading(true)
    try {
      const response = await submitQuickOrder(retailerId, entered.map(row => ({ productCode: row.productCode, quantity: row.quantity })))
      setSubmittedRows(entered)
      setResult(response)
      setCartCount((response.cart?.items || []).reduce((sum, item) => sum + Number(item.paidCases || 0), 0))
    } catch (err) { setError(err.message || 'Quick Order could not be processed.') }
    finally { setLoading(false) }
  }

  const outcomes = result?.results || []
  const addedCount = outcomes.filter(row => row.outcome === 'ADDED').length
  const skippedCount = outcomes.length - addedCount

  return <div className="retailer-page retailer-quick-order-page">
    <header className="retailer-quick-order-header"><div><span className="retailer-eyebrow">REPEAT ORDERING</span><h1>Quick Order</h1><p>Enter product codes and case quantities. Codes are checked against your current distributor catalogue.</p></div><Link className="retailer-button retailer-button-outline" to="/retailer/cart"><ShoppingCart size={17}/>Review cart</Link></header>
    {error && <div className="retailer-quick-order-error" role="alert">{error}</div>}
    {validation && <div className="retailer-quick-order-validation" role="alert">{validation}</div>}
    {!result && !rows.some(row => row.productCode.trim()) && <p className="retailer-quick-order-empty">No product codes entered yet. Add a catalogue SKU and case quantity in each row, then add the valid items to your cart.</p>}
    <form className="retailer-quick-order-form" onSubmit={submit}>
      <div className="retailer-quick-order-labels"><span>Product code / SKU</span><span>Quantity (cases)</span><span className="sr-only">Row actions</span></div>
      <div className="retailer-quick-order-rows">{rows.map((row, index) => <div className="retailer-quick-order-row" key={row.id}>
        <label><span className="sr-only">Product code, row {index + 1}</span><input autoComplete="off" value={row.productCode} onChange={event => update(row.id, 'productCode', event.target.value)} placeholder="e.g. SKU-BS-075" maxLength={60}/></label>
        <label><span className="sr-only">Quantity in cases, row {index + 1}</span><input inputMode="numeric" value={row.quantity} onChange={event => update(row.id, 'quantity', event.target.value)} placeholder="Cases" aria-label={`Quantity in cases, row ${index + 1}`}/></label>
        <button className="retailer-quick-order-remove" type="button" onClick={() => removeRow(row.id)} aria-label={`Remove row ${index + 1}`} title="Remove row"><Trash2 size={17}/></button>
      </div>)}</div>
      <div className="retailer-quick-order-actions"><button className="retailer-button retailer-button-outline" type="button" disabled={rows.length >= 100} onClick={addRow}><Plus size={17}/>Add another row</button><button className="retailer-button retailer-button-primary" type="submit" disabled={loading}>{loading ? <><LoaderCircle className="retailer-quick-order-spin"/>Adding items…</> : 'Add valid items to cart'}</button></div>
    </form>
    {loading && <div className="retailer-state"><LoaderCircle className="retailer-quick-order-spin"/>Checking products and current cart rules…</div>}
    {result && <section className="retailer-quick-order-results" aria-live="polite"><div className="retailer-quick-order-results-heading"><div><span className="retailer-eyebrow">CART UPDATE</span><h2>{addedCount} added{skippedCount ? ` · ${skippedCount} not added` : ''}</h2></div><Link className="retailer-button retailer-button-primary" to="/retailer/cart">Review cart<ArrowRight size={17}/></Link></div>
      {!outcomes.length ? <p>No rows were processed.</p> : <div className="retailer-quick-order-table-wrap"><table><thead><tr><th>Row</th><th>Code</th><th>Product</th><th>Cases</th><th>Result</th></tr></thead><tbody>{outcomes.map(outcome => <tr className={outcome.outcome === 'ADDED' ? 'is-added' : 'is-not-added'} key={`${outcome.rowNumber}-${outcome.productCode || 'empty'}`}><td data-label="Row">{outcome.rowNumber}</td><td data-label="Code">{submittedRows[outcome.rowNumber - 1]?.productCode || outcome.productCode || '—'}</td><td data-label="Product">{outcome.productName || '—'}</td><td data-label="Cases">{outcome.quantity ?? submittedRows[outcome.rowNumber - 1]?.quantity ?? '—'}</td><td data-label="Result"><strong>{outcome.outcome === 'ADDED' ? 'Added to cart' : 'Not added'}</strong><small>{outcome.message}</small></td></tr>)}</tbody></table></div>}
      <p className="retailer-quick-order-footnote">Items were added to your cart only. Review current prices, schemes, and availability in your cart before checkout.</p>
    </section>}
  </div>
}

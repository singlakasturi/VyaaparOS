import { useCallback, useEffect, useMemo, useState } from 'react'
import { Check, CircleDollarSign, LoaderCircle, Search } from 'lucide-react'
import { deactivateManagedPrice, fetchManagedPrices, upsertManagedPrice, validatePriceForm } from '../../services/distributorCatalog.js'
import './DistributorPricing.css'

export default function DistributorPricing() {
  const [prices, setPrices] = useState([])
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [amount, setAmount] = useState('')
  const [gstIncluded, setGstIncluded] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const rows = await fetchManagedPrices()
      setPrices(rows || [])
      setSelectedId(current => (rows || []).some(row => row.productId === current) ? current : rows?.[0]?.productId || null)
    } catch (e) { setError(e.message || 'Pricing could not be loaded.') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { load() }, [load])

  const visible = useMemo(() => prices.filter(row => `${row.productName} ${row.sku}`.toLowerCase().includes(query.trim().toLowerCase())), [prices, query])
  const selected = visible.find(row => row.productId === selectedId) || visible[0] || null
  useEffect(() => { setSelectedId(selected?.productId || null); setAmount(selected?.pricePerCase == null ? '' : String(selected.pricePerCase)); setGstIncluded(selected?.gstIncluded == null ? '' : String(selected.gstIncluded)) }, [selectedId, prices, query])

  const save = async event => {
    event.preventDefault()
    if (!selected || !validatePriceForm({ pricePerCase: amount })) return
    setSaving(true); setError(''); setSuccess('')
    try {
      await upsertManagedPrice(selected.productId, { pricePerCase: Number(amount), currency: 'INR', gstIncluded: gstIncluded === '' ? null : gstIncluded === 'true' })
      setSuccess('Price saved. The previous price is retained in history.')
      await load()
    } catch (e) { setError(e.message || 'Price could not be saved.') }
    finally { setSaving(false) }
  }

  const deactivate = async () => {
    if (!selected?.priceActive) return
    setSaving(true); setError(''); setSuccess('')
    try { await deactivateManagedPrice(selected.productId); setSuccess('Price deactivated; retailers will no longer see it.'); await load() }
    catch (e) { setError(e.message || 'Price could not be deactivated.') }
    finally { setSaving(false) }
  }

  return <div className="desk-page pricing-page">
    <header className="desk-page-header"><div><span className="desk-eyebrow">DISTRIBUTOR OPERATIONS</span><h1>Product pricing</h1><p>Set the default case price retailers see through your distributor relationship.</p></div></header>
    {error && <div className="desk-message desk-message-error" role="alert"><div><strong>Pricing action failed</strong><p>{error}</p></div><button className="desk-text-button" onClick={load} type="button">Retry</button></div>}
    {success && <div className="desk-message desk-message-success" role="status"><Check size={18}/>{success}</div>}
    <section className="desk-filter-panel pricing-filter"><label className="desk-search"><Search size={17}/><span className="desk-visually-hidden">Search prices by product or SKU</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search product name or SKU" /></label></section>
    {loading ? <div className="desk-loading"><LoaderCircle className="desk-spin"/>Loading prices…</div> : visible.length === 0 ? <section className="desk-empty pricing-empty"><CircleDollarSign size={27}/><h3>{query ? 'No products match that search' : 'No products available'}</h3><p>Add products to your distributor catalog before setting prices.</p></section> : <section className="pricing-workspace">
      <div className="desk-queue-card pricing-list"><div className="desk-section-heading"><div><h2>Products</h2><p>{visible.length} shown</p></div></div><div className="pricing-product-list">{visible.map(row => <button key={row.productId} type="button" onClick={() => setSelectedId(row.productId)} className={`pricing-product-row${selected?.productId === row.productId ? ' is-selected' : ''}`}><span><strong>{row.productName}</strong><small>{row.sku}</small></span><span className={`pricing-pill${row.priceActive ? ' is-priced' : ''}`}>{row.priceActive ? `₹${Number(row.pricePerCase).toFixed(2)}` : 'Not priced'}</span></button>)}</div></div>
      <aside className="desk-queue-card pricing-editor"><div className="desk-section-heading"><div><h2>{selected.productName}</h2><p>{selected.sku} · {selected.productActive ? 'Active product' : 'Inactive product'}</p></div></div><form onSubmit={save}><label>Price per case (INR)<input required type="number" min="0" step="0.01" value={amount} onChange={event => setAmount(event.target.value)} placeholder="0.00" /></label><label>GST status<select value={gstIncluded} onChange={event => setGstIncluded(event.target.value)}><option value="">Not specified</option><option value="true">Included</option><option value="false">Excluded</option></select></label><p className="pricing-history-note">Saving a changed price retains the previous value as inactive history. Retailers see only the current active price.</p><div className="pricing-actions"><button className="desk-button desk-button-primary" disabled={saving || !validatePriceForm({ pricePerCase: amount })}>{saving ? 'Saving…' : 'Save price'}</button>{selected.priceActive && <button className="desk-button desk-button-secondary" disabled={saving} onClick={deactivate} type="button">Deactivate price</button>}</div></form></aside>
    </section>}
  </div>
}

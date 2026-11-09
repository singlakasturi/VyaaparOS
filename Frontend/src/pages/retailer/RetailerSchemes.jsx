import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, Tag } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useOutletContext } from 'react-router-dom'
import { fetchRetailerSchemes } from '../../services/distributorCatalog.js'
import './RetailerSchemes.css'

const offer = scheme => scheme.schemeType === 'BUY_X_GET_Y_FREE' ? `Buy ${scheme.buyQuantity} cases, get ${scheme.freeQuantity} free` : `${scheme.discountPercentage}% off when you buy ${scheme.minimumQuantity} or more cases`
const date = value => value ? new Date(value).toLocaleString() : null

export default function RetailerSchemes() {
  const { retailer, retailerId } = useOutletContext()
  const [schemes, setSchemes] = useState([]); const [loading, setLoading] = useState(true); const [error, setError] = useState('')
  const load = useCallback(async () => { if (!retailerId) { setSchemes([]); setLoading(false); return } setLoading(true); setError(''); try { setSchemes(await fetchRetailerSchemes(retailerId) || []) } catch (e) { setError(e.message || 'Promotions could not be loaded.') } finally { setLoading(false) } }, [retailerId])
  useEffect(() => { load() }, [load])
  return <main className="retailer-schemes-page"><header><span className="retailer-eyebrow">YOUR DISTRIBUTOR</span><h1>Current promotions</h1><p>Offers available from your active distributor relationship.</p></header>
    {error && <section className="retailer-schemes-state" role="alert"><h2>Promotions could not be loaded</h2><p>{error}</p><button className="retailer-button retailer-button-outline" onClick={load} type="button"><RefreshCw size={15}/>Try again</button></section>}
    {loading ? <div className="retailer-schemes-state">Loading promotions…</div> : !error && schemes.length === 0 ? <section className="retailer-schemes-state"><Tag size={25}/><h2>No current promotions</h2><p>Your distributor has no active offers for this retailer right now.</p><Link className="retailer-button retailer-button-primary" to="/retailer">Browse catalogue</Link></section> : <section className="retailer-schemes-grid">{schemes.map(s => <article className="retailer-scheme-card" key={s.id}><span className="retailer-scheme-tag"><Tag size={15}/>{s.applicability === 'PRODUCT' ? 'Product offer' : s.applicability === 'CATEGORY' ? 'Category offer' : 'Distributor offer'}</span><h2>{s.name}</h2>{s.description && <p>{s.description}</p>}<strong>{offer(s)}</strong><dl>{(s.startsAt || s.endsAt) && <div><dt>Valid</dt><dd>{date(s.startsAt) || 'Now'} to {date(s.endsAt) || 'Until further notice'}</dd></div>}<div><dt>Applies to</dt><dd>{s.applicabilityDetails}</dd></div></dl></article>)}</section>}
  </main>
}

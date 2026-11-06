import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchCategories, fetchProducts } from '../../services/catalog.js'
import { fetchDeskOrders } from '../../services/orderDesk.js'
import {
  addRetailerSalesperson, clearRetailerPrice, createAdminScheme, deactivateAdminScheme,
  fetchAdminRetailer, fetchAdminRetailers, fetchAdminSchemes, fetchRetailerSalespeople,
  removeRetailerSalesperson, setDefaultPrice, setRetailerPrice, updateAdminScheme,
} from '../../services/admin.js'
import '../../styles/admin.css'

const money = (value, currency = 'INR') => value == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(value)
const rows = (data) => Array.isArray(data) ? data : data?.content || data?.items || []
const total = (data) => data?.totalElements ?? rows(data).length
const asMessage = (error) => error?.message || 'The request could not be completed.'

function useLoad(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: '' })
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, loading: true, error: '' }))
    loader().then((data) => live && setState({ data, loading: false, error: '' }))
      .catch((error) => live && setState({ data: null, loading: false, error: asMessage(error) }))
    return () => { live = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reload])
  return { ...state, refresh: () => setReload((x) => x + 1) }
}

function Header({ eyebrow = 'VyapaarOS administration', title, description, action }) {
  return <header className="admin-page-header"><div><p className="admin-eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action}</header>
}
function ErrorNote({ error }) { return error ? <div role="alert" className="admin-notice admin-notice-error">{error}</div> : null }
function Loading() { return <p className="admin-muted" role="status">Loading…</p> }
function Empty({ children }) { return <div className="admin-empty">{children}</div> }
function Card({ children, className = '' }) { return <section className={`admin-card ${className}`}>{children}</section> }
function ProductSelect({ products, value, onChange }) { return <label>Product<select required value={value} onChange={(e) => onChange(e.target.value)}><option value="">Choose a product</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name} · {p.sku}</option>)}</select></label> }

export function AdminDashboard() {
  const retailers = useLoad(fetchAdminRetailers)
  const products = useLoad(() => fetchProducts({ page: 0, size: 1 }))
  const orders = useLoad(() => fetchDeskOrders({ page: 0, size: 6 }))
  const submitted = useLoad(() => fetchDeskOrders({ status: 'SUBMITTED', page: 0, size: 1 }))
  const holds = useLoad(() => fetchDeskOrders({ status: 'CREDIT_HOLD', page: 0, size: 1 }))
  const approved = useLoad(() => fetchDeskOrders({ status: 'APPROVED', page: 0, size: 1 }))
  const metrics = [
    ['Orders in queue', orders, 'Latest order desk records'],
    ['Submitted', submitted, 'Awaiting review'],
    ['Credit holds', holds, 'Require credit review'],
    ['Approved', approved, 'Orders approved'],
    ['Catalogue products', products, 'Available catalogue records'],
    ['Assigned retailers', retailers, 'Visible to this account'],
  ]
  return <div className="admin-workspace"><Header title="Admin overview" description="A live snapshot of order desk activity and the catalogue available to your account." action={<Link className="admin-button" to="/order-desk">Open order desk</Link>} />
    <div className="admin-metric-grid">{metrics.map(([name, result, caption]) => <Card key={name}><p className="admin-metric-label">{name}</p><strong className="admin-metric-value">{result.loading || result.error ? '—' : total(result.data)}</strong><span className="admin-muted">{caption}</span></Card>)}</div>
    <Card><div className="admin-card-heading"><div><h2>Recent orders</h2><p>Most recent records returned by the Order Desk API.</p></div><Link to="/order-desk">View queue →</Link></div>
      {orders.loading ? <Loading /> : orders.error ? <ErrorNote error={orders.error} /> : rows(orders.data).length ? <div className="admin-table-wrap"><table><thead><tr><th>Order</th><th>Retailer</th><th>Status</th><th>Amount</th><th>Created</th></tr></thead><tbody>{rows(orders.data).map((order) => <tr key={order.id}><td>{order.orderNumber || order.id}</td><td>{order.retailerName || 'Retailer'}</td><td><span className="admin-tag">{order.status}</span></td><td>{money(order.finalAmount, order.currency)}</td><td>{order.createdAt ? new Date(order.createdAt).toLocaleString() : '—'}</td></tr>)}</tbody></table></div> : <Empty>No orders are available in the order desk yet.</Empty>}
    </Card>
  </div>
}

export function AdminRetailers() {
  const { retailerId } = useParams()
  const list = useLoad(fetchAdminRetailers)
  const [search, setSearch] = useState('')
  const [personId, setPersonId] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const filtered = rows(list.data).filter((r) => `${r.businessName} ${r.ownerName} ${r.city}`.toLowerCase().includes(search.toLowerCase()))
  const detail = useLoad(() => fetchAdminRetailer(retailerId), [retailerId])
  const assignments = useLoad(() => fetchRetailerSalespeople(retailerId), [retailerId])
  const mutate = async (fn, success) => { setBusy(true); setNotice(''); try { await fn(); setNotice(success); assignments.refresh(); list.refresh() } catch (e) { setNotice(asMessage(e)) } finally { setBusy(false) } }
  return <div className="admin-workspace"><Header title="Retailers" description="Retailers assigned to the signed-in admin account, with current salesperson assignments." />
    <div className="admin-two-column"><Card><div className="admin-card-heading"><div><h2>Assigned retailers</h2><p>This endpoint returns account assignments, not a global directory.</p></div><span className="admin-tag">{filtered.length}</span></div><label className="admin-search">Search retailers<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Business, owner, or city" /></label>
      {list.loading ? <Loading /> : list.error ? <ErrorNote error={list.error} /> : filtered.length ? <ul className="admin-retailer-list">{filtered.map((r) => <li key={r.id}><Link className={retailerId === r.id ? 'selected' : ''} to={`/admin/retailers/${r.id}`}><strong>{r.businessName}</strong><span>{r.ownerName || r.city || r.contactEmail || r.id}</span><span className="admin-tag">{r.status}</span></Link></li>)}</ul> : <Empty>No assigned retailers match this search.</Empty>}</Card>
    <Card>{!retailerId ? <Empty>Select an assigned retailer to view details and salespeople.</Empty> : detail.loading ? <Loading /> : detail.error ? <ErrorNote error={detail.error} /> : <><div className="admin-card-heading"><div><h2>{detail.data?.businessName}</h2><p>{detail.data?.ownerName} · {detail.data?.contactEmail}</p></div><span className="admin-tag">{detail.data?.status}</span></div><dl className="admin-detail-list"><div><dt>Phone</dt><dd>{detail.data?.contactPhone || '—'}</dd></div><div><dt>Location</dt><dd>{[detail.data?.city, detail.data?.state, detail.data?.pincode].filter(Boolean).join(', ') || '—'}</dd></div><div><dt>Credit limit</dt><dd>{money(detail.data?.creditLimit)}</dd></div><div><dt>Outstanding</dt><dd>{money(detail.data?.outstandingBalance)}</dd></div><div><dt>Address</dt><dd>{detail.data?.address || '—'}</dd></div></dl><hr /><h3>Salesperson assignments</h3><p className="admin-muted">The backend has no salesperson directory. Add a known salesperson account UUID.</p><ErrorNote error={assignments.error} />{rows(assignments.data).map((person) => <div className="admin-assignment" key={person.id}><span><strong>{person.fullName}</strong><small>{person.email}</small></span><button className="admin-button admin-button-danger" disabled={busy} onClick={() => mutate(() => removeRetailerSalesperson(retailerId, person.id), 'Assignment removed.')}>Remove</button></div>)}<form className="admin-inline-form" onSubmit={(e) => { e.preventDefault(); if (personId.trim()) mutate(() => addRetailerSalesperson(retailerId, personId.trim()), 'Salesperson assigned.') }}><label>Salesperson account UUID<input required value={personId} onChange={(e) => setPersonId(e.target.value)} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" /></label><button className="admin-button" disabled={busy}>Assign salesperson</button></form></>}</Card></div>{notice && <div className="admin-notice" role="status">{notice}</div>}</div>
}

export function AdminProducts() {
  const [q, setQ] = useState(''); const [category, setCategory] = useState(''); const [page, setPage] = useState(0)
  const cats = useLoad(fetchCategories)
  const products = useLoad(() => fetchProducts({ q, category, page, size: 20 }), [q, category, page])
  const data = rows(products.data)
  return <div className="admin-workspace"><Header title="Products & inventory" description="Catalogue and available-case stock counts. Product and inventory write endpoints are not available." /><Card><div className="admin-filter-row"><label>Search products<input value={q} onChange={(e) => { setPage(0); setQ(e.target.value) }} placeholder="Name, SKU, or brand" /></label><label>Category<select value={category} onChange={(e) => { setPage(0); setCategory(e.target.value) }}><option value="">All categories</option>{rows(cats.data).map((c) => <option key={c.id || c.name} value={c.id || c.name}>{c.name}</option>)}</select></label></div><ErrorNote error={products.error || cats.error} />{products.loading ? <Loading /> : data.length ? <><div className="admin-table-wrap"><table><thead><tr><th>Product</th><th>SKU</th><th>Category</th><th>Pack</th><th>Available cases</th><th>Price / case</th><th>Scheme</th></tr></thead><tbody>{data.map((p) => <tr key={p.id}><td><strong>{p.name}</strong><small>{p.brand || ''}</small></td><td>{p.sku}</td><td>{p.category?.name || '—'}</td><td>{p.caseDescription || `${p.unitsPerCase} units`}</td><td><span className={`admin-stock ${p.availableCases === 0 ? 'out' : p.availableCases < 10 ? 'low' : ''}`}>{p.availableCases}</span></td><td>{p.priceAvailable ? money(p.pricePerCase, p.currency) : 'Not set'}</td><td>{p.schemeAvailable ? p.schemeName : '—'}</td></tr>)}</tbody></table></div><div className="admin-pagination"><span>{total(products.data)} products · page {page + 1}</span><div><button className="admin-button admin-button-light" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><button className="admin-button admin-button-light" disabled={products.data?.last === true || data.length < 20} onClick={() => setPage(page + 1)}>Next</button></div></div></> : <Empty>No products found.</Empty>}</Card></div>
}

export function AdminPricing() {
  const [q, setQ] = useState(''); const [productId, setProductId] = useState(''); const [retailerId, setRetailerId] = useState(''); const [price, setPrice] = useState(''); const [gstIncluded, setGst] = useState(true); const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false)
  const products = useLoad(() => fetchProducts({ q, page: 0, size: 50 }), [q]); const retailers = useLoad(fetchAdminRetailers)
  const product = rows(products.data).find((p) => p.id === productId)
  const save = async (e, retailerOverride) => { e.preventDefault(); setBusy(true); setMessage(''); try { const body = { pricePerCase: Number(price), currency: 'INR', gstIncluded }; if (retailerOverride) await setRetailerPrice(retailerId, productId, body); else await setDefaultPrice(productId, body); setMessage('Price saved. Refresh the catalogue to see the resolved price.') } catch (err) { setMessage(asMessage(err)) } finally { setBusy(false) } }
  return <div className="admin-workspace"><Header title="Pricing" description="Set default catalogue prices or retailer-specific overrides. All prices use INR." /><div className="admin-two-column"><Card><h2>Price editor</h2><label>Find product<input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search by product name or SKU" /></label><ProductSelect products={rows(products.data)} value={productId} onChange={setProductId} />{product && <div className="admin-current-price"><span>Current resolved price</span><strong>{product.priceAvailable ? money(product.pricePerCase, product.currency) : 'Not set'}</strong><small>Source: {product.priceSource || 'none'} · {product.gstIncluded ? 'GST included' : 'GST excluded/unspecified'}</small></div>}<form className="admin-form" onSubmit={(e) => save(e, false)}><label>Default price per case (INR)<input required min="0" step="0.01" type="number" value={price} onChange={(e) => setPrice(e.target.value)} /></label><label className="admin-checkbox"><input type="checkbox" checked={gstIncluded} onChange={(e) => setGst(e.target.checked)} /> GST included</label><button className="admin-button" disabled={busy || !productId}>Save default price</button></form></Card><Card><h2>Retailer-specific override</h2><form className="admin-form" onSubmit={(e) => save(e, true)}><label>Retailer<select required value={retailerId} onChange={(e) => setRetailerId(e.target.value)}><option value="">Choose assigned retailer</option>{rows(retailers.data).map((r) => <option key={r.id} value={r.id}>{r.businessName}</option>)}</select></label><ProductSelect products={rows(products.data)} value={productId} onChange={setProductId} /><label>Override price per case (INR)<input required min="0" step="0.01" type="number" value={price} onChange={(e) => setPrice(e.target.value)} /></label><label className="admin-checkbox"><input type="checkbox" checked={gstIncluded} onChange={(e) => setGst(e.target.checked)} /> GST included</label><button className="admin-button" disabled={busy || !retailerId || !productId}>Save retailer override</button><button type="button" className="admin-button admin-button-light" disabled={busy || !retailerId || !productId} onClick={async () => { setBusy(true); try { await clearRetailerPrice(retailerId, productId); setMessage('Retailer override cleared; default price will apply.') } catch (e) { setMessage(asMessage(e)) } finally { setBusy(false) } }}>Clear override</button></form></Card></div>{message && <div className="admin-notice" role="status">{message}</div>}<ErrorNote error={products.error || retailers.error} /></div>
}

export function AdminSchemes() {
  const [q, setQ] = useState(''); const [type, setType] = useState('BUY_X_GET_Y_FREE'); const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [productId, setProductId] = useState(''); const [retailerId, setRetailerId] = useState(''); const [buy, setBuy] = useState(''); const [free, setFree] = useState(''); const [minimum, setMinimum] = useState(''); const [discount, setDiscount] = useState(''); const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false); const [editId, setEditId] = useState('')
  const products = useLoad(() => fetchProducts({ q, page: 0, size: 50 }), [q]); const retailers = useLoad(fetchAdminRetailers); const schemes = useLoad(() => fetchAdminSchemes({ active: true }), [])
  const schemeRows = useMemo(() => rows(schemes.data), [schemes.data])
  const reset = () => { setEditId(''); setName(''); setDescription(''); setProductId(''); setRetailerId(''); setBuy(''); setFree(''); setMinimum(''); setDiscount(''); setType('BUY_X_GET_Y_FREE') }
  const submit = async (e) => { e.preventDefault(); setBusy(true); setMessage(''); const body = { schemeType: type, name, description: description || null, productId, retailerId: retailerId || null, buyQuantity: type === 'BUY_X_GET_Y_FREE' ? Number(buy) : null, freeQuantity: type === 'BUY_X_GET_Y_FREE' ? Number(free) : null, minimumQuantity: type === 'SLAB_DISCOUNT' ? Number(minimum) : null, discountPercentage: type === 'SLAB_DISCOUNT' ? Number(discount) : null }; try { if (editId) await updateAdminScheme(editId, body); else await createAdminScheme(body); setMessage(editId ? 'Scheme updated.' : 'Scheme created.'); reset(); schemes.refresh() } catch (e2) { setMessage(asMessage(e2)) } finally { setBusy(false) } }
  const edit = (s) => { setEditId(s.id); setName(s.name); setDescription(s.description || ''); setProductId(s.productId); setRetailerId(s.retailerId || ''); setType(s.schemeType); setBuy(s.buyQuantity || ''); setFree(s.freeQuantity || ''); setMinimum(s.minimumQuantity || ''); setDiscount(s.discountPercentage || '') }
  return <div className="admin-workspace"><Header title="Schemes" description="Create and maintain catalogue offers using the scheme types supported by the backend." /><div className="admin-two-column admin-scheme-layout"><Card><div className="admin-card-heading"><div><h2>Active schemes</h2><p>Backend scheme list and lifecycle status.</p></div><button className="admin-button admin-button-light" onClick={schemes.refresh}>Refresh</button></div><ErrorNote error={schemes.error} />{schemes.loading ? <Loading /> : schemeRows.length ? <div className="admin-scheme-list">{schemeRows.map((s) => <article key={s.id}><div><span className="admin-tag">{s.schemeType.replaceAll('_', ' ')}</span><h3>{s.name}</h3><p>{s.description || 'No description'} · {s.retailerId ? 'Retailer specific' : 'Default scheme'}</p><small>Product {s.productId}</small></div><div className="admin-row-actions"><button className="admin-button admin-button-light" onClick={() => edit(s)}>Edit</button><button className="admin-button admin-button-danger" disabled={busy} onClick={async () => { setBusy(true); try { await deactivateAdminScheme(s.id); setMessage('Scheme deactivated.'); schemes.refresh() } catch (e) { setMessage(asMessage(e)) } finally { setBusy(false) } }}>Deactivate</button></div></article>)}</div> : <Empty>No active schemes found.</Empty>}</Card><Card><h2>{editId ? 'Edit scheme' : 'Create scheme'}</h2><form className="admin-form" onSubmit={submit}><label>Name<input required maxLength="150" value={name} onChange={(e) => setName(e.target.value)} /></label><label>Description<textarea maxLength="500" value={description} onChange={(e) => setDescription(e.target.value)} rows="2" /></label><label>Scheme type<select value={type} onChange={(e) => setType(e.target.value)}><option value="BUY_X_GET_Y_FREE">Buy X get Y free</option><option value="SLAB_DISCOUNT">Slab discount</option></select></label><label>Product<select required value={productId} onChange={(e) => setProductId(e.target.value)}><option value="">Choose a product</option>{rows(products.data).map((p) => <option key={p.id} value={p.id}>{p.name} · {p.sku}</option>)}</select></label><label>Retailer scope<select value={retailerId} onChange={(e) => setRetailerId(e.target.value)}><option value="">All retailers (default)</option>{rows(retailers.data).map((r) => <option key={r.id} value={r.id}>{r.businessName}</option>)}</select></label>{type === 'BUY_X_GET_Y_FREE' ? <div className="admin-form-grid"><label>Buy quantity<input required min="1" type="number" value={buy} onChange={(e) => setBuy(e.target.value)} /></label><label>Free quantity<input required min="1" type="number" value={free} onChange={(e) => setFree(e.target.value)} /></label></div> : <div className="admin-form-grid"><label>Minimum quantity<input required min="1" type="number" value={minimum} onChange={(e) => setMinimum(e.target.value)} /></label><label>Discount (%)<input required min="0.01" max="100" step="0.01" type="number" value={discount} onChange={(e) => setDiscount(e.target.value)} /></label></div>}<div className="admin-row-actions"><button className="admin-button" disabled={busy || !rows(products.data).length}>{editId ? 'Save changes' : 'Create scheme'}</button>{editId && <button type="button" className="admin-button admin-button-light" onClick={reset}>Cancel edit</button>}</div></form><ErrorNote error={products.error || retailers.error} /></Card></div>{message && <div className="admin-notice" role="status">{message}</div>}</div>
}

export function AdminUsers() {
  return <div className="admin-workspace"><Header title="Users & access" description="Account and role administration is not available through the current backend API." /><Card className="admin-limit-card"><div className="admin-limit-icon">i</div><div><h2>User management endpoint unavailable</h2><p>The backend exposes no admin API to list users, invite accounts, edit roles, disable accounts, or reset credentials. This page therefore provides no user mutation controls.</p><p>Retailer to salesperson assignments can be managed from an assigned retailer detail page.</p><Link className="admin-button" to="/admin/retailers">Open retailer assignments</Link></div></Card></div>
}

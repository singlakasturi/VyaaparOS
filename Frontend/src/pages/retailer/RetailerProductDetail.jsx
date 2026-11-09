import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Package, RefreshCw, Tag } from 'lucide-react'
import { Link, useOutletContext, useParams } from 'react-router-dom'
import { fetchProduct } from '../../services/catalog.js'
import './RetailerProductDetail.css'

const money = (amount, currency = 'INR') => amount == null ? 'Price unavailable' : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount))
const date = value => value ? new Date(value).toLocaleString() : null

export default function RetailerProductDetail() {
  const { retailerId } = useOutletContext()
  const { productId } = useParams()
  const [product, setProduct] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [noDistributor, setNoDistributor] = useState(false)
  const load = useCallback(async () => { setLoading(true); setError(''); setNoDistributor(false); try { setProduct(await fetchProduct(productId, retailerId)) } catch (e) { setProduct(null); if (e.status === 403) setNoDistributor(true); else setError(e.message || 'Product details could not be loaded.') } finally { setLoading(false) } }, [productId, retailerId])
  useEffect(() => { load() }, [load])
  return <main className="retailer-product-detail-page"><Link className="retailer-detail-back" to="/retailer"><ArrowLeft size={17}/>Back to catalogue</Link>
    {loading ? <section className="retailer-detail-state"><RefreshCw className="retailer-spin"/>Loading product details…</section> : noDistributor ? <section className="retailer-detail-state"><h1>Connect with a distributor</h1><p>Product details are available after an active distributor relationship is established.</p><Link className="retailer-button retailer-button-primary" to="/retailer/distributor">Find or join a distributor</Link></section> : error ? <section className="retailer-detail-state" role="alert"><h1>Product details unavailable</h1><p>{error}</p><button className="retailer-button retailer-button-outline" onClick={load} type="button">Try again</button></section> : product && <article className="retailer-product-detail"><div className="retailer-detail-icon"><Package size={34}/></div><div className="retailer-detail-content"><span className="retailer-eyebrow">{product.category?.name || 'PRODUCT'}</span><h1>{product.name}</h1><p className="retailer-detail-sku">SKU {product.sku}{product.brand ? ` · ${product.brand}` : ''}</p><p className="retailer-detail-description">{product.description || 'No product description provided.'}</p><div className="retailer-detail-facts"><div><small>Pack size</small><strong>{product.caseDescription || `${product.unitsPerCase} units per case`}</strong></div><div><small>Unit</small><strong>{product.unitQuantity} {product.unitName}</strong></div><div><small>Availability</small><strong>{product.availableCases > 0 ? `${product.availableCases} cases` : 'Currently unavailable'}</strong></div></div><div className="retailer-detail-price"><strong>{product.priceAvailable ? money(product.pricePerCase, product.currency) : 'Price unavailable'}</strong><span>per case</span></div>{product.schemeAvailable && <section className="retailer-detail-offer"><span><Tag size={15}/>Current offer</span><strong>{product.schemeName}</strong><p>{product.schemeDescription || (product.schemeType === 'BUY_X_GET_Y_FREE' ? `Buy ${product.buyQuantity} cases and get ${product.freeQuantity} free.` : `${product.discountPercentage}% off from ${product.minimumQuantity} cases.`)}</p>{(product.schemeStartsAt || product.schemeEndsAt) && <small>{date(product.schemeStartsAt) || 'Now'} – {date(product.schemeEndsAt) || 'Until further notice'}</small>}</section>}</div></article>}
  </main>
}

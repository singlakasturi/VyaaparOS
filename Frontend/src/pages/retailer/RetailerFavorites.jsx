import { useCallback, useEffect, useState } from 'react'
import { Heart, LoaderCircle, ShoppingCart, Trash2 } from 'lucide-react'
import { Link, useOutletContext } from 'react-router-dom'
import { addToCart, fetchCart } from '../../services/cart.js'
import { fetchRetailerFavorites, removeRetailerFavorite } from '../../services/favorites.js'
import { addRetailerCartProduct } from '../../services/retailerOrdering.js'
import './RetailerFavorites.css'

const money = (amount, currency = 'INR') => amount == null ? 'Price unavailable' : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount))

function CurrentScheme({ product }) {
  if (!product.schemeAvailable) return null
  const offer = product.schemeName || (product.schemeType || '').replaceAll('_', ' ')
  const detail = product.discountPercentage != null ? `${product.discountPercentage}% off`
    : product.buyQuantity && product.freeQuantity ? `Buy ${product.buyQuantity}, get ${product.freeQuantity} free` : ''
  return <p className="retailer-favorite-scheme">{offer}{detail ? ` · ${detail}` : ''}</p>
}

export default function RetailerFavorites() {
  const { retailerId, setCartCount } = useOutletContext()
  const [favorites, setFavorites] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [feedback, setFeedback] = useState({})

  const load = useCallback(async () => {
    setLoading(true); setError('')
    setFavorites([]); setFeedback({})
    try { setFavorites(await fetchRetailerFavorites(retailerId) || []) }
    catch (err) { setError(err.message || 'Favorites could not be loaded.') }
    finally { setLoading(false) }
  }, [retailerId])

  useEffect(() => { load() }, [load])

  const remove = async favorite => {
    setBusy(favorite.productId); setError(''); setFeedback(current => ({ ...current, [favorite.productId]: '' }))
    try {
      await removeRetailerFavorite(retailerId, favorite.productId)
      setFavorites(current => current.filter(item => item.productId !== favorite.productId))
    } catch (err) { setFeedback(current => ({ ...current, [favorite.productId]: err.message || 'Favorite could not be removed.' })) }
    finally { setBusy('') }
  }

  const add = async favorite => {
    const product = favorite.product
    setBusy(favorite.productId); setFeedback(current => ({ ...current, [favorite.productId]: '' }))
    try {
      if (!favorite.catalogActive) throw new Error('This product is no longer active in the current catalogue.')
      if (!product.priceAvailable) throw new Error('A current price is unavailable for this product.')
      if (product.availableCases <= 0) throw new Error('This product is currently out of stock.')
      const cart = await fetchCart(retailerId)
      const currentQuantity = (cart?.items || []).filter(item => item.productId === favorite.productId).reduce((sum, item) => sum + Number(item.paidCases || 0), 0)
      const result = await addRetailerCartProduct({ retailerId, product, quantity: 1, currentQuantity, addToCart })
      setCartCount(result.count)
      setFeedback(current => ({ ...current, [favorite.productId]: 'Added to your current cart. Review the cart before placing an order.' }))
    } catch (err) { setFeedback(current => ({ ...current, [favorite.productId]: err.message || 'Product could not be added to your cart.' })) }
    finally { setBusy('') }
  }

  return <div className="retailer-page retailer-favorites-page">
    <header className="retailer-favorites-header"><div><span className="retailer-eyebrow">QUICK ORDERING</span><h1>Favorite products</h1><p>Saved for your current distributor. Availability, pricing, and offers reflect the current catalogue.</p></div><Link className="retailer-button retailer-button-outline" to="/retailer/cart"><ShoppingCart size={17}/>Review cart</Link></header>
    {error && <div className="retailer-inline-error" role="alert"><span>{error}</span><button className="retailer-button retailer-button-outline" type="button" onClick={load}>Retry</button></div>}
    {loading ? <div className="retailer-state"><LoaderCircle className="retailer-favorite-spin"/>Loading favorites…</div>
      : !favorites.length && !error ? <section className="retailer-state-card"><Heart size={24}/><h2>No favorite products yet</h2><p>Save products from the catalogue for quick access here.</p><Link className="retailer-button retailer-button-primary" to="/retailer">Browse catalogue</Link></section>
        : <div className="retailer-favorites-grid">{favorites.map(favorite => {
          const product = favorite.product
          const message = feedback[favorite.productId]
          return <article className="retailer-favorite-card" key={favorite.productId}>
            <div className="retailer-favorite-top"><span className="retailer-favorite-category">{product.category?.name || product.brand || 'Product'}</span><span className={`retailer-favorite-stock ${!favorite.catalogActive || product.availableCases <= 0 ? 'is-unavailable' : ''}`}>{!favorite.catalogActive ? 'Unavailable' : product.availableCases > 0 ? `${product.availableCases} cases available` : 'Out of stock'}</span></div>
            <h2>{product.name}</h2><p className="retailer-favorite-sku">SKU {product.sku || '—'}</p>
            <div className="retailer-favorite-price">{product.priceAvailable ? money(product.pricePerCase, product.currency) : <span>Price unavailable</span>}<small>per case</small></div>
            <CurrentScheme product={product}/>
            {(!favorite.catalogActive || !product.priceAvailable || product.availableCases <= 0) && <p className="retailer-favorite-unavailable-note">{!favorite.catalogActive ? 'This product is no longer active in the current catalogue.' : !product.priceAvailable ? 'No current price is available, so it cannot be added.' : 'Currently out of stock.'}</p>}
            <div className="retailer-favorite-actions"><button className="retailer-button retailer-button-primary" type="button" disabled={busy === favorite.productId || !favorite.catalogActive || !product.priceAvailable || product.availableCases <= 0} onClick={() => add(favorite)}>{busy === favorite.productId ? 'Adding…' : 'Add to cart'}</button><button className="retailer-icon-button" type="button" disabled={busy === favorite.productId} onClick={() => remove(favorite)} aria-label={`Remove ${product.name} from favorites`} title="Remove favorite"><Trash2 size={17}/></button></div>
            {message && <p className={`retailer-favorite-feedback ${/added/i.test(message) ? 'is-success' : 'is-error'}`} role={/added/i.test(message) ? 'status' : 'alert'}>{message}{/added/i.test(message) && <Link to="/retailer/cart">Open cart</Link>}</p>}
          </article>
        })}</div>}
  </div>
}

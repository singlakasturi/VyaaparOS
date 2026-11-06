import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Package,
  Search,
  ShoppingCart,
  Sparkles,
  Plus,
  Minus,
  Check,
  AlertCircle,
  Tag,
  Building2,
  RefreshCw,
  ArrowRight,
  CloudOff,
  Clock,
} from 'lucide-react'
import { useSalesperson } from '../../services/SalespersonProvider'
import { fetchCategories, fetchProducts } from '../../services/catalog'
import { addToCart, fetchCart } from '../../services/cart'
import {
  cacheCatalog,
  getCachedCatalog,
  getDraftsByRetailer,
  saveDraft,
  DRAFT_STATUS,
} from '../../services/db'

export default function SalespersonCatalogue() {
  const {
    selectedRetailer,
    isOnline,
    salespersonId,
    refreshDraftsCount,
    pendingDraftsCount,
  } = useSalesperson()
  const navigate = useNavigate()

  const [categories, setCategories] = useState([])
  const [selectedCategory, setSelectedCategory] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [products, setProducts] = useState([])
  const [cartCount, setCartCount] = useState(0)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [quantities, setQuantities] = useState({})
  const [addingId, setAddingId] = useState(null)
  const [addedToast, setAddedToast] = useState(null)
  const [isCachedCatalogue, setIsCachedCatalogue] = useState(false)
  const [catalogueCachedAt, setCatalogueCachedAt] = useState(null)

  const loadCatalog = useCallback(async () => {
    if (!selectedRetailer) return
    setIsLoading(true)
    setError(null)

    // Offline check: load directly from IndexedDB catalog cache
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      const cached = await getCachedCatalog(selectedRetailer.id)
      if (cached && cached.products && cached.products.length > 0) {
        setProducts(cached.products)
        setCategories(cached.categories || [])
        setIsCachedCatalogue(true)
        setCatalogueCachedAt(cached.cachedAt)
        setIsLoading(false)
        return
      }
      setError('You are offline and no catalogue was cached for this retailer. An initial online connection is required to browse products.')
      setIsLoading(false)
      return
    }

    try {
      const [cats, prods, cart] = await Promise.all([
        fetchCategories(selectedRetailer.id).catch(() => []),
        fetchProducts({
          retailerId: selectedRetailer.id,
          category: selectedCategory || undefined,
          q: searchQuery || undefined,
          size: 100,
        }),
        fetchCart(selectedRetailer.id).catch(() => null),
      ])

      const productList = prods?.content || []
      const categoryList = cats || []

      setCategories(categoryList)
      setProducts(productList)
      setIsCachedCatalogue(false)
      setCatalogueCachedAt(null)

      if (cart?.items) {
        setCartCount(cart.items.reduce((sum, item) => sum + (item.paidCases || 0), 0))
      }

      // Cache products and categories in IndexedDB for offline use
      if (productList.length > 0) {
        await cacheCatalog(selectedRetailer.id, productList, categoryList)
      }
    } catch (err) {
      // Fallback to cache on network failure
      const cached = await getCachedCatalog(selectedRetailer.id)
      if (cached && cached.products && cached.products.length > 0) {
        setProducts(cached.products)
        setCategories(cached.categories || [])
        setIsCachedCatalogue(true)
        setCatalogueCachedAt(cached.cachedAt)
        return
      }
      setError(err?.message || 'Failed to load catalogue.')
    } finally {
      setIsLoading(false)
    }
  }, [selectedRetailer, selectedCategory, searchQuery])

  useEffect(() => {
    loadCatalog()
  }, [loadCatalog])

  const handleQuantityChange = (productId, delta) => {
    setQuantities((prev) => {
      const current = prev[productId] || 1
      const next = Math.max(1, current + delta)
      return { ...prev, [productId]: next }
    })
  }

  const handleAddToCartOrDraft = async (product) => {
    if (!selectedRetailer) return
    const cases = quantities[product.id] || 1
    setAddingId(product.id)

    // OFFLINE WORKFLOW: Save directly to an IndexedDB local draft
    if (!isOnline) {
      try {
        const existingDrafts = await getDraftsByRetailer(selectedRetailer.id, salespersonId)
        // Find existing open draft or create new one
        let targetDraft = existingDrafts.find(
          (d) =>
            d.status === DRAFT_STATUS.SAVED_LOCALLY ||
            d.status === DRAFT_STATUS.WAITING_FOR_CONNECTION ||
            d.status === DRAFT_STATUS.READY_TO_SYNC
        )

        const draftItem = {
          productId: product.id,
          productName: product.name,
          sku: product.sku,
          packSize: product.packSize || product.unit || '',
          paidCases: cases,
          cachedPrice: product.resolvedPrice || product.pricePerCase || product.price || 0,
          cachedScheme: product.activeScheme?.name || null,
        }

        if (targetDraft) {
          const items = [...(targetDraft.items || [])]
          const existingItemIndex = items.findIndex((i) => i.productId === product.id)
          if (existingItemIndex >= 0) {
            items[existingItemIndex].paidCases = cases
          } else {
            items.push(draftItem)
          }
          targetDraft.items = items
          await saveDraft(targetDraft)
        } else {
          targetDraft = {
            id: crypto.randomUUID(),
            salespersonId,
            retailerId: selectedRetailer.id,
            retailerName: selectedRetailer.businessName,
            retailerCity: selectedRetailer.city || '',
            status: DRAFT_STATUS.SAVED_LOCALLY,
            items: [draftItem],
            notes: '',
          }
          await saveDraft(targetDraft)
        }

        await refreshDraftsCount()
        setAddedToast(`Added ${cases} case(s) of ${product.name} to offline draft`)
        setTimeout(() => setAddedToast(null), 3000)
      } catch (err) {
        alert('Failed to save to offline draft: ' + err.message)
      } finally {
        setAddingId(null)
      }
      return
    }

    // ONLINE WORKFLOW: Add to cart
    try {
      const updatedCart = await addToCart(selectedRetailer.id, product.id, cases)
      if (updatedCart?.items) {
        setCartCount(updatedCart.items.reduce((sum, item) => sum + (item.paidCases || 0), 0))
      }
      setAddedToast(`Added ${cases} case(s) of ${product.name} to cart`)
      setTimeout(() => setAddedToast(null), 3000)
    } catch (err) {
      alert(err?.message || 'Failed to add item to cart.')
    } finally {
      setAddingId(null)
    }
  }

  if (!selectedRetailer) {
    return (
      <div className="sp-page">
        <div className="sp-empty card">
          <Building2 size={40} className="sp-empty-icon" />
          <h2>No Retailer Selected</h2>
          <p>Please select an assigned retailer to view their catalogue and retailer-specific pricing.</p>
          <button
            className="btn btn-primary"
            onClick={() => navigate('/salesperson/retailers')}
            type="button"
          >
            Select an Assigned Retailer
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="sp-page">
      {/* Toast Notification */}
      {addedToast && (
        <div className="sp-toast" role="status">
          <Check size={16} />
          <span>{addedToast}</span>
        </div>
      )}

      {/* Offline / Cached Notice Banner */}
      {isCachedCatalogue && (
        <div className="sp-cached-banner-full">
          <Clock size={16} />
          <div className="sp-cached-banner-content">
            <strong>Offline Mode — Cached Catalogue</strong>
            <span>
              Prices and stock shown are reference estimates from cache (last updated:{' '}
              {catalogueCachedAt ? new Date(catalogueCachedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'earlier today'}
              ). Actual prices, schemes, and stock reservation will be confirmed by the server upon synchronization.
            </span>
          </div>
        </div>
      )}

      {/* Header */}
      <div className="sp-header">
        <div className="sp-header-left">
          <div className="sp-icon-wrap">
            <Package size={24} />
          </div>
          <div>
            <div className="sp-title-row">
              <h1 className="sp-title">Product Catalogue</h1>
              <span className="badge">{products.length} Products</span>
            </div>
            <p className="sp-subtitle">
              Ordering for <strong>{selectedRetailer.businessName}</strong> ({selectedRetailer.city || 'Assigned'})
            </p>
          </div>
        </div>

        <div className="sp-header-actions">
          {pendingDraftsCount > 0 && (
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/salesperson/drafts')}
              type="button"
            >
              <CloudOff size={16} />
              Drafts ({pendingDraftsCount})
            </button>
          )}
          {isOnline && (
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/salesperson/ai-assistant')}
              type="button"
            >
              <Sparkles size={16} />
              AI Assistant
            </button>
          )}
          <button
            className="btn btn-primary"
            onClick={() => navigate('/salesperson/cart')}
            type="button"
          >
            <ShoppingCart size={16} />
            {isOnline ? `View Cart (${cartCount})` : 'View Draft Order'}
          </button>
        </div>
      </div>

      {/* Search & Categories */}
      <div className="sp-controls card">
        <div className="sp-search-wrap">
          <Search size={18} className="sp-search-icon" />
          <input
            className="input sp-search-input"
            type="search"
            placeholder="Search products by name or SKU..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            aria-label="Search products"
          />
        </div>

        {categories.length > 0 && (
          <div className="sp-category-pills">
            <button
              className={`sp-cat-pill ${selectedCategory === '' ? 'sp-cat-pill-active' : ''}`}
              onClick={() => setSelectedCategory('')}
              type="button"
            >
              All Categories
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id || cat.slug}
                className={`sp-cat-pill ${selectedCategory === (cat.slug || cat.name) ? 'sp-cat-pill-active' : ''}`}
                onClick={() => setSelectedCategory(cat.slug || cat.name)}
                type="button"
              >
                {cat.name}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="sp-loading card">
          <RefreshCw size={24} className="sp-spinner" />
          <p>Loading catalogue for {selectedRetailer.businessName}...</p>
        </div>
      )}

      {/* Error */}
      {error && !isLoading && (
        <div className="sp-error card">
          <AlertCircle size={24} />
          <p>{error}</p>
          {isOnline && (
            <button className="btn btn-secondary" onClick={loadCatalog} type="button">
              Retry
            </button>
          )}
        </div>
      )}

      {/* Products Grid */}
      {!isLoading && !error && products.length === 0 && (
        <div className="sp-empty card">
          <Package size={40} className="sp-empty-icon" />
          <h3>No products found</h3>
          <p>Try modifying your search or choosing a different category.</p>
        </div>
      )}

      {!isLoading && !error && products.length > 0 && (
        <div className="sp-product-grid">
          {products.map((p) => {
            const qty = quantities[p.id] || 1
            const isAdding = addingId === p.id
            const price = p.resolvedPrice || p.pricePerCase || p.price || 0
            const stock = p.availableCases !== undefined ? p.availableCases : p.stockQuantity

            return (
              <div key={p.id} className="sp-product-card card">
                <div className="sp-card-body">
                  <div className="sp-card-header">
                    <div>
                      <span className="sp-product-sku">{p.sku}</span>
                      <h3 className="sp-product-name">{p.name}</h3>
                      {p.category && (
                        <span className="sp-product-cat">{p.category.name || p.category}</span>
                      )}
                    </div>
                  </div>

                  {/* Pricing info */}
                  <div className="sp-pricing-box">
                    <div className="sp-price-row">
                      <span className="sp-price-label">
                        {isCachedCatalogue ? 'Ref. Price / Case:' : 'Resolved Price / Case:'}
                      </span>
                      <span className="sp-price-val">₹{Number(price).toFixed(2)}</span>
                    </div>

                    {p.activeScheme && (
                      <div className="sp-scheme-tag">
                        <Tag size={12} />
                        <span>{p.activeScheme.name || p.activeScheme.description}</span>
                      </div>
                    )}

                    {/* Stock indicator */}
                    <div className="sp-stock-row">
                      <span className="sp-stock-label">
                        {isCachedCatalogue ? 'Est. Available:' : 'Available:'}
                      </span>
                      <span
                        className={`sp-stock-val ${
                          stock > 10 ? 'in-stock' : stock > 0 ? 'low-stock' : 'out-stock'
                        }`}
                      >
                        {stock !== undefined ? `${stock} cases` : 'Available'}
                        {isCachedCatalogue && ' (est.)'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Footer with quantity stepper and add button */}
                <div className="sp-card-footer">
                  <div className="sp-stepper">
                    <button
                      className="sp-stepper-btn"
                      onClick={() => handleQuantityChange(p.id, -1)}
                      disabled={qty <= 1}
                      type="button"
                      aria-label="Decrease cases"
                    >
                      <Minus size={14} />
                    </button>
                    <span className="sp-stepper-val">{qty}</span>
                    <button
                      className="sp-stepper-btn"
                      onClick={() => handleQuantityChange(p.id, 1)}
                      type="button"
                      aria-label="Increase cases"
                    >
                      <Plus size={14} />
                    </button>
                  </div>

                  <button
                    className={`btn ${isOnline ? 'btn-primary' : 'btn-secondary'} sp-add-btn`}
                    onClick={() => handleAddToCartOrDraft(p)}
                    disabled={isAdding}
                    type="button"
                  >
                    {isAdding ? (
                      <RefreshCw size={14} className="sp-spinner" />
                    ) : (
                      <>
                        <Plus size={14} />
                        {isOnline ? 'Add to Cart' : 'Add to Draft'}
                      </>
                    )}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

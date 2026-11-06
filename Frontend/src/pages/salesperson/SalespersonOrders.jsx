import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileText,
  Building2,
  ArrowRight,
  Package,
  Calendar,
  AlertCircle,
  RefreshCw,
  ChevronDown,
  ShoppingCart,
  UserCheck,
} from 'lucide-react'
import { useSalesperson } from '../../services/SalespersonProvider'
import { useAuth } from '../../services/AuthProvider'
import { fetchOrders } from '../../services/order'

export default function SalespersonOrders() {
  const { selectedRetailer } = useSalesperson()
  const { session } = useAuth()
  const navigate = useNavigate()

  const [orders, setOrders] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [expandedOrderId, setExpandedOrderId] = useState(null)

  const currentUserId = session?.user?.id

  const loadOrders = useCallback(async () => {
    if (!selectedRetailer) return
    setIsLoading(true)
    setError(null)
    try {
      const data = await fetchOrders(selectedRetailer.id, 0, 50)
      setOrders(data?.content || [])
    } catch (err) {
      setError(err?.message || 'Failed to load order history.')
    } finally {
      setIsLoading(false)
    }
  }, [selectedRetailer])

  useEffect(() => {
    loadOrders()
  }, [loadOrders])

  const formatCurrency = (val) => {
    const num = Number(val) || 0
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(num)
  }

  const formatDate = (isoString) => {
    if (!isoString) return ''
    const date = new Date(isoString)
    return date.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  if (!selectedRetailer) {
    return (
      <div className="sp-page">
        <div className="sp-empty card">
          <Building2 size={40} className="sp-empty-icon" />
          <h2>No Retailer Selected</h2>
          <p>Please select an assigned retailer to view their historical orders and tracking details.</p>
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
      {/* Header */}
      <div className="sp-header">
        <div className="sp-header-left">
          <div className="sp-icon-wrap">
            <FileText size={24} />
          </div>
          <div>
            <div className="sp-title-row">
              <h1 className="sp-title">Order History</h1>
              <span className="badge">{orders.length} Orders</span>
            </div>
            <p className="sp-subtitle">
              Orders placed for <strong>{selectedRetailer.businessName}</strong> ({selectedRetailer.city || 'Assigned'})
            </p>
          </div>
        </div>

        <div className="sp-header-actions">
          <button
            className="btn btn-primary"
            onClick={() => navigate('/salesperson/catalogue')}
            type="button"
          >
            <Package size={16} />
            Create New Order
          </button>
        </div>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="sp-loading card">
          <RefreshCw size={24} className="sp-spinner" />
          <p>Loading order history…</p>
        </div>
      )}

      {/* Error */}
      {error && !isLoading && (
        <div className="sp-error card">
          <AlertCircle size={20} className="sp-error-icon" />
          <div>
            <h3 className="sp-error-title">Failed to load orders</h3>
            <p className="sp-error-message">{error}</p>
          </div>
          <button className="btn btn-secondary" onClick={loadOrders} type="button">
            Retry
          </button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !error && orders.length === 0 && (
        <div className="sp-empty card">
          <ShoppingCart size={40} className="sp-empty-icon" />
          <h2>No Orders Yet</h2>
          <p>No orders have been placed for {selectedRetailer.businessName} yet.</p>
          <button
            className="btn btn-primary"
            onClick={() => navigate('/salesperson/catalogue')}
            type="button"
          >
            <Package size={16} />
            Start First Order
          </button>
        </div>
      )}

      {/* Order Cards List */}
      {!isLoading && orders.length > 0 && (
        <div className="sp-orders-list">
          {orders.map((order) => {
            const isExpanded = expandedOrderId === order.id
            const isPlacedByMe = order.createdBy === currentUserId
            const statusClass =
              order.status === 'SUBMITTED' || order.status === 'APPROVED'
                ? 'sp-badge-active'
                : order.status === 'CREDIT_HOLD'
                ? 'sp-badge-pending'
                : 'sp-badge-neutral'

            return (
              <div key={order.id} className="sp-order-card card">
                <div
                  className="sp-order-header"
                  onClick={() => setExpandedOrderId(isExpanded ? null : order.id)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      setExpandedOrderId(isExpanded ? null : order.id)
                    }
                  }}
                  aria-expanded={isExpanded}
                >
                  <div className="sp-order-meta">
                    <div className="sp-order-title-line">
                      <span className="sp-order-ref">{order.orderNumber}</span>
                      <span className={`sp-status-badge ${statusClass}`}>
                        {order.status}
                      </span>
                      {isPlacedByMe && (
                        <span className="sp-placed-badge">
                          <UserCheck size={12} />
                          Field Sales Order
                        </span>
                      )}
                    </div>
                    <div className="sp-order-subline">
                      <span><Calendar size={13} /> {formatDate(order.createdAt)}</span>
                      <span>•</span>
                      <span>{order.items?.length || 0} line item(s)</span>
                    </div>
                  </div>

                  <div className="sp-order-right">
                    <span className="sp-order-amount">{formatCurrency(order.finalAmount)}</span>
                    <ChevronDown
                      size={18}
                      className={`sp-chevron ${isExpanded ? 'sp-chevron-rotated' : ''}`}
                    />
                  </div>
                </div>

                {/* Credit Hold Reason if applicable */}
                {order.creditHoldReason && (
                  <div className="sp-order-hold-banner">
                    <AlertCircle size={14} />
                    <span>{order.creditHoldReason}</span>
                  </div>
                )}

                {/* Order Notes */}
                {order.notes && (
                  <div className="sp-order-notes-preview">
                    <strong>Notes:</strong> {order.notes}
                  </div>
                )}

                {/* Expandable Line Items */}
                {isExpanded && order.items && order.items.length > 0 && (
                  <div className="sp-order-items-table-wrap">
                    <table className="sp-order-table">
                      <thead>
                        <tr>
                          <th>Product</th>
                          <th>SKU</th>
                          <th>Paid Cases</th>
                          <th>Free Cases</th>
                          <th>Price / Case</th>
                          <th>Total Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {order.items.map((item) => (
                          <tr key={item.id}>
                            <td>
                              <div className="sp-table-prod-name">{item.productName}</div>
                              {item.schemeName && (
                                <span className="ai-badge-scheme">{item.schemeName}</span>
                              )}
                            </td>
                            <td className="sp-table-sku">{item.productSku}</td>
                            <td>{item.paidCases}</td>
                            <td>{item.freeCases || 0}</td>
                            <td>₹{Number(item.unitPricePerCase).toFixed(2)}</td>
                            <td><strong>{formatCurrency(item.finalAmount)}</strong></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

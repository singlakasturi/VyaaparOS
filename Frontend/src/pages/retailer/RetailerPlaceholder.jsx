import { Link } from 'react-router-dom'
import { Sparkles, ArrowRight, Package } from 'lucide-react'

function RetailerPlaceholder() {
  return (
    <div className="retailer-dashboard-placeholder">
      <div className="card ai-promo-banner">
        <div className="ai-promo-content">
          <div className="ai-promo-icon">
            <Sparkles size={24} />
          </div>
          <div>
            <h2>Order with Gemini AI Assistant</h2>
            <p>
              Just tell us what you need in Hindi, English, or Hinglish.
              We'll parse your order and prepare your cart in seconds.
            </p>
          </div>
        </div>
        <Link to="/retailer/ai-assistant" className="btn btn-primary ai-promo-btn">
          Try AI Order Assistant
          <ArrowRight size={18} />
        </Link>
      </div>

      <div className="card placeholder-section">
        <div className="placeholder-icon">
          <Package size={32} />
        </div>
        <h1>Retailer Catalogue &amp; Orders</h1>
        <p>Browse products, manage carts, and view order history.</p>
        <div className="placeholder-actions">
          <Link to="/retailer/ai-assistant" className="btn btn-secondary">
            <Sparkles size={16} />
            Quick Order with AI
          </Link>
        </div>
      </div>
    </div>
  )
}

export default RetailerPlaceholder

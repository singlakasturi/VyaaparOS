import RetailerLayout from '../layouts/RetailerLayout.jsx'
import RetailerOrdering, { RetailerAccount } from '../pages/retailer/RetailerOrdering.jsx'
import RetailerRelationships from '../pages/retailer/RetailerRelationships.jsx'

export const retailerRoutes = {
  path: '/retailer',
  element: <RetailerLayout />,
  children: [
    { index: true, element: <RetailerOrdering view="catalogue" /> },
    { path: 'catalogue', element: <RetailerOrdering view="catalogue" /> },
    { path: 'products/:productId', lazy: async () => ({ Component: (await import('../pages/retailer/RetailerProductDetail.jsx')).default }) },
    { path: 'cart', element: <RetailerOrdering view="cart" /> },
    { path: 'checkout', element: <RetailerOrdering view="checkout" /> },
    { path: 'orders', element: <RetailerOrdering view="orders" /> },
    { path: 'orders/:orderId', element: <RetailerOrdering view="detail" /> },
    { path: 'favorites', lazy: async () => ({ Component: (await import('../pages/retailer/RetailerFavorites.jsx')).default }) },
    { path: 'quick-order', lazy: async () => ({ Component: (await import('../pages/retailer/RetailerQuickOrder.jsx')).default }) },
    { path: 'statement', lazy: async () => ({ Component: (await import('../pages/retailer/RetailerStatement.jsx')).default }) },
    { path: 'account', element: <RetailerAccount /> },
    { path: 'distributor', element: <RetailerRelationships /> },
    { path: 'schemes', lazy: async () => ({ Component: (await import('../pages/retailer/RetailerSchemes.jsx')).default }) },
    { path: 'ai-assistant', lazy: async () => ({ Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default }) },
    { path: 'assistant', lazy: async () => ({ Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default }) },
  ],
}

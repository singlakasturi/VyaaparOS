import RetailerLayout from '../layouts/RetailerLayout.jsx'
import RetailerOrdering, { RetailerAccount } from '../pages/retailer/RetailerOrdering.jsx'

export const retailerRoutes = {
  path: '/retailer',
  element: <RetailerLayout />,
  children: [
    { index: true, element: <RetailerOrdering view="catalogue" /> },
    { path: 'catalogue', element: <RetailerOrdering view="catalogue" /> },
    { path: 'cart', element: <RetailerOrdering view="cart" /> },
    { path: 'checkout', element: <RetailerOrdering view="checkout" /> },
    { path: 'orders', element: <RetailerOrdering view="orders" /> },
    { path: 'orders/:orderId', element: <RetailerOrdering view="detail" /> },
    { path: 'account', element: <RetailerAccount /> },
    { path: 'ai-assistant', lazy: async () => ({ Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default }) },
    { path: 'assistant', lazy: async () => ({ Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default }) },
  ],
}

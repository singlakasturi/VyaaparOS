import OrderDeskLayout from '../layouts/OrderDeskLayout.jsx'

export const orderDeskRoutes = {
  path: '/order-desk',
  element: <OrderDeskLayout />,
  children: [
    { path: 'business', lazy: async () => ({ Component: (await import('../pages/distributor/DistributorBusinessWorkspace.jsx')).default }) },
    { path: 'retailers', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorRetailerRelationships.jsx')).default }) },
    { path: 'catalog', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorCatalog.jsx')).default }) },
    { path: 'inventory', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorInventory.jsx')).default }) },
    { path: 'reports', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorReports.jsx')).default }) },
    { path: 'pricing', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorPricing.jsx')).default }) },
    { path: 'schemes', lazy: async () => ({ Component: (await import('../pages/orderDesk/DistributorSchemes.jsx')).default }) },
    { path: 'ai-assistant', lazy: async () => ({ Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default }) },
    { index: true, lazy: async () => ({ Component: (await import('../pages/orderDesk/OrderDeskQueue.jsx')).default }) },
    { path: 'orders/:orderId', lazy: async () => ({ Component: (await import('../pages/orderDesk/OrderDeskOrderDetails.jsx')).default }) },
    { path: 'exports', lazy: async () => ({ Component: (await import('../pages/orderDesk/TallyExports.jsx')).default }) },
    { path: 'exports/:batchId', lazy: async () => ({ Component: (await import('../pages/orderDesk/TallyExportDetails.jsx')).default }) },
  ],
}

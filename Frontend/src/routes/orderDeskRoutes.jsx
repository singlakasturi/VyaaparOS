import OrderDeskLayout from '../layouts/OrderDeskLayout.jsx'

export const orderDeskRoutes = {
  path: '/order-desk',
  element: <OrderDeskLayout />,
  children: [
    { index: true, lazy: async () => ({ Component: (await import('../pages/orderDesk/OrderDeskQueue.jsx')).default }) },
    { path: 'orders/:orderId', lazy: async () => ({ Component: (await import('../pages/orderDesk/OrderDeskOrderDetails.jsx')).default }) },
    { path: 'exports', lazy: async () => ({ Component: (await import('../pages/orderDesk/TallyExports.jsx')).default }) },
    { path: 'exports/:batchId', lazy: async () => ({ Component: (await import('../pages/orderDesk/TallyExportDetails.jsx')).default }) },
  ],
}

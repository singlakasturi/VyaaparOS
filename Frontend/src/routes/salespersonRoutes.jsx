import SalespersonLayout from '../layouts/SalespersonLayout.jsx'
import { RoleProtectedRoute } from './RouteGuards.jsx'

export const salespersonRoutes = {
  path: '/salesperson',
  element: (
    <RoleProtectedRoute allowedRoles={['SALESPERSON', 'ADMIN', 'ORDER_DESK']} fallbackPath="/retailer" />
  ),
  children: [
    {
      element: <SalespersonLayout />,
      children: [
        {
          index: true,
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonRetailers.jsx')).default,
          }),
        },
        {
          path: 'retailers',
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonRetailers.jsx')).default,
          }),
        },
        {
          path: 'catalogue',
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonCatalogue.jsx')).default,
          }),
        },
        {
          path: 'ai-assistant',
          lazy: async () => ({
            Component: (await import('../pages/retailer/AiOrderAssistant.jsx')).default,
          }),
        },
        {
          path: 'cart',
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonCart.jsx')).default,
          }),
        },
        {
          path: 'drafts',
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonDrafts.jsx')).default,
          }),
        },
        {
          path: 'orders',
          lazy: async () => ({
            Component: (await import('../pages/salesperson/SalespersonOrders.jsx')).default,
          }),
        },
      ],
    },
  ],
}

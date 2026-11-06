import { createBrowserRouter } from 'react-router-dom'
import { NotFound } from '../pages/NotFound.jsx'
import { adminRoutes } from './adminRoutes'
import { authRoutes } from './authRoutes'
import { retailerRoutes } from './retailerRoutes'
import { salespersonRoutes } from './salespersonRoutes'
import { ProtectedRoute, PublicOnlyRoute, RoleProtectedRoute, RootRedirect } from './RouteGuards'
import { orderDeskRoutes } from './orderDeskRoutes.jsx'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootRedirect />,
  },
  {
    element: <PublicOnlyRoute />,
    children: [authRoutes],
  },
  {
    element: <ProtectedRoute />,
    children: [
      retailerRoutes,
      salespersonRoutes,
      {
        element: <RoleProtectedRoute allowedRoles={['ADMIN']} fallbackPath="/retailer" />,
        children: [adminRoutes],
      },
      {
        element: <RoleProtectedRoute allowedRoles={['ORDER_DESK', 'ADMIN']} fallbackPath="/retailer" />,
        children: [orderDeskRoutes],
      },
    ],
  },
  {
    path: '*',
    element: <NotFound />,
  },
])

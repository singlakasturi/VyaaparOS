import { createBrowserRouter } from 'react-router-dom'
import { NotFound } from '../pages/NotFound.jsx'
import { adminRoutes } from './adminRoutes'
import { authRoutes } from './authRoutes'
import { retailerRoutes } from './retailerRoutes'
import { salespersonRoutes } from './salespersonRoutes'
import { ProtectedRoute, PublicOnlyRoute, RoleProtectedRoute, RootRedirect } from './RouteGuards'
import { orderDeskRoutes } from './orderDeskRoutes.jsx'
import DistributorAccessRoute from './DistributorAccessRoute.jsx'
import WorkspaceHome from '../pages/WorkspaceHome.jsx'
import AcceptRelationshipInvitation from '../pages/retailer/AcceptRelationshipInvitation.jsx'

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
      { path: '/workspace', element: <WorkspaceHome /> },
      { path: '/retailer/invitations/:token', element: <AcceptRelationshipInvitation /> },
      retailerRoutes,
      salespersonRoutes,
      {
        element: <RoleProtectedRoute allowedRoles={['ADMIN']} fallbackPath="/retailer" />,
        children: [adminRoutes],
      },
      {
        element: <DistributorAccessRoute />,
        children: [orderDeskRoutes],
      },
    ],
  },
  {
    path: '*',
    element: <NotFound />,
  },
])

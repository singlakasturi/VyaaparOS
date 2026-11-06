import AdminLayout from '../layouts/AdminLayout.jsx'

export const adminRoutes = {
  path: '/admin',
  element: <AdminLayout />,
  children: [
    { index: true, lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminDashboard }) },
    { path: 'retailers', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminRetailers }) },
    { path: 'retailers/:retailerId', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminRetailers }) },
    { path: 'products', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminProducts }) },
    { path: 'pricing', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminPricing }) },
    { path: 'schemes', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminSchemes }) },
    { path: 'users', lazy: async () => ({ Component: (await import('../pages/admin/AdminWorkspace.jsx')).AdminUsers }) },
  ],
}

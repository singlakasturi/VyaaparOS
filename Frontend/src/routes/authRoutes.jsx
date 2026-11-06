import AuthLayout from '../layouts/AuthLayout.jsx'

export const authRoutes = {
  path: '/auth',
  element: <AuthLayout />,
  children: [
    {
      index: true,
      lazy: async () => ({
        Component: (await import('../pages/auth/AuthPlaceholder.jsx')).default,
      }),
    },
  ],
}

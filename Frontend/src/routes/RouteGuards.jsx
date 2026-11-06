import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../services/AuthProvider'

export function ProtectedRoute() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate replace state={{ from: location }} to="/auth" />
  }

  return <Outlet />
}

export function RoleProtectedRoute({ allowedRoles = [], fallbackPath }) {
  const { isAuthenticated, session } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate replace state={{ from: location }} to="/auth" />
  }

  const userRoles = session?.user?.roles || []
  const hasAllowedRole = allowedRoles.length === 0 || userRoles.some((role) => allowedRoles.includes(role))

  if (!hasAllowedRole) {
    const role = userRoles[0]
    const defaultFallback = fallbackPath || (role === 'SALESPERSON' ? '/salesperson' : role === 'ADMIN' ? '/admin' : '/retailer')
    return <Navigate replace to={defaultFallback} />
  }

  return <Outlet />
}

export function PublicOnlyRoute() {
  const { isAuthenticated, session } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) return <Outlet />

  const requestedPath = location.state?.from?.pathname || '/retailer'
  const requestedSearch = location.state?.from?.search || ''
  const role = session?.user?.roles?.[0]
  const fallback = role === 'ADMIN' ? '/admin' : role === 'ORDER_DESK' ? '/order-desk' : role === 'SALESPERSON' ? '/salesperson' : '/retailer'
  const destination = location.state?.from ? `${requestedPath}${requestedSearch}` : fallback

  return <Navigate replace to={destination} />
}

export function RootRedirect() {
  const { isAuthenticated, session } = useAuth()
  if (!isAuthenticated) {
    return <Navigate replace to="/auth" />
  }
  const role = session?.user?.roles?.[0]
  if (role === 'SALESPERSON') {
    return <Navigate replace to="/salesperson" />
  }
  if (role === 'ADMIN' || role === 'ORDER_DESK') {
    return <Navigate replace to={role === 'ORDER_DESK' ? '/order-desk' : '/admin'} />
  }
  return <Navigate replace to="/retailer" />
}

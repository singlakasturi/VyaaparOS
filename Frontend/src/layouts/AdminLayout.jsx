import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import {
  MobileBottomNavigation,
  MobileHeader,
  SidebarNavigation,
} from '../components/common/Navigation'
import { adminNavigation } from '../components/common/navigationConfig'
import { useAuth } from '../services/AuthProvider'

function AdminLayout() {
  const [isNavigationOpen, setNavigationOpen] = useState(false)
  const { logout } = useAuth()

  return (
    <div className="app-layout">
      <SidebarNavigation
        isOpen={isNavigationOpen}
        items={adminNavigation}
        title="Administration"
        onSignOut={logout}
      />
      <div className="app-main">
        <MobileHeader
          title="administration"
          onMenuClick={() => setNavigationOpen((isOpen) => !isOpen)}
        />
        <main className="app-content">
          <Outlet />
        </main>
      </div>
      <MobileBottomNavigation items={adminNavigation} />
    </div>
  )
}

export default AdminLayout

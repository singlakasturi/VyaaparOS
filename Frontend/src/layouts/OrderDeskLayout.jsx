import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import {
  MobileBottomNavigation,
  MobileHeader,
  SidebarNavigation,
} from '../components/common/Navigation'
import { orderDeskNavigation } from '../components/common/navigationConfig'
import { useAuth } from '../services/AuthProvider'
import './OrderDesk.css'

export default function OrderDeskLayout() {
  const [isNavigationOpen, setNavigationOpen] = useState(false)
  const { logout, session } = useAuth()
  const isAdmin = session?.user?.roles?.includes('ADMIN')

  return (
    <div className="app-layout order-desk-layout">
      <SidebarNavigation
        isOpen={isNavigationOpen}
        items={orderDeskNavigation}
        title={isAdmin ? 'Order desk · Admin' : 'Order desk'}
        onSignOut={logout}
      />
      <div className="app-main">
        <MobileHeader title="order desk" onMenuClick={() => setNavigationOpen((open) => !open)} />
        <main className="app-content order-desk-content"><Outlet /></main>
      </div>
      <MobileBottomNavigation items={orderDeskNavigation} />
    </div>
  )
}

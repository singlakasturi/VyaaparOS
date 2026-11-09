import { useState } from 'react'
import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import {
  MobileBottomNavigation,
  MobileHeader,
  SidebarNavigation,
} from '../components/common/Navigation'
import { orderDeskNavigation } from '../components/common/navigationConfig'
import { useAuth } from '../services/AuthProvider'
import './OrderDesk.css'
import { useDistributorWorkspace } from '../routes/DistributorAccessRoute.jsx'
import { fetchDistributorRetailers } from '../services/relationships.js'

export default function OrderDeskLayout() {
  const [isNavigationOpen, setNavigationOpen] = useState(false)
  const { logout } = useAuth()
  const { business } = useDistributorWorkspace()
  const [retailers, setRetailers] = useState([])
  const [retailerId, setRetailerId] = useState('')
  useEffect(() => { fetchDistributorRetailers().then(setRetailers).catch(() => setRetailers([])) }, [])

  return (
    <div className="app-layout order-desk-layout">
      <SidebarNavigation
        isOpen={isNavigationOpen}
        items={orderDeskNavigation}
        title={business?.businessName || 'Order Desk'}
        onSignOut={logout}
      />
      <div className="app-main">
        <MobileHeader title={business?.businessName || 'Order Desk'} onMenuClick={() => setNavigationOpen((open) => !open)} />
        <main className="app-content order-desk-content"><Outlet context={{ retailerId, setRetailerId, retailers }} /></main>
      </div>
      <MobileBottomNavigation items={orderDeskNavigation} />
    </div>
  )
}

import { FileText, Package, ShoppingCart, Sparkles, Store, Users, CloudOff, UserRound, ClipboardCheck, Tags, CircleDollarSign } from 'lucide-react'

export const retailerNavigation = [
  { label: 'Catalogue', to: '/retailer', icon: Package, end: true },
  { label: 'Cart', to: '/retailer/cart', icon: ShoppingCart, end: false },
  { label: 'Orders', to: '/retailer/orders', icon: FileText, end: false },
  { label: 'Account', to: '/retailer/account', icon: UserRound },
]

export const salespersonNavigation = [
  { label: 'Retailers', to: '/salesperson/retailers', icon: Store },
  { label: 'Catalogue', to: '/salesperson/catalogue', icon: Package },
  { label: 'AI Assistant', to: '/salesperson/ai-assistant', icon: Sparkles },
  { label: 'Cart', to: '/salesperson/cart', icon: ShoppingCart },
  { label: 'Offline Drafts', to: '/salesperson/drafts', icon: CloudOff },
  { label: 'Orders', to: '/salesperson/orders', icon: FileText },
]

export const adminNavigation = [
  { label: 'Overview', to: '/admin', icon: Package, end: true },
  { label: 'Retailers', to: '/admin/retailers', icon: Store },
  { label: 'Products & stock', to: '/admin/products', icon: Package },
  { label: 'Pricing', to: '/admin/pricing', icon: CircleDollarSign },
  { label: 'Schemes', to: '/admin/schemes', icon: Tags },
  { label: 'Users & access', to: '/admin/users', icon: Users },
  { label: 'Order desk', to: '/order-desk', icon: ClipboardCheck },
]

export const orderDeskNavigation = [
  { label: 'Order queue', to: '/order-desk', icon: ClipboardCheck, end: true },
  { label: 'Tally exports', to: '/order-desk/exports', icon: FileText, end: false },
]

import { FileText, Package, ShoppingCart, Sparkles, Store, Users, CloudOff, UserRound, ClipboardCheck, Tags, CircleDollarSign, Boxes, ChartNoAxesCombined, Heart, ListPlus } from 'lucide-react'

export const retailerNavigation = [
  { label: 'Switch workspace', to: '/workspace', icon: Store },
  { label: 'Catalogue', to: '/retailer', icon: Package, end: true },
  { label: 'Cart', to: '/retailer/cart', icon: ShoppingCart, end: false },
  { label: 'Orders', to: '/retailer/orders', icon: FileText, end: false },
  { label: 'Favorites', to: '/retailer/favorites', icon: Heart, end: false },
  { label: 'Quick Order', to: '/retailer/quick-order', icon: ListPlus, end: false },
  { label: 'Text order capture', to: '/retailer/ai-assistant', icon: Sparkles, end: false },
  { label: 'Statement', to: '/retailer/statement', icon: FileText, end: false },
  { label: 'Distributor', to: '/retailer/distributor', icon: Users },
  { label: 'Promotions', to: '/retailer/schemes', icon: Tags },
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
  { label: 'Switch workspace', to: '/workspace', icon: Store },
  { label: 'Business & access', to: '/order-desk/business', icon: Users, end: true },
  { label: 'Retailers', to: '/order-desk/retailers', icon: Store, end: true },
  { label: 'Catalog', to: '/order-desk/catalog', icon: Package, end: true },
  { label: 'Inventory', to: '/order-desk/inventory', icon: Boxes, end: true },
  { label: 'Reports', to: '/order-desk/reports', icon: ChartNoAxesCombined, end: true },
  { label: 'Pricing', to: '/order-desk/pricing', icon: CircleDollarSign, end: true },
  { label: 'Schemes', to: '/order-desk/schemes', icon: Tags, end: true },
  { label: 'Text order capture', to: '/order-desk/ai-assistant', icon: Sparkles, end: false },
  { label: 'Order queue', to: '/order-desk', icon: ClipboardCheck, end: true },
  { label: 'Tally exports', to: '/order-desk/exports', icon: FileText, end: false },
]

import { NavLink } from 'react-router-dom'
import { LogOut, Menu } from 'lucide-react'
import NotificationBell from './NotificationBell.jsx'

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">V</span>
      <span>VyapaarOS</span>
    </div>
  )
}

function NavigationLink({ item, mobile = false }) {
  const Icon = item.icon

  return (
    <NavLink
      className={({ isActive }) =>
        `navigation-link${isActive ? ' navigation-link-active' : ''}${
          mobile ? ' navigation-link-mobile' : ''
        }`
      }
      to={item.to}
      end={item.end ?? true}
    >
      <Icon aria-hidden="true" size={mobile ? 20 : 18} strokeWidth={1.8} />
      <span>{item.label}</span>
    </NavLink>
  )
}

export function SidebarNavigation({ items, title = 'Workspace', isOpen = false, onSignOut }) {
  return (
    <aside className={`app-sidebar${isOpen ? ' app-sidebar-open' : ''}`}>
      <Brand />
      <div className="sidebar-notifications"><NotificationBell /></div>
      <p className="navigation-title">{title}</p>
      <nav className="navigation-list" aria-label={`${title} navigation`}>
        {items.map((item) => (
          <NavigationLink item={item} key={item.to} />
        ))}
      </nav>
      <button className="navigation-sign-out" onClick={onSignOut} type="button">
        <LogOut aria-hidden="true" size={18} />
        Sign out
      </button>
    </aside>
  )
}

export function MobileBottomNavigation({ items }) {
  return (
    <nav className="mobile-bottom-navigation" aria-label="Mobile navigation; scroll horizontally for more sections">
      {items.map((item) => (
        <NavigationLink item={item} key={item.to} mobile />
      ))}
    </nav>
  )
}

export function MobileHeader({ title, onMenuClick }) {
  return (
    <header className="mobile-header">
      <Brand />
      <NotificationBell />
      <button
        aria-label={`Open ${title} navigation`}
        className="navigation-menu-button"
        onClick={onMenuClick}
        type="button"
      >
        <Menu aria-hidden="true" size={22} />
      </button>
    </header>
  )
}

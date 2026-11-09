import { useCallback, useEffect, useState } from 'react'
import { Bell, Check, LoaderCircle } from 'lucide-react'
import { fetchNotifications, fetchUnreadNotificationCount, markAllNotificationsRead, markNotificationRead } from '../../services/notifications.js'
import './notifications.css'

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [count, setCount] = useState(0)
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const refreshCount = useCallback(async () => {
    try { setCount(Number(await fetchUnreadNotificationCount()) || 0) } catch { /* workspace may not allow notifications */ }
  }, [])
  useEffect(() => { refreshCount() }, [refreshCount])
  const load = async () => {
    setLoading(true); setError('')
    try {
      const [page] = await Promise.all([fetchNotifications(), refreshCount()])
      setItems(page?.content || [])
    } catch (err) { setError(err.message || 'Notifications are unavailable.') }
    finally { setLoading(false) }
  }
  const toggle = () => { const next = !open; setOpen(next); if (next) load() }
  const markRead = async (item) => {
    try { await markNotificationRead(item.id); setItems((rows) => rows.map((row) => row.id === item.id ? { ...row, readAt: new Date().toISOString() } : row)); await refreshCount() }
    catch (err) { setError(err.message || 'Could not update this notification.') }
  }
  const markAll = async () => {
    try { await markAllNotificationsRead(); setItems((rows) => rows.map((row) => ({ ...row, readAt: row.readAt || new Date().toISOString() }))); setCount(0) }
    catch (err) { setError(err.message || 'Could not update notifications.') }
  }
  return <div className="notification-center">
    <button className="notification-trigger" type="button" aria-label={`Notifications${count ? `, ${count} unread` : ''}`} aria-expanded={open} onClick={toggle}>
      <Bell size={19} aria-hidden="true" />{count > 0 && <span className="notification-count">{count > 99 ? '99+' : count}</span>}
    </button>
    {open && <section className="notification-popover" aria-label="Notifications">
      <header><strong>Notifications</strong><button type="button" onClick={markAll} disabled={!count}>Mark all read</button></header>
      {loading ? <div className="notification-state"><LoaderCircle className="notification-spin" size={18} />Loading…</div>
        : error ? <div className="notification-state notification-error" role="alert">{error}<button type="button" onClick={load}>Retry</button></div>
          : !items.length ? <div className="notification-state">You’re all caught up.</div>
            : <ul>{items.map((item) => <li key={item.id} className={item.readAt ? '' : 'notification-unread'}>
              <div><strong>{item.title}</strong><p>{item.message}</p><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()}</time></div>
              {!item.readAt && <button type="button" aria-label={`Mark ${item.title} as read`} onClick={() => markRead(item)}><Check size={16} /></button>}
            </li>)}</ul>}
    </section>}
  </div>
}

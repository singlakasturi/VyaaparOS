import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { AlertCircle, LoaderCircle, RefreshCw } from 'lucide-react'
import { ApiError } from '../services/api.js'
import { fetchCurrentDistributor } from '../services/distributor.js'
import { useAuth } from '../services/AuthProvider'

const DistributorWorkspaceContext = createContext(null)

export function useDistributorWorkspace() {
  const value = useContext(DistributorWorkspaceContext)
  if (!value) throw new Error('useDistributorWorkspace must be used inside DistributorAccessRoute')
  return value
}

export default function DistributorAccessRoute() {
  const { session } = useAuth()
  const [business, setBusiness] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [denied, setDenied] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    setDenied(false)
    try {
      setBusiness(await fetchCurrentDistributor())
    } catch (err) {
      setBusiness(null)
      if (err instanceof ApiError && (err.status === 403 || err.status === 404)) setDenied(true)
      else setError(err.message || 'Could not load your distributor workspace.')
    } finally {
      setLoading(false)
    }
  }, [session?.user?.id])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    const handleAccessDenied = (event) => {
      if (event.detail?.path?.startsWith('/api/order-desk')
        || event.detail?.path?.startsWith('/api/distributor')) {
        setDenied(true)
        setBusiness(null)
        setLoading(false)
      }
    }
    window.addEventListener('vyapaaros:access-denied', handleAccessDenied)
    return () => window.removeEventListener('vyapaaros:access-denied', handleAccessDenied)
  }, [])

  if (loading) return <div className="distributor-route-state"><LoaderCircle className="distributor-spin" aria-hidden="true" />Checking distributor access…</div>
  if (denied) return <main className="distributor-route-state" role="alert"><AlertCircle aria-hidden="true" /><div><h1>Distributor access unavailable</h1><p>Your active distributor membership may have ended. Switch workspace or sign in again if your access should still be active.</p><Link to="/workspace">Choose a workspace</Link></div></main>
  if (error) return <main className="distributor-route-state" role="alert"><AlertCircle aria-hidden="true" /><div><h1>Workspace couldn’t load</h1><p>{error}</p><button type="button" onClick={load}><RefreshCw size={16} aria-hidden="true" /> Try again</button><Link to="/workspace">Choose another workspace</Link></div></main>

  return <DistributorWorkspaceContext.Provider value={{ business, setBusiness, reloadBusiness: load }}><Outlet /></DistributorWorkspaceContext.Provider>
}

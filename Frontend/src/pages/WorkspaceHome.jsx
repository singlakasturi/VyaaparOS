import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ArrowRight, Building2, LoaderCircle, RefreshCw, Store } from 'lucide-react'
import { ApiError } from '../services/api.js'
import { fetchCurrentDistributor, getWorkspaceAvailability } from '../services/distributor.js'
import { fetchMyRetailers } from '../services/retailer.js'
import { useAuth } from '../services/AuthProvider'
import './workspace.css'

export default function WorkspaceHome() {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [business, setBusiness] = useState(null)
  const [retailers, setRetailers] = useState([])
  const location = useLocation()
  const { logout } = useAuth()

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const [distributorResult, retailerResult] = await Promise.allSettled([
      fetchCurrentDistributor(), fetchMyRetailers(),
    ])
    if (distributorResult.status === 'fulfilled') setBusiness(distributorResult.value)
    else if (!(distributorResult.reason instanceof ApiError)
      || ![403, 404].includes(distributorResult.reason.status)) {
      setError(distributorResult.reason?.message || 'Could not check distributor access.')
    }
    if (retailerResult.status === 'fulfilled') setRetailers(Array.isArray(retailerResult.value) ? retailerResult.value : [])
    else if (!(retailerResult.reason instanceof ApiError) || retailerResult.reason.status !== 403) {
      setError((current) => current || retailerResult.reason?.message || 'Could not check retailer access.')
    }
    setLoading(false)
  }, [])

  useEffect(() => { load() }, [load])
  const availability = getWorkspaceAvailability({ distributor: business, retailers })

  return (
    <main className="workspace-home">
      <header className="workspace-home-header"><span className="workspace-eyebrow">VYAPAAROS</span><h1>Choose your workspace</h1><p>Open a workspace connected to your account.</p><button className="workspace-sign-out" onClick={logout} type="button">Sign out</button></header>
      {location.state?.notice && <p className="workspace-notice" role="status">{location.state.notice}</p>}
      {loading ? <div className="workspace-state"><LoaderCircle className="distributor-spin" aria-hidden="true" />Checking your access…</div>
        : error ? <div className="workspace-state workspace-state-error" role="alert"><p>{error}</p><button type="button" onClick={load}><RefreshCw size={16} aria-hidden="true" /> Try again</button></div>
          : availability.empty ? <div className="workspace-state"><h2>No workspace is linked to this account</h2><p>Ask a business owner to add this account to their Order Desk, or sign in with another account.</p></div>
            : <div className="workspace-card-grid">
              {availability.distributor && <Link className="workspace-choice" to="/order-desk/business"><span className="workspace-choice-icon"><Building2 size={22} aria-hidden="true" /></span><span className="workspace-choice-copy"><strong>{business.businessName}</strong><small>Distributor workspace{business.owner ? ' · Owner' : ' · Staff'}</small></span><ArrowRight size={19} aria-hidden="true" /></Link>}
              {availability.retailer && <Link className="workspace-choice" to="/retailer"><span className="workspace-choice-icon"><Store size={22} aria-hidden="true" /></span><span className="workspace-choice-copy"><strong>{retailers.length === 1 ? retailers[0].businessName : `${retailers.length} retailer businesses`}</strong><small>Retailer ordering workspace</small></span><ArrowRight size={19} aria-hidden="true" /></Link>}
            </div>}
    </main>
  )
}

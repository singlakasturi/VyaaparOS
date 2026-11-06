import { createContext, useContext, useEffect, useMemo, useState, useCallback } from 'react'
import { fetchAssignedRetailers } from './salesperson.js'
import { useAuth } from './AuthProvider.jsx'
import {
  cacheAssignedRetailers,
  getCachedAssignedRetailers,
  getAllDrafts,
  DRAFT_STATUS,
} from './db.js'

const SalespersonContext = createContext(null)
const SELECTED_RETAILER_KEY = 'vyapaaros.selectedRetailer'
const RETAILER_ID_KEY = 'vyapaaros.retailerId'

function getStoredRetailer() {
  try {
    const value = window.sessionStorage.getItem(SELECTED_RETAILER_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    window.sessionStorage.removeItem(SELECTED_RETAILER_KEY)
    window.sessionStorage.removeItem(RETAILER_ID_KEY)
    return null
  }
}

export function SalespersonProvider({ children }) {
  const { session } = useAuth()
  const [retailers, setRetailers] = useState([])
  const [selectedRetailer, setSelectedRetailerState] = useState(getStoredRetailer)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [isOnline, setIsOnline] = useState(() => (typeof navigator !== 'undefined' ? navigator.onLine : true))
  const [isCachedData, setIsCachedData] = useState(false)
  const [cachedAt, setCachedAt] = useState(null)
  const [pendingDraftsCount, setPendingDraftsCount] = useState(0)

  const isSalesperson = Boolean(
    session?.user?.roles?.some(
      (r) => r === 'SALESPERSON' || r === 'ROLE_SALESPERSON' || r === 'ADMIN' || r === 'ORDER_DESK'
    )
  )

  const salespersonId = session?.user?.id || null

  // Listen for online and offline events
  useEffect(() => {
    const handleOnline = () => setIsOnline(true)
    const handleOffline = () => setIsOnline(false)

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  // Refresh pending drafts count from IndexedDB
  const refreshDraftsCount = useCallback(async () => {
    if (!salespersonId) {
      setPendingDraftsCount(0)
      return
    }
    try {
      const drafts = await getAllDrafts(salespersonId)
      const count = drafts.filter(
        (d) => d.status !== DRAFT_STATUS.SUBMITTED && d.status !== DRAFT_STATUS.CREDIT_HOLD
      ).length
      setPendingDraftsCount(count)
    } catch {
      // In-memory or storage fallback
    }
  }, [salespersonId])

  useEffect(() => {
    refreshDraftsCount()
  }, [refreshDraftsCount])

  const loadRetailers = useCallback(async () => {
    if (!isSalesperson) return
    setIsLoading(true)
    setError(null)

    // If device is offline, load from IndexedDB cache directly
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      if (salespersonId) {
        const cached = await getCachedAssignedRetailers(salespersonId)
        if (cached && cached.retailers && cached.retailers.length > 0) {
          setRetailers(cached.retailers)
          setIsCachedData(true)
          setCachedAt(cached.cachedAt)
          setIsLoading(false)
          return
        }
      }
      setError('You are offline and no cached retailers were found. An initial online connection is required.')
      setIsLoading(false)
      return
    }

    try {
      const data = await fetchAssignedRetailers()
      const list = Array.isArray(data) ? data : []
      setRetailers(list)
      setIsCachedData(false)
      setCachedAt(null)

      // Cache assigned retailers in IndexedDB for offline drafting
      if (salespersonId && list.length > 0) {
        await cacheAssignedRetailers(salespersonId, list)
      }

      // If a selected retailer is stored, sync its latest data if present in assigned list
      setSelectedRetailerState((current) => {
        if (!current) return null
        const fresh = list.find((r) => r.id === current.id)
        if (fresh) {
          window.sessionStorage.setItem(SELECTED_RETAILER_KEY, JSON.stringify(fresh))
          return fresh
        }
        return current
      })
    } catch (err) {
      // Network failure fallback: try loading from cache
      if (salespersonId) {
        const cached = await getCachedAssignedRetailers(salespersonId)
        if (cached && cached.retailers && cached.retailers.length > 0) {
          setRetailers(cached.retailers)
          setIsCachedData(true)
          setCachedAt(cached.cachedAt)
          setIsLoading(false)
          return
        }
      }
      setError(err?.message || 'Unable to load assigned retailers. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }, [isSalesperson, salespersonId])

  useEffect(() => {
    if (isSalesperson) {
      loadRetailers()
    } else {
      setRetailers([])
      setSelectedRetailerState(null)
      window.sessionStorage.removeItem(SELECTED_RETAILER_KEY)
      window.sessionStorage.removeItem(RETAILER_ID_KEY)
    }
  }, [isSalesperson, loadRetailers])

  const selectRetailer = useCallback((retailer) => {
    if (!retailer) {
      window.sessionStorage.removeItem(SELECTED_RETAILER_KEY)
      window.sessionStorage.removeItem(RETAILER_ID_KEY)
      setSelectedRetailerState(null)
    } else {
      window.sessionStorage.setItem(SELECTED_RETAILER_KEY, JSON.stringify(retailer))
      window.sessionStorage.setItem(RETAILER_ID_KEY, retailer.id)
      setSelectedRetailerState(retailer)
    }
  }, [])

  const clearSelectedRetailer = useCallback(() => {
    selectRetailer(null)
  }, [selectRetailer])

  const filteredRetailers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return retailers
    return retailers.filter((r) => {
      const name = (r.businessName || '').toLowerCase()
      const owner = (r.ownerName || '').toLowerCase()
      const city = (r.city || '').toLowerCase()
      const phone = (r.contactPhone || '').toLowerCase()
      return name.includes(q) || owner.includes(q) || city.includes(q) || phone.includes(q)
    })
  }, [retailers, searchQuery])

  const value = useMemo(() => ({
    retailers,
    filteredRetailers,
    selectedRetailer,
    isLoading,
    error,
    searchQuery,
    setSearchQuery,
    selectRetailer,
    clearSelectedRetailer,
    reloadRetailers: loadRetailers,
    isOnline,
    isCachedData,
    cachedAt,
    pendingDraftsCount,
    refreshDraftsCount,
    salespersonId,
  }), [
    retailers,
    filteredRetailers,
    selectedRetailer,
    isLoading,
    error,
    searchQuery,
    selectRetailer,
    clearSelectedRetailer,
    loadRetailers,
    isOnline,
    isCachedData,
    cachedAt,
    pendingDraftsCount,
    refreshDraftsCount,
    salespersonId,
  ])

  return (
    <SalespersonContext.Provider value={value}>
      {children}
    </SalespersonContext.Provider>
  )
}

export function useSalesperson() {
  const context = useContext(SalespersonContext)
  if (!context) {
    throw new Error('useSalesperson must be used within SalespersonProvider')
  }
  return context
}

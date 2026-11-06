import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { getAccessToken, setAccessToken } from './api'
import { signOut } from './auth'
import { clearUserCache, getPendingDrafts } from './db'

const AuthContext = createContext(null)
const USER_KEY = 'vyapaaros.user'

function getStoredUser() {
  try {
    const value = window.sessionStorage.getItem(USER_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    window.sessionStorage.removeItem(USER_KEY)
    return null
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => (
    getAccessToken() ? { token: getAccessToken(), user: getStoredUser() } : null
  ))

  useEffect(() => {
    const handleExpiredSession = () => {
      const currentUser = getStoredUser()
      if (currentUser?.id) clearUserCache(currentUser.id).catch(() => {})
      window.sessionStorage.removeItem(USER_KEY)
      setSession(null)
    }
    window.addEventListener('vyapaaros:session-expired', handleExpiredSession)
    return () => window.removeEventListener('vyapaaros:session-expired', handleExpiredSession)
  }, [])

  /**
   * Performs safe logout:
   * 1. Checks for pending unsynchronised drafts.
   * 2. If found, warns the user and waits for confirmation before proceeding.
   * 3. Clears user-specific retailer and catalogue caches (not drafts themselves).
   * 4. Clears the session.
   *
   * Returns true if logout proceeded, false if the user cancelled.
   */
  const logout = useCallback(async () => {
    const currentUser = getStoredUser()
    const salespersonId = currentUser?.id || null

    // Warn if there are unsynced drafts for this user
    if (salespersonId) {
      try {
        const pending = await getPendingDrafts(salespersonId)
        if (pending.length > 0) {
          const confirmed = window.confirm(
            `You have ${pending.length} unsynchronised draft order${pending.length > 1 ? 's' : ''} that ${
              pending.length > 1 ? 'have' : 'has'
            } not been submitted yet.\n\n` +
            `These drafts will remain saved on this device and can be synced after you log back in.\n\n` +
            `Do you want to log out anyway?`
          )
          if (!confirmed) {
            return false
          }
        }
      } catch {
        // If we cannot read drafts, proceed with logout but do not silently destroy data
      }
    }

    // Clear user-specific cache (retailer list + catalogue) but NOT drafts
    if (salespersonId) {
      try {
        await clearUserCache(salespersonId)
      } catch {
        // Cache cleanup failure must not block logout
      }
    }

    signOut()
    window.sessionStorage.removeItem(USER_KEY)
    setSession(null)
    return true
  }, [])

  const value = useMemo(() => ({
    session,
    isAuthenticated: Boolean(session?.token),
    completeSignIn: (authResponse) => {
      setAccessToken(authResponse.accessToken)
      window.sessionStorage.setItem(USER_KEY, JSON.stringify(authResponse.user))
      setSession({ token: authResponse.accessToken, user: authResponse.user })
    },
    logout,
  }), [session, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}

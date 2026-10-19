import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, refreshAccessToken, setAccessToken, setSessionExpiredHandler } from '../lib/api'
import type { User } from '../lib/types'

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthState {
  status: Status
  user: User | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<Status>('loading')
  const [user, setUser] = useState<User | null>(null)

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [queryClient])

  // Pulihkan sesi saat halaman dibuka: cookie refresh token (jika ada) ditukar dengan access token baru.
  // Di mode development StrictMode memanggil efek ini dua kali; refreshAccessToken berbagi satu permintaan.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        await refreshAccessToken()
        const me = await api.me()
        if (!cancelled) {
          setUser(me)
          setStatus('authenticated')
        }
      } catch {
        if (!cancelled) {
          setAccessToken(null)
          setStatus('anonymous')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  // Saat refresh gagal di tengah pemakaian (sesi habis atau dicabut), kembali ke halaman masuk.
  useEffect(() => {
    setSessionExpiredHandler(clearSession)
    return () => setSessionExpiredHandler(null)
  }, [clearSession])

  const login = useCallback(
    async (email: string, password: string) => {
      await api.login(email, password)
      const me = await api.me()
      queryClient.clear()
      setUser(me)
      setStatus('authenticated')
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      clearSession()
    }
  }, [clearSession])

  const value = useMemo(() => ({ status, user, login, logout }), [status, user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth harus dipakai di dalam AuthProvider')
  return ctx
}

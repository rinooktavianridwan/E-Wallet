import type {
  AccessTokenResponse,
  PageResponse,
  RegisterInput,
  TopUpInput,
  Transaction,
  TransferInput,
  User,
  Wallet,
} from './types'

/**
 * Klien HTTP untuk backend.
 *
 * Keamanan token:
 * - Access token hanya disimpan di memori (variabel modul), tidak di localStorage,
 *   jadi skrip yang disuntikkan (XSS) tidak bisa membacanya dari penyimpanan browser.
 * - Refresh token tidak pernah terlihat oleh JavaScript. Backend menaruhnya di cookie
 *   HttpOnly + SameSite=Strict, dan browser mengirimnya otomatis ke /api/auth/*.
 */

let accessToken: string | null = null
let onSessionExpired: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

/** Dipanggil saat refresh gagal, supaya UI bisa kembali ke halaman masuk. */
export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler
}

export interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, title: string, detail: string, fieldErrors: Record<string, string> = {}) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // body kosong atau bukan JSON (misalnya error dari reverse proxy)
  }
  return new ApiError(
    response.status,
    problem.title ?? 'Permintaan gagal',
    problem.detail ?? `Server menjawab dengan status ${response.status}`,
    problem.errors ?? {},
  )
}

// --- Refresh access token -------------------------------------------------

let refreshInFlight: Promise<string> | null = null

async function callRefreshEndpoint(): Promise<string> {
  const response = await fetch('/api/auth/refresh', { method: 'POST' })
  if (!response.ok) {
    throw await toApiError(response)
  }
  const body = (await response.json()) as AccessTokenResponse
  accessToken = body.accessToken
  return body.accessToken
}

/**
 * Menukar cookie refresh token dengan access token baru.
 *
 * Refresh token berotasi: setiap pemakaian mengembalikan cookie baru dan mematikan yang lama.
 * Jika dua permintaan memakai cookie lama bersamaan, backend menganggapnya pencurian dan
 * mencabut seluruh sesi. Karena itu:
 * - dalam satu tab, semua pemanggil berbagi satu permintaan (single-flight);
 * - antar tab, permintaan diserialkan lewat Web Locks, jadi tab kedua selalu membawa
 *   cookie terbaru milik tab pertama.
 */
export function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    const run = () => callRefreshEndpoint()
    const locked =
      typeof navigator !== 'undefined' && navigator.locks
        ? navigator.locks.request('ewallet-refresh', run)
        : run()
    refreshInFlight = Promise.resolve(locked).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

// --- Request wrapper ------------------------------------------------------

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  headers?: Record<string, string>
  /** false untuk endpoint publik (login, register, logout): tanpa Bearer dan tanpa auto-refresh. */
  auth?: boolean
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { ...options.headers }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  return fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options)

  // Access token kedaluwarsa: tukar refresh token, lalu ulangi permintaan satu kali saja.
  if (response.status === 401 && options.auth !== false) {
    try {
      await refreshAccessToken()
    } catch {
      accessToken = null
      onSessionExpired?.()
      throw await toApiError(response)
    }
    response = await send(path, options)
    if (response.status === 401) {
      accessToken = null
      onSessionExpired?.()
    }
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

// --- Endpoint -------------------------------------------------------------

export const api = {
  async login(email: string, password: string): Promise<void> {
    const body = await request<AccessTokenResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    })
    accessToken = body.accessToken
  },

  register(input: RegisterInput) {
    return request<{ id: number; email: string; name: string }>('/api/auth/register', {
      method: 'POST',
      body: input,
      auth: false,
    })
  },

  async logout(): Promise<void> {
    try {
      await request<void>('/api/auth/logout', { method: 'POST', auth: false })
    } finally {
      accessToken = null
    }
  },

  me: () => request<User>('/api/users/me'),
  wallet: () => request<Wallet>('/api/wallets/me'),

  transactions: (page: number, size: number) =>
    request<PageResponse<Transaction>>(`/api/transactions?page=${page}&size=${size}`),

  topUp: (input: TopUpInput) =>
    request<Transaction>('/api/wallets/top-up', { method: 'POST', body: input }),

  transfer: (input: TransferInput, idempotencyKey: string) =>
    request<Transaction>('/api/transfers', {
      method: 'POST',
      body: input,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
}

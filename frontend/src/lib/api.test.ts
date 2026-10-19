import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getAccessToken, setAccessToken, setSessionExpiredHandler } from './api'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const wallet = { id: 1, balance: 100, active: true, updatedAt: '2026-01-01T00:00:00Z' }

function authorizationOf(call: unknown[]): string | undefined {
  const init = call[1] as RequestInit | undefined
  return (init?.headers as Record<string, string> | undefined)?.Authorization
}

describe('api client', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('old-token')
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
    setAccessToken(null)
    setSessionExpiredHandler(null)
  })

  it('mengirim access token sebagai Bearer', async () => {
    fetchMock.mockResolvedValueOnce(json(wallet))

    await api.wallet()

    expect(authorizationOf(fetchMock.mock.calls[0])).toBe('Bearer old-token')
  })

  it('saat 401: refresh sekali, lalu mengulang permintaan dengan token baru', async () => {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/auth/refresh') return json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 })
      const auth = (init?.headers as Record<string, string>).Authorization
      return auth === 'Bearer new-token' ? json(wallet) : json({ title: 'Unauthorized' }, 401)
    })

    await expect(api.wallet()).resolves.toEqual(wallet)
    expect(getAccessToken()).toBe('new-token')
    expect(fetchMock.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/wallets/me',
      '/api/auth/refresh',
      '/api/wallets/me',
    ])
  })

  it('beberapa permintaan yang 401 bersamaan hanya memicu satu refresh', async () => {
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input) === '/api/auth/refresh') {
        refreshCalls++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 })
      }
      const auth = (init?.headers as Record<string, string>).Authorization
      return auth === 'Bearer new-token' ? json(wallet) : json({ title: 'Unauthorized' }, 401)
    })

    await Promise.all([api.wallet(), api.me(), api.transactions(0, 5)])

    expect(refreshCalls).toBe(1)
  })

  it('jika refresh gagal: token dibuang, handler sesi habis dipanggil, error 401 dilempar', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    fetchMock.mockImplementation(async (input) =>
      String(input) === '/api/auth/refresh'
        ? json({ title: 'Invalid refresh token', detail: 'Refresh token is invalid' }, 401)
        : json({ title: 'Unauthorized' }, 401),
    )

    await expect(api.wallet()).rejects.toMatchObject({ status: 401 })

    expect(expired).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
  })

  it('login yang salah tidak memicu refresh', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ title: 'Invalid credentials', detail: 'Email or password is incorrect' }, 401),
    )

    await expect(api.login('a@b.com', 'salah')).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('error validasi membawa pesan per kolom', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ title: 'Invalid request', detail: 'Validation failed', errors: { email: 'Email format is invalid' } }, 400),
    )

    const error = await api.register({ name: 'A', email: 'x', password: '12345678' }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.fieldErrors).toEqual({ email: 'Email format is invalid' })
  })

  it('transfer mengirim header Idempotency-Key', async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 'x' }, 201))

    await api.transfer({ recipientEmail: 'a@b.com', amount: 10 }, 'kunci-123')

    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>
    expect(headers['Idempotency-Key']).toBe('kunci-123')
  })
})

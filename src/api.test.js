import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createIdempotencyKey, refreshSession, request, resolveClientToken, serverNow } from './api'

const storage = {
  values: new Map(),
  getItem(key) { return this.values.get(key) ?? null },
  setItem(key, value) { this.values.set(key, String(value)) },
  removeItem(key) { this.values.delete(key) },
}

beforeEach(() => {
  storage.values.clear()
  vi.stubGlobal('localStorage', storage)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('createIdempotencyKey', () => {
  it('uses the native UUID API when it is available', () => {
    expect(createIdempotencyKey({ randomUUID: () => 'native-request-id' })).toBe('native-request-id')
  })

  it('creates an RFC 4122 v4 UUID when randomUUID is unavailable', () => {
    const values = Array.from({ length: 16 }, (_, index) => index)
    const id = createIdempotencyKey({
      getRandomValues: (bytes) => {
        bytes.set(values)
        return bytes
      },
    })

    expect(id).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
  })

  it('still returns a request key when no crypto API exists', () => {
    expect(createIdempotencyKey(null)).toMatch(/^request-[a-z0-9]+-[a-z0-9]+$/)
  })
})

describe('refreshSession', () => {
  it('shares one rotating refresh request between concurrent callers', async () => {
    let resolveFetch
    const fetchMock = vi.fn(() => new Promise((resolve) => { resolveFetch = resolve }))
    vi.stubGlobal('fetch', fetchMock)

    const first = refreshSession()
    const second = refreshSession()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    resolveFetch({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({ accessToken: 'rotated-token', userId: 'staff-1', username: 'sysadmin' }),
    })

    const [firstSession, secondSession] = await Promise.all([first, second])
    expect(firstSession.accessToken).toBe('rotated-token')
    expect(secondSession).toEqual(firstSession)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('serializes refresh through the origin-wide Web Lock when available', async () => {
    const lockRequest = vi.fn((_name, callback) => callback())
    vi.stubGlobal('navigator', { locks: { request: lockRequest } })
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({ accessToken: 'locked-token', userId: 'staff-1', username: 'sysadmin' }),
    })))

    await refreshSession()

    expect(lockRequest).toHaveBeenCalledTimes(1)
    expect(lockRequest.mock.calls[0][0]).toBe('matrixlive-auth-refresh')
  })

  it('uses a token rotated by another tab while waiting for the lock', async () => {
    storage.setItem('matrix.access-token', 'before-lock')
    storage.setItem('matrix.identity', JSON.stringify({ id: 'staff-1', username: 'sysadmin' }))
    let runLockedCallback
    const lockRequest = vi.fn((_name, callback) => new Promise((resolve, reject) => {
      runLockedCallback = () => Promise.resolve(callback()).then(resolve, reject)
    }))
    const fetchMock = vi.fn()
    vi.stubGlobal('navigator', { locks: { request: lockRequest } })
    vi.stubGlobal('fetch', fetchMock)

    const pending = refreshSession()
    storage.setItem('matrix.access-token', 'after-lock')
    runLockedCallback()

    await expect(pending).resolves.toMatchObject({ accessToken: 'after-lock' })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not refresh after the session is cleared while waiting for the lock', async () => {
    storage.setItem('matrix.access-token', 'before-logout')
    let runLockedCallback
    const lockRequest = vi.fn((_name, callback) => new Promise((resolve, reject) => {
      runLockedCallback = () => Promise.resolve(callback()).then(resolve, reject)
    }))
    const fetchMock = vi.fn()
    vi.stubGlobal('navigator', { locks: { request: lockRequest } })
    vi.stubGlobal('fetch', fetchMock)

    const pending = refreshSession()
    storage.removeItem('matrix.access-token')
    runLockedCallback()

    await expect(pending).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

describe('authenticated request retries', () => {
  it('replays with an access token rotated by a concurrent request', async () => {
    storage.setItem('matrix.access-token', 'expired-token')
    const fetchMock = vi.fn((path, options) => {
      if (fetchMock.mock.calls.length === 1) storage.setItem('matrix.access-token', 'fresh-token')
      return Promise.resolve(fetchMock.mock.calls.length === 1
        ? {
            ok: false,
            status: 401,
            headers: { get: () => 'application/json' },
            json: async () => ({ message: 'expired' }),
          }
        : {
            ok: true,
            status: 200,
            headers: { get: () => 'application/json' },
            json: async () => ({ ok: true }),
          })
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/protected')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer fresh-token')
  })

  it('clears the staff session after an unrecoverable refresh failure', async () => {
    storage.setItem('matrix.access-token', 'expired')
    storage.setItem('matrix.identity', '{"username":"staff"}')
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false, status: 401, headers: { get: () => 'application/json' }, json: async () => ({ error: 'expired' }),
    })))
    await expect(request('/api/protected')).rejects.toMatchObject({ status: 401 })
    expect(storage.getItem('matrix.access-token')).toBeNull()
    expect(storage.getItem('matrix.identity')).toBeNull()
  })

  it('preserves the staff session when refreshing fails due to a network interruption', async () => {
    storage.setItem('matrix.access-token', 'cached')
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('network offline') }))
    await expect(refreshSession()).rejects.toThrow('network offline')
    expect(storage.getItem('matrix.access-token')).toBe('cached')
  })

  it('renews an expired participant session once for concurrent callers', async () => {
    const clientStorage = { ...storage, values: new Map() }
    vi.stubGlobal('sessionStorage', clientStorage)
    const token = `header.${btoa(JSON.stringify({ kind: 'PARTICIPANT', aid: 'renewal-test', exp: 1 }))}.signature`
    clientStorage.setItem('matrix.participant-refresh.renewal-test', 'long-client-credential')
    const fetchMock = vi.fn(async () => ({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ accessToken: 'renewed-client' }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const result = await Promise.all([resolveClientToken(token), resolveClientToken(token)])
    expect(result).toEqual(['renewed-client', 'renewed-client'])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/client-refresh')
    expect(clientStorage.getItem('matrix.participant-token.renewal-test')).toBe('renewed-client')
  })

  it('uses server time instead of the device wall clock', async () => {
    const now = Date.parse('2026-10-02T04:00:00Z')
    vi.spyOn(Date, 'now').mockReturnValue(now + 3600000)
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true, status: 200, headers: { get: (key) => key === 'X-Server-Time' ? new Date(now).toISOString() : 'application/json' }, json: async () => ({}),
    })))
    await request('/api/health', { auth: false })
    expect(Math.abs(serverNow() - now)).toBeLessThan(1000)
  })
})

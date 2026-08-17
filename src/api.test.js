import { describe, expect, it } from 'vitest'
import { createIdempotencyKey } from './api'

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

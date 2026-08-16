import { describe, expect, it } from 'vitest'
import { activeVenueCode, buildRegistrationPayload, splitRegistrationOptions } from './registration'

describe('registration request mapping', () => {
  it('sends configured dynamic values through customFields only', () => {
    expect(buildRegistrationPayload(
      { name: 'Alex', contact: '138 0000 1000', organization: 'Matrix' },
      { department: 'Engineering', name: 'spoofed', empty: '   ' },
    )).toEqual({
      name: 'Alex',
      contact: '138 0000 1000',
      organization: 'Matrix',
      customFields: { department: 'Engineering' },
    })
  })

  it('keeps a requested enabled venue and otherwise chooses the first enabled venue', () => {
    const venues = [
      { code: 'north', enabled: false },
      { code: 'south', enabled: true },
      { code: 'west', enabled: true },
    ]
    expect(activeVenueCode(venues, 'west')).toBe('west')
    expect(activeVenueCode(venues, 'north')).toBe('south')
    expect(activeVenueCode([], 'south')).toBe('')
  })

  it('converts staff-entered options to a clean API list', () => {
    expect(splitRegistrationOptions('Engineering\n\n Operations \n')).toEqual(['Engineering', 'Operations'])
  })
})

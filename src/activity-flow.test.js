import { describe, expect, it } from 'vitest'
import { availableActivities, participantActivityId, activityEntry, participantMatches } from './activity-flow'

describe('participant activity routing', () => {
  it('excludes drafts and children of closed parents', () => {
    const activities = [{ id: 'main', status: 'LIVE' }, { id: 'draft', status: 'DRAFT' },
      { id: 'lottery', status: 'LIVE', parentActivityId: 'main' },
      { id: 'hidden', status: 'LIVE', parentActivityId: 'draft' }]
    expect(availableActivities(activities).map((item) => item.id)).toEqual(['main', 'lottery'])
  })
  it('keeps lottery identity on the parent while routing to the lottery child', () => {
    const activity = { id: 'draw', activityType: 'LOTTERY', parentActivityId: 'main' }
    expect(participantActivityId(activity)).toBe('main')
    expect(activityEntry(activity)).toBe('/lottery/draw')
  })
  it('searches organization and custom registration values as well as identity', () => {
    expect(participantMatches({ name: 'Alice', organization: 'ACME', customFields: { team: 'Sales' } }, 'sales')).toBe(true)
    expect(participantMatches({ name: 'Alice', contact: '123456' }, '345')).toBe(true)
  })
})

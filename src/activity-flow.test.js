import { describe, expect, it } from 'vitest'
import { availableActivities, participantActivityId, activityEntry, participantMatches, activityNavigation } from './activity-flow'

describe('participant activity routing', () => {
  it('keeps turtle soup out of participant entries and exposes only its screen controls', () => {
    expect(availableActivities([{ id: 'soup', activityType: 'TURTLE_SOUP', status: 'LIVE' }])).toEqual([])
    expect(activityNavigation({ activityType: 'TURTLE_SOUP' })).toMatchObject({ showQuiz: false, showRewards: false, showControl: true, controlLabel: '海龟汤控场' })
  })
  it('shares the main identity for new quizzes and retains the local identity for legacy quizzes', () => {
    expect(participantActivityId({ id: 'quiz', parentActivityId: 'main', activityType: 'QUIZ', participantActivityId: 'main' })).toBe('main')
    expect(participantActivityId({ id: 'old-quiz', parentActivityId: 'main', activityType: 'QUIZ', participantActivityId: 'old-quiz' })).toBe('old-quiz')
  })
  it('separates main management, quiz controls and host lottery controls while preserving legacy entries', () => {
    expect(activityNavigation({ activityType: 'EVENT' }).showQuiz).toBe(false)
    expect(activityNavigation({ activityType: 'EVENT', legacyOperations: true }).showQuiz).toBe(true)
    expect(activityNavigation({ activityType: 'QUIZ' }).controlLabel).toBe('答题控场')
    expect(activityNavigation({ activityType: 'LOTTERY' })).toMatchObject({ showQuiz: false, controlLabel: '摇奖控场', rewardsLabel: '自助抽奖与核销' })
  })
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

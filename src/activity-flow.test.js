import { describe, expect, it } from 'vitest'
import { quizNextStep, availableActivities, participantActivityId, activityEntry, participantMatches, activityNavigation, screenJoinPrompt, screenJoinUrl } from './activity-flow'

describe('participant activity routing', () => {
  it('uses venue feature switches for all operational navigation', () => {
    expect(activityNavigation({activityType: 'VENUE', features: []})).toMatchObject({showQuiz: false,showControl: false,showRewards: false})
    expect(activityNavigation({activityType: 'VENUE', features: ['QUIZ']})).toMatchObject({showQuiz: true,showControl: true,showRewards: true})
    expect(activityNavigation({activityType: 'VENUE', features: ['LOTTERY','TURTLE_SOUP']})).toMatchObject({showQuiz: false,showControl: false,showRewards: true})
  })
  it('does not expose disabled features through old public links', () => {
    expect(availableActivities([{id:'quiz',activityType:'QUIZ',status:'LIVE',featureEnabled:false}])).toEqual([])
  })
  it('hides mother entries and supports venue portals when public APIs omit mothers', () => {
    const main = { id: 'main', activityType: 'EVENT', status: 'LIVE' }
    const venue = { id: 'venue', parentActivityId: 'main', activityType: 'VENUE', status: 'LIVE' }
    const quiz = { id: 'quiz', parentActivityId: 'venue', activityType: 'QUIZ', status: 'LIVE' }
    expect(availableActivities([main, venue, quiz]).map((item) => item.id)).toEqual(['venue', 'quiz'])
    expect(availableActivities([venue, quiz]).map((item) => item.id)).toEqual(['venue', 'quiz'])
    expect(activityNavigation(venue)).toMatchObject({ showQuiz: true, showControl: false, showRewards: false })
  })
  it('checks the full venue hierarchy before exposing a child entry', () => {
    const main = { id: 'main', activityType: 'EVENT', status: 'FINISHED' }
    const venue = { id: 'venue', parentActivityId: 'main', activityType: 'VENUE', status: 'LIVE' }
    const quiz = { id: 'quiz', parentActivityId: 'venue', activityType: 'QUIZ', status: 'LIVE' }
    expect(availableActivities([main, venue, quiz])).toEqual([])
  })
  it('keeps turtle soup out of participant entries and exposes only its screen controls', () => {
    expect(availableActivities([{ id: 'soup', activityType: 'TURTLE_SOUP', status: 'LIVE' }])).toEqual([])
    expect(activityNavigation({ activityType: 'TURTLE_SOUP' })).toMatchObject({ showQuiz: false, showRewards: false, showControl: true, controlLabel: '海龟汤控场' })
  })
  it('shares the main identity for new quizzes and retains the local identity for legacy quizzes', () => {
    expect(participantActivityId({ id: 'quiz', parentActivityId: 'main', activityType: 'QUIZ', participantActivityId: 'main' })).toBe('main')
    expect(participantActivityId({ id: 'old-quiz', parentActivityId: 'main', activityType: 'QUIZ', participantActivityId: 'old-quiz' })).toBe('old-quiz')
  })
  it('separates main management, quiz controls and host lottery controls while preserving legacy entries', () => {
    expect(activityNavigation({ activityType: 'EVENT' })).toMatchObject({ showQuiz: true, showControl: false, showRewards: false })
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

describe('big-screen join QR', () => {
  it('keeps the join QR on quiz, buzz and turtle soup screens only', () => {
    for (const mode of ['QUESTION', 'RESULT', 'TURTLE_SOUP']) expect(screenJoinPrompt(mode)).toBeTruthy()
    for (const mode of ['LOBBY', 'TEMPLATE', 'SCOREBOARD', 'LEADERBOARD', 'WINNERS', 'ANNOUNCEMENT', 'FILE', '', undefined]) {
      expect(screenJoinPrompt(mode)).toBe(null)
    }
  })
  it('labels the soup QR for clue guessing instead of answering', () => {
    expect(screenJoinPrompt('TURTLE_SOUP').caption).toBe('扫码加入')
    expect(screenJoinPrompt('TURTLE_SOUP').hint).toContain('猜汤')
    expect(screenJoinPrompt('QUESTION').hint).toContain('抢答')
  })
  it('points every QR at the same join entry used by templates', () => {
    expect(screenJoinUrl('http://127.0.0.1:5173', 'venue-1')).toBe('http://127.0.0.1:5173/join/venue-1')
    expect(screenJoinUrl('https://live.example.com/', 'venue-1')).toBe('https://live.example.com/join/venue-1')
  })
})

describe('guided quiz flow', () => {
  it('waits for loaded state and an available question', () => {
    expect(quizNextStep(null, '', 'q1', null, 0).disabled).toBe(true)
    expect(quizNextStep({stage:'LOBBY'}, '', undefined, null, 0).disabled).toBe(true)
  })
  it('requires confirmation to reveal early, including when another question is prepared', () => {
    expect(quizNextStep({stage:'QUESTION_OPEN',questionId:'q1'}, 'q2', 'q2', 'q2', 20).action).toBe('CONFIRM_REVEAL')
    expect(quizNextStep({stage:'QUESTION_OPEN',questionId:'q1'}, '', 'q1', 'q2', 0).action).toBe('REVEAL')
  })
  it('previews the next question before allowing a separate start', () => {
    const state = {stage:'ANSWER_REVEALED',questionId:'q1'}
    expect(quizNextStep(state, '', 'q1', 'q2', 0)).toMatchObject({action:'PREPARE',questionId:'q2'})
    expect(quizNextStep(state, 'q2', 'q2', 'q2', 0)).toMatchObject({action:'OPEN',questionId:'q2'})
  })
  it('finishes with the scoreboard and permits an explicit replay', () => {
    expect(quizNextStep({stage:'ANSWER_REVEALED',questionId:'q2'}, '', 'q2', null, 0).action).toBe('SCOREBOARD')
    expect(quizNextStep({stage:'SCOREBOARD',questionId:'q2'}, '', 'q2', null, 0).disabled).toBe(true)
    expect(quizNextStep({stage:'SCOREBOARD',questionId:'q2'}, 'q1', 'q1', null, 0).action).toBe('OPEN')
  })
})

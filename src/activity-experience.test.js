import { describe, expect, it } from 'vitest'
import { availableActivities, activityEntryGroups, controlStatusHint, screenAnswerHint, participationLabel, participantFeatureLabel, venueDisplayName } from './activity-flow'

describe('three-client activity experience', () => {
  it('renders venue entries even when their mother events are excluded from the public list', () => {
    const rows=availableActivities([
      {id:'venue',parentActivityId:'hidden-mother',activityType:'VENUE',status:'LIVE',name:'一号会场'},
      {id:'quiz',parentActivityId:'venue',activityType:'QUIZ',status:'LIVE',name:'答题'},
      {id:'soup',parentActivityId:'venue',activityType:'TURTLE_SOUP',status:'LIVE'},
    ])
    expect(activityEntryGroups(rows)).toEqual([{activity:rows[0],features:[rows[1]]}])
  })
  it('retains legacy root events and renders multiple independent venues exactly once', () => {
    const rows=[{id:'old'},{id:'v1',parentActivityId:'hidden'},{id:'v2',parentActivityId:'hidden'},{id:'q1',parentActivityId:'v1'}]
    const groups=activityEntryGroups(rows)
    expect(groups.map(group=>group.activity.id)).toEqual(['old','v1','v2'])
    expect(groups[1].features.map(item=>item.id)).toEqual(['q1'])
  })
  it('gives the actual next step for draft, registration, paused and ended activities', () => {
    expect(controlStatusHint('DRAFT')).toContain('开始活动')
    expect(controlStatusHint('DRAFT')).not.toContain('已暂停')
    expect(controlStatusHint('REGISTRATION_OPEN')).toContain('报名已开放')
    expect(controlStatusHint('PAUSED')).toContain('恢复')
    expect(controlStatusHint('FINISHED')).toContain('已结束')
    expect(controlStatusHint('LIVE')).toBe('')
  })
  it('never asks viewers to submit after time has expired or answers are revealed', () => {
    expect(screenAnswerHint(false,15,'QUESTION_OPEN')).toContain('提交答案')
    expect(screenAnswerHint(false,0,'QUESTION_OPEN')).toContain('答题已结束')
    expect(screenAnswerHint(true,15,'ANSWER_REVEALED')).toContain('答案已公布')
    expect(screenAnswerHint(true,0,'ANSWER_REVEALED')).not.toContain('提交答案')
  })
  it('uses venue and feature names without hidden mother labels or duplicated feature names', () => {
    const venue={id:'v',activityType:'VENUE',name:'一号会场',parentActivityId:'hidden'}
    const quiz={id:'q',activityType:'QUIZ',name:'答题',parentActivityId:'v'}
    expect(participationLabel(venue,[venue,quiz])).toBe('会场 · 一号会场')
    expect(participationLabel(quiz,[venue,quiz])).toBe('一号会场 / 答题')
    expect(participantFeatureLabel(quiz)).toBe('答题')
    expect(venueDisplayName('main')).toBe('本会场')
    expect(venueDisplayName('main',[{code:'main',name:'一号会场'}])).toBe('一号会场')
  })
})

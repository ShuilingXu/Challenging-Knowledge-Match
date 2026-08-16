import { describe, expect, it } from 'vitest'
import { formatSeconds, questions, scoreAnswer } from './domain'

describe('answer scoring', () => {
  it('awards full score for the correct single choice', () => {
    expect(scoreAnswer(questions[0], 'B')).toBe(100)
    expect(scoreAnswer(questions[0], 'C')).toBe(0)
  })

  it('supports partial and full score for multi-choice answers', () => {
    expect(scoreAnswer(questions[1], ['A'])).toBe(40)
    expect(scoreAnswer(questions[1], ['A', 'B', 'D'])).toBe(100)
    expect(scoreAnswer(questions[1], ['A', 'C'])).toBe(0)
  })

  it('formats the event timer consistently', () => {
    expect(formatSeconds(8)).toBe('00:08')
    expect(formatSeconds(-2)).toBe('00:00')
  })
})

import { describe, it, expect } from 'vitest'
import { paginateSoupText, soupPlaybackFrame } from './soupPlayback'

describe('soup reading pages', () => {
  it('preserves the full story, whitespace and emoji across bounded pages', () => {
    const text = '雨夜。\n\n旅客👨‍👩‍👧‍👦听到了消息！  完整真相。'.repeat(12)
    const pages = paginateSoupText(text, s => [...new Intl.Segmenter('zh', { granularity: 'grapheme' }).segment(s)].length <= 18)
    expect(pages.join('')).toBe(text)
    expect(pages.every(p => [...new Intl.Segmenter('zh', { granularity: 'grapheme' }).segment(p)].length <= 18)).toBe(true)
    expect(pages.some(p => p.includes('👨‍👩‍👧‍👦'))).toBe(true)
  })
  it('keeps short content on one page and always makes progress', () => {
    expect(paginateSoupText('短故事', () => true)).toEqual(['短故事'])
    expect(paginateSoupText('abc', () => false)).toEqual(['a','b','c'])
    expect(paginateSoupText('', () => true)).toEqual([''])
  })
})
describe('shared playback clock', () => {
  const pages = [{ text: 'A' }, { text: 'B' }, { text: 'C' }]
  it('advances automatically, loops, and focuses the newest clue', () => {
    const clock = { startedAt: 1000, paceSeconds: 8 }
    expect(soupPlaybackFrame(pages, clock, 9000).index).toBe(1)
    expect(soupPlaybackFrame(pages, clock, 25000).index).toBe(0)
    expect(soupPlaybackFrame(pages, clock, 1000, 2).index).toBe(2)
  })
  it('freezes paused pages and supports previous from the first page', () => {
    const clock = { paused:true, elapsedMs: 9000, paceSeconds: 8 }
    expect(soupPlaybackFrame(pages, clock, 1000).index).toBe(1)
    expect(soupPlaybackFrame(pages, clock, 999999).index).toBe(1)
    expect(soupPlaybackFrame(pages, { paused:true, step:-1 }, 1000).index).toBe(2)
  })
  it('allows time to read longer pages and leaves a single page still', () => {
    expect(soupPlaybackFrame([{ text:'长'.repeat(360) }], { paceSeconds:8 },1000).duration).toBe(33000)
    expect(soupPlaybackFrame([{ text:'短' }], { startedAt:1 },999999).index).toBe(0)
  })
})

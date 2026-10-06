import { describe, it, expect } from 'vitest'
import { paginateSoupText, soupPlaybackFrame, paginateSoupClues, soupClueFrame } from './soupPlayback'

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

describe('independent clue pages', () => {
  const fits = clues => clues.reduce((size, c) => size + c.text.length + 2, 0) <= 20
  it('shows several clues together and groups overflow without splitting short clues', () => {
    const entries = [{number:3,text:'first'},{number:1,text:'second'},{number:7,text:'third'}]
    expect(paginateSoupClues(entries.slice(0,2),fits)).toEqual([entries.slice(0,2)])
    expect(paginateSoupClues(entries,fits)).toEqual([entries.slice(0,2),entries.slice(2)])
  })
  it('preserves an oversized clue and its number across pages', () => {
    const entries = [{number:4,text:'very long clue text '.repeat(5)},{number:9,text:'short'}]
    const pages = paginateSoupClues(entries,fits)
    expect(pages.every(fits)).toBe(true)
    expect(pages.flat().filter(c=>c.number===4).map(c=>c.text).join('')).toBe(entries[0].text)
    expect(pages.flat().filter(c=>c.number===4).slice(1).every(c=>c.continued)).toBe(true)
    expect(pages.flat().at(-1)).toEqual(entries[1])
  })
  it('only rotates overflow pages on its own reading clock', () => {
    const pages = [[{number:1,text:'one'}],[{number:2,text:'two'}]]
    expect(soupClueFrame(pages,1000,1000).index).toBe(0)
    expect(soupClueFrame(pages,1000,16000).index).toBe(1)
    expect(soupClueFrame(pages,1000,31000).index).toBe(0)
    expect(soupClueFrame([pages.flat()],1000,999999).index).toBe(0)
    expect(soupClueFrame([[{number:1,text:'x'.repeat(360)}]],1000).duration).toBe(33000)
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
  it('uses the selected interval even for long pages and leaves a single page still', () => {
    expect(soupPlaybackFrame([{ text:'长'.repeat(360) }], { paceSeconds:8 },1000).duration).toBe(8000)
    expect(soupPlaybackFrame(pages, { startedAt:1000, paceSeconds:15 },13000).index).toBe(0)
    expect(soupPlaybackFrame(pages, { startedAt:1000, paceSeconds:8 },13000).index).toBe(1)
    expect(soupPlaybackFrame([{ text:'短' }], { startedAt:1 },999999).index).toBe(0)
  })
})

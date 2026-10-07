import { describe, it, expect } from 'vitest'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { TurtleSoupScreen } from './TurtleSoup'

describe('simultaneous soup display', () => {
  it('includes the solution pane as soon as the surface is shown', () => {
    const html = renderToStaticMarkup(createElement(TurtleSoupScreen, {
      data: { stage: 'SURFACE', title: '故事', surface: '汤面内容', solution: '汤底内容', clues: [] },
    }))
    expect(html).toContain('aria-label="汤面"')
    expect(html).toContain('aria-label="汤底 · 完整真相"')
    expect(html).toContain('soup-screen-body--solution')
  })

  it('keeps the surface and solution hidden in the lobby', () => {
    const html = renderToStaticMarkup(createElement(TurtleSoupScreen, {
      data: { stage: 'LOBBY', surface: '汤面内容', solution: '汤底内容' },
    }))
    expect(html).not.toContain('aria-label="汤面"')
    expect(html).not.toContain('aria-label="汤底 · 完整真相"')
  })
})

// Measuring is supplied by the screen so page boundaries follow the actual font and area.
export function paginateSoupText(text, fits) {
  const units = typeof Intl.Segmenter === 'function'
    ? [...new Intl.Segmenter('zh', { granularity: 'grapheme' }).segment(text)].map(x => x.segment)
    : Array.from(text)
  const pages = []
  let start = 0
  while (start < units.length) {
    let low = start + 1, high = units.length
    while (low < high) {
      const middle = Math.ceil((low + high) / 2)
      if (fits(units.slice(start, middle).join(''))) low = middle
      else high = middle - 1
    }
    let end = low
    // Prefer a sentence boundary near the end, preserving every character.
    for (let i = end - 1; i >= start + Math.floor((end - start) * .75); i--) {
      if (/[。！？!?；;\n]/.test(units[i])) { end = i + 1; break }
    }
    pages.push(units.slice(start, end).join(''))
    start = end
  }
  return pages.length ? pages : ['']
}

export function soupPlaybackFrame(pages, playback = {}, now = Date.now(), focus = 0) {
  const duration = Math.max(8, Math.min(40, Number(playback.paceSeconds) || 15)) * 1000
  const elapsed = Math.max(0, Number(playback.elapsedMs) || 0)
    + (playback.paused ? 0 : Math.max(0, now - (Number(playback.startedAt) || now)))
  const count = Math.max(1, pages.length)
  const index = ((focus + Math.floor(elapsed / duration) + (Number(playback.step) || 0)) % count + count) % count
  return { index, duration, progress: count === 1 ? 0 : elapsed % duration / duration }
}

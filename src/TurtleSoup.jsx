import { PageHeader, StatusNotice } from "./StaffUi"
import './turtle-soup.css'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { api } from './api'

// Fit within a readable range; extremely long material stays in its own pane.
function SoupPane({ title, className = '', children }) {
  const ref = useRef(null)
  const [overflow, setOverflow] = useState(false)
  useLayoutEffect(() => {
    const node = ref.current
    let frame, active = true
    const fit = () => {
      if (!active || !node.clientHeight) return
      node.style.setProperty('--soup-fit', 1)
      if (node.scrollHeight > node.clientHeight + 1) {
        let low = .82, high = 1
        for (let i = 0; i < 7; i++) {
          const scale = (low + high) / 2
          node.style.setProperty('--soup-fit', scale)
          if (node.scrollHeight > node.clientHeight + 1) high = scale
          else low = scale
        }
        node.style.setProperty('--soup-fit', low)
      }
      setOverflow(node.scrollHeight > node.clientHeight + 1)
    }
    const schedule = () => { if (!active) return; cancelAnimationFrame(frame); frame = requestAnimationFrame(fit) }
    fit()
    const observer = new ResizeObserver(schedule)
    observer.observe(node)
    document.fonts?.ready.then(schedule)
    return () => { active = false; observer.disconnect(); cancelAnimationFrame(frame) }
  }, [children])
  return <section className={`soup-pane ${className}`}>
    <h2>{title}{overflow && <small>可滚动查看</small>}</h2>
    <div ref={ref} className="soup-pane-scroll" data-screen-scroll role="region" aria-label={title} tabIndex={overflow ? 0 : undefined}>{children}</div>
  </section>
}

export function TurtleSoupScreen({ data = {}, scrollRef }) {
  const waiting = !data.stage || data.stage === 'LOBBY'
  const solution = data.stage === 'SOLUTION'
  const clues = !!data.clues?.length
  return <article ref={scrollRef} className={`soup-screen ${waiting ? 'soup-screen--waiting' : ''}`}>
    <header><span className="soup-kicker">海龟汤 · 情境推理</span><span>{waiting ? '候场' : data.stage === 'SOLUTION' ? '真相揭晓' : '一起寻找真相'}</span></header>
    <h1 title={waiting ? undefined : data.title}>{waiting ? '一个故事，藏着另一种真相' : data.title}</h1>
    {waiting ? <p className="soup-surface">向主持人提问，通过「是」「否」「无关」逐步还原故事。</p> : <div className={`soup-screen-body ${solution ? 'soup-screen-body--solution' : ''} ${clues ? 'soup-screen-body--clues' : ''}`}>
      <SoupPane title="汤面" className="soup-surface-pane"><p className="soup-surface">{data.surface}</p></SoupPane>
      {clues && <SoupPane title="已公开线索" className="soup-clues"><ol>{data.clues.map((clue, index) => <li key={index}><span>{String(data.clueNumbers?.[index] ?? index + 1).padStart(2, '0')}</span><p>{clue}</p></li>)}</ol></SoupPane>}
      {solution && <SoupPane title="汤底 · 完整真相" className="soup-solution"><p>{data.solution}</p></SoupPane>}
    </div>}
    <footer>现场提问 · 主持人回应 · 共同推理</footer>
  </article>
}

const empty = { title: '', surface: '', clues: [], solution: '' }
export function TurtleSoupConsole({ activity, canManage, lifecycle, useStream }) {
  const [state, setState] = useState(null)
  const layoutRef = useRef(null)
  const controlsRef = useRef(null)
  const [draft, setDraft] = useState(empty)
  const [dirty, setDirty] = useState(false)
  const dirtyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [confirmReveal, setConfirmReveal] = useState(false)
  const [manualClue, setManualClue] = useState('')
  const [templates, setTemplates] = useState([])
  const [playlist, setPlaylist] = useState({entries: [], activeTemplateId: null})
  const [selectedIds, setSelectedIds] = useState([])
  const [playlistEditing, setPlaylistEditing] = useState(false)
  useLayoutEffect(() => {
    const controls = controlsRef.current
    if (!controls) return
    const resize = () => layoutRef.current?.style.setProperty('--soup-controls-height', `${controls.offsetHeight}px`)
    const observer = new ResizeObserver(resize)
    observer.observe(controls)
    resize()
    return () => observer.disconnect()
  }, [!!state])
  useEffect(() => { api.soupPlaylist(activity.id).then((next) => { setPlaylist(next); setSelectedIds(next.entries.map((entry) => entry.id)) }).catch((e) => setError(e.message)) }, [activity.id])
  useEffect(() => { if (canManage) api.soupTemplates(activity.id).then(setTemplates).catch((e) => setError(e.message)) }, [activity.id, canManage])
  const load = useCallback(async () => {
    try {
      const next = await api.turtleSoup(activity.id)
      setState(next)
      setPlaylist(await api.soupPlaylist(activity.id))
      if (!dirtyRef.current) setDraft(next.content || empty)
    } catch (cause) { setError(cause.message) }
  }, [activity.id])
  useEffect(() => { load() }, [load])
  useStream(activity.id, load, undefined, setError)
  const edit = (patch) => { dirtyRef.current = true; setDirty(true); setDraft((value) => ({ ...value, ...patch })); setNotice('') }
  const save = async (event) => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const next = await api.saveTurtleSoup(activity.id, draft)
      dirtyRef.current = false; setDirty(false); setState(next); setDraft(next.content); setNotice('汤稿已保存，可以开始展示。')
    } catch (cause) { setError(cause.message) }
    finally { setBusy(false) }
  }
  const control = async (action, extra = {}) => {
    setBusy(true); setError(''); setConfirmReveal(false); setNotice('')
    try { setState(await api.controlTurtleSoup(activity.id, { action, revision: state.revision, ...extra })); if (action === 'ADD_CLUE') setManualClue(''); setNotice(action === 'ADD_CLUE' && !extra.publishNow ? '临时线索已保存，尚未公开。' : '已同步到本会场大屏。') }
    catch (cause) { setError(cause.message); await load() }
    finally { setBusy(false) }
  }
  const content = state?.content
  const waiting = state?.stage === 'LOBBY'
  const running = state?.stage === 'SURFACE'
  const ended = ['FINISHED', 'CANCELLED'].includes(activity.status)
  const disabled = busy || dirty || !content || activity.status !== 'LIVE'
  const selected = state?.revealedClueIndexes || Array.from({ length: state?.revealedClues || 0 }, (_, i) => i)
  const preview = waiting || !state ? { stage: 'LOBBY' } : { stage: state.stage, title: content.title, surface: content.surface,
    clues: selected.map((index) => content.clues[index]), clueNumbers: selected.map((index) => index + 1), ...(state.stage === 'SOLUTION' ? { solution: content.solution } : {}) }
  return <div className="page-content" ref={layoutRef}>
    <PageHeader eyebrow="会场海龟汤" title="海龟汤控场" description="准备汤稿 → 展示汤面 → 选择线索 → 揭晓汤底。观众在现场提问，无需手机报名或作答。" />
    {lifecycle}
    <details className="data-panel staff-panel staff-form soup-playlist" open={waiting || playlistEditing || undefined}><summary>本会场使用的汤稿{content?.title && <span> · {content.title}</span>}</summary><p>汤稿来自所属母活动，可选多份依次使用。切换前请先回到候场；切换后线索和揭晓状态会清零，汤底不会自动上屏。</p>
      <label>当前汤稿<select aria-label="当前汤稿" value={playlist.activeTemplateId || ''} disabled={busy || !waiting || ended || dirty} onChange={async (e) => {
        if (!e.target.value) return; setBusy(true); setError('');
        try { const next=await api.switchSoup(activity.id,e.target.value,state.revision); setState(next); setDraft(next.content || empty); setPlaylist(await api.soupPlaylist(activity.id)); setNotice('已切换汤稿，请点击“展示汤面”开始新故事。') } catch(cause) {setError(cause.message); await load()} finally {setBusy(false)}
      }}><option value="">请选择已选入会场的汤稿</option>{playlist.entries.map((entry) => <option key={entry.id} value={entry.id}>{entry.content.title}</option>)}</select></label>
      {!playlist.entries.length && <p>尚未选入汤稿，请管理员点击“选择本会场汤稿”。</p>}
      {canManage && <button className="secondary-button" disabled={busy || !waiting || ended || dirty} onClick={() => {setSelectedIds(playlist.entries.map((entry) => entry.id));setPlaylistEditing(!playlistEditing)}}>选择本会场汤稿</button>}
      {playlistEditing && <div><h3>从母活动汤稿库勾选</h3><p>可勾选多份；已有汤稿的本地修改会保留。取消勾选会移出会场列表，不会删除母活动原稿。</p>{[...templates, ...playlist.entries.filter((entry) => !templates.some((item) => item.id === entry.id))].map((entry) => <label className="venue-feature-choice" key={entry.id}><input type="checkbox" checked={selectedIds.includes(entry.id)} onChange={(e) => setSelectedIds(e.target.checked ? [...selectedIds,entry.id] : selectedIds.filter((id) => id !== entry.id))} />{entry.content.title}</label>)}{!templates.length && <p>母活动尚未准备汤稿，请联系母活动资料管理员。</p>}<button className="primary-button" disabled={busy || dirty || !waiting} onClick={async () => {setBusy(true);setError('');try {const next=await api.saveSoupPlaylist(activity.id,selectedIds);setPlaylist(next);setPlaylistEditing(false);await load();setNotice(`已选入 ${next.entries.length} 份汤稿。`)}catch(cause){setError(cause.message)}finally{setBusy(false)}}}>保存会场汤稿选择</button></div>}
    </details>
    <StatusNotice text={error} tone="error" onRetry={load} />
    <StatusNotice text={notice} />
    {!state ? <p>正在读取汤稿…</p> : <>
        <section ref={controlsRef} className="data-panel staff-panel staff-form soup-controls"><div><span className="type-chip">{waiting ? '候场' : running ? '推理中' : '已揭晓'}</span><h2>展示控制</h2><p>已公开 {state.revealedClues} / {content?.clues.length || 0} 条线索</p></div>
          <div className="soup-actions">
            <button className="primary-button" disabled={disabled || !waiting} onClick={() => control('SHOW_SURFACE')}>展示汤面</button>
            <button className="secondary-button" disabled={disabled || !running} onClick={() => setConfirmReveal(true)}>揭晓汤底</button>
            <button className="secondary-button" disabled={busy || waiting} onClick={() => control('RESET')}>回到候场 / 重置</button>
          </div>
          {confirmReveal && <div className="soup-reveal-confirm" role="alert"><p>确认将完整汤底展示给所有观众？</p><button className="primary-button" disabled={busy} onClick={() => control('REVEAL_SOLUTION')}>确认揭晓</button><button className="secondary-button" onClick={() => setConfirmReveal(false)}>继续推理</button></div>}
          {dirty && <p>有未保存的修改，请先保存汤稿。</p>}
          {activity.status !== 'LIVE' && <p>请先开始海龟汤；暂停期间可回到候场。</p>}
        </section>
      <div className="soup-console-grid">
      <div className="soup-console-main">
        <section className="data-panel staff-panel staff-form soup-private"><h2>交互线索板</h2><p>根据现场推断自由选择任意线索。公开顺序与推理过程一致，可单独撤回；未公开内容仅主持人可见。</p>
          {!content ? <p>请先准备并保存汤稿。</p> : <><h3>{content.title}</h3><p className="soup-text soup-host-surface">{content.surface}</p>
            <div className="soup-clue-board">{content.clues.map((clue, index) => {
              const visible = selected.includes(index)
              return <article className={`soup-clue-card ${visible ? 'is-revealed' : ''}`} key={index}>
                <header><strong>线索 {index + 1}</strong><span className="type-chip">{visible ? '已公开' : '未公开'}</span></header>
                <p className="soup-text">{clue}</p>
                <button type="button" className={visible ? 'secondary-button' : 'primary-button'} disabled={disabled || !running}
                  aria-label={`${visible ? '撤回' : '公开'}线索 ${index + 1}`}
                  onClick={() => control(visible ? 'HIDE_CLUE' : 'SHOW_CLUE', { clueIndex: index })}>{visible ? '撤回这条线索' : '公开这条线索'}</button>
              </article>
            })}</div>
            {!content.clues.length && <p>还没有线索，可以现场临时添加。</p>}
            <details><summary>查看汤底（仅主持人）</summary><p className="soup-text">{content.solution}</p></details></>}
        </section>
        <section className="data-panel staff-panel staff-form soup-manual"><h2>临时添加线索</h2><p>现场补充提示、澄清误解。保存后进入线索板，也可直接公开到大屏。</p>
          <label>临时线索<textarea maxLength={2000} rows={3} value={manualClue} disabled={busy || ended || state.stage === 'SOLUTION'} onChange={(e) => setManualClue(e.target.value)} placeholder="输入主持人现场补充的提示…" /></label>
          <small>{manualClue.length}/2000 字 · 已保存 {content?.clues.length || 0}/50 条</small>
          <div className="soup-actions">
            <button type="button" className="secondary-button" disabled={disabled || !manualClue.trim() || content.clues.length >= 50 || state.stage === 'SOLUTION'} onClick={() => control('ADD_CLUE', { clue: manualClue, publishNow: false })}>保存为待展示线索</button>
            <button type="button" className="primary-button" disabled={disabled || !running || !manualClue.trim() || content.clues.length >= 50} onClick={() => control('ADD_CLUE', { clue: manualClue, publishNow: true })}>添加并立即公开</button>
          </div>
        </section>
        {canManage && <details className="data-panel staff-panel staff-form soup-editor" open={dirty || undefined}><summary>编辑当前汤稿（仅本会场）</summary><form onSubmit={save}><p>修改前请回到候场。线索可自由选择展示顺序，也可在推理时临时添加。</p>
          <fieldset disabled={busy || !waiting || ended}>
            <label>故事标题<input required maxLength={160} value={draft.title} onChange={(e) => edit({ title: e.target.value })} placeholder="例如：最后一班电梯" /></label>
            <label>汤面<textarea required maxLength={8000} rows={5} value={draft.surface} onChange={(e) => edit({ surface: e.target.value })} placeholder="描述令人疑惑的故事，保留关键真相。" /></label>
            <label>线索 · {draft.clues.length}/50</label>
            {draft.clues.map((clue, index) => <div className="soup-clue-editor" key={index}><label>线索 {index + 1}<textarea required maxLength={2000} rows={2} value={clue} onChange={(e) => edit({ clues: draft.clues.map((value, i) => i === index ? e.target.value : value) })} /></label><button type="button" className="secondary-button" onClick={() => edit({ clues: draft.clues.filter((_, i) => i !== index) })}>移除</button></div>)}
            <button type="button" className="secondary-button" disabled={draft.clues.length >= 50} onClick={() => edit({ clues: [...draft.clues, ''] })}>添加线索</button>
            <label>汤底 · 完整真相<textarea required maxLength={8000} rows={5} value={draft.solution} onChange={(e) => edit({ solution: e.target.value })} placeholder="主持人参考的完整解释，揭晓前仅主持人可见。" /></label>
            <button className="primary-button" type="submit" disabled={!dirty}>保存汤稿</button>
          </fieldset>
        </form></details>}
      </div>
      <aside className="soup-preview"><h2>大屏内容预览</h2><p>与上屏内容一致，仅展示已公开内容。</p><TurtleSoupScreen data={preview} /><p>如新配对的大屏尚未显示内容，可重新同步当前展示。</p><button className="secondary-button" disabled={busy || !state} onClick={() => control(waiting ? 'RESET' : 'SYNC')}>同步当前展示</button></aside>
    </div></>}
  </div>
}

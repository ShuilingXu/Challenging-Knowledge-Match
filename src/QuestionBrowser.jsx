import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { filterQuestions, questionPage, selectQuestionResults } from './question-bank'

export function Pagination({ pageInfo, onPage, noun = '题目' }) {
  return <nav className="collection-pagination" aria-label={`${noun}分页`}>
    <span>共 {pageInfo.total} {noun === '题目' ? '道题目' : '个题组'} · 每页最多 10 {noun === '题目' ? '题' : '组'} · 第 {pageInfo.page} / {pageInfo.pages} 页</span>
    <div><button className="secondary-button" type="button" disabled={pageInfo.page <= 1} onClick={() => onPage(pageInfo.page - 1)}>上一页</button>
      <button className="secondary-button" type="button" disabled={pageInfo.page >= pageInfo.pages} onClick={() => onPage(pageInfo.page + 1)}>下一页</button></div>
  </nav>
}

/** Selection stays in the parent so searching and changing pages never drops checked IDs. */
export function QuestionBrowser({ questions, renderQuestion, empty, selectedIds, onSelection, label = '题库', onSearch }) {
  const [filters, setFilters] = useState({ query: '', scope: 'all', type: '', enabled: '' })
  const [page, setPage] = useState(1)
  const filtered = filterQuestions(questions, filters)
  const pageInfo = questionPage(filtered, page)
  useEffect(() => { setPage(pageInfo.page) }, [pageInfo.page])
  const change = (patch) => {
    const next = { ...filters, ...patch }
    setFilters(next); setPage(1); onSearch?.(next)
  }
  return <div className="question-browser">
    <div className="question-filter-toolbar">
      <div className="search-input"><Search size={17} /><input aria-label={`${label}搜索`} value={filters.query}
        onChange={(e) => change({ query: e.target.value })} placeholder="搜索标题、题目内容或答案" /></div>
      <label>搜索范围<select aria-label={`${label}搜索范围`} value={filters.scope} onChange={(e) => change({scope:e.target.value})}><option value="all">全部</option><option value="title">标题</option><option value="content">题目内容与选项</option><option value="answer">答案</option></select></label>
      <label>题型<select aria-label={`${label}题型`} value={filters.type} onChange={(e) => change({type:e.target.value})}><option value="">全部题型</option><option value="SINGLE">单选</option><option value="MULTIPLE">多选</option><option value="TEXT">文本</option></select></label>
      <label>状态<select aria-label={`${label}状态`} value={filters.enabled} onChange={(e) => change({enabled:e.target.value})}><option value="">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select></label>
    </div>
    {onSelection && <div className="collection-selection-actions"><span>已选 {selectedIds.length} 道（跨页保留）</span>
      <button className="secondary-button" type="button" disabled={!filtered.length} onClick={() => onSelection(selectQuestionResults(selectedIds, filtered))}>选择全部筛选结果（{filtered.length}）</button>
      <button className="text-button" type="button" disabled={!selectedIds.length} onClick={() => onSelection([])}>清空全部选择</button></div>}
    <div className="question-rows">{pageInfo.items.map((question, index) => renderQuestion(question, pageInfo.start + index))}
      {!filtered.length && (empty || <p className="collection-empty">没有匹配的题目，请更换关键词或筛选条件。</p>)}</div>
    <Pagination pageInfo={pageInfo} onPage={setPage} />
  </div>
}

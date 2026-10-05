export const QUESTION_PAGE_SIZE = 10
const typeNames = { SINGLE: '单选', MULTIPLE: '多选', TEXT: '文本 文字' }
const values = (value) => Array.isArray(value) ? value : value ? [value] : []
const normalize = (value) => String(value ?? '').normalize('NFKC').trim().toLowerCase()
const matches = (fields, query) => {
  const text = fields.flat().map(normalize).join(' ')
  return normalize(query).split(/\s+/).filter(Boolean).every((term) => text.includes(term))
}

export function questionSearchFields(question, scope = 'all') {
  const options = values(question.options)
  const answers = values(question.answers)
  const letters = answers.flatMap((answer) => {
    const index = options.indexOf(answer)
    return index >= 0 ? [String.fromCharCode(65 + index)] : []
  })
  const title = [question.title]
  const content = [...title, question.content, question.question, ...options]
  const correctOptions = answers.flatMap((answer) => {
    const index = /^[A-Z]$/i.test(answer) ? answer.toUpperCase().charCodeAt(0) - 65 : -1
    return index >= 0 && index < options.length ? [options[index]] : []
  })
  const answer = [...answers, ...letters, ...correctOptions, ...values(question.textAcceptedAnswers)]
  return scope === 'title' ? title : scope === 'content' ? content : scope === 'answer' ? answer
    : [...content, ...answer, question.type, typeNames[question.type]]
}

export function filterQuestions(questions, { query = '', scope = 'all', type = '', enabled = '' } = {}) {
  return questions.filter((question) => (!type || question.type === type)
    && (!enabled || (enabled === 'enabled' ? question.enabled !== false : question.enabled === false))
    && matches(questionSearchFields(question, scope), query))
}

export function filterQuestionSets(sets, questions, query = '', scope = 'all') {
  const byId = new Map(questions.map((question) => [question.id, question]))
  return sets.filter((set) => matches([
    ...(scope === 'answer' ? [] : [set.name, set.description]),
    ...(set.items || []).flatMap((item) => questionSearchFields(byId.get(item.questionId) || item, scope)),
  ], query))
}

export function questionPage(items, requestedPage = 1) {
  const pages = Math.max(1, Math.ceil(items.length / QUESTION_PAGE_SIZE))
  const page = Math.max(1, Math.min(pages, Number.isFinite(requestedPage) ? Math.trunc(requestedPage) : 1))
  const start = (page - 1) * QUESTION_PAGE_SIZE
  return { items: items.slice(start, start + QUESTION_PAGE_SIZE), page, pages, total: items.length, start }
}

export function selectQuestionResults(selectedIds, results) {
  return [...new Set([...selectedIds, ...results.map((item) => item.id)])]
}

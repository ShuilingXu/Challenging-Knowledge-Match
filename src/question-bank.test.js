import { describe, it, expect } from 'vitest'
import { filterQuestions, filterQuestionSets, questionPage, selectQuestionResults } from './question-bank'

const questions = [
  { id: 'one', title: '中国首都', type: 'SINGLE', options: ['上海', '北京'], answers: ['B'], enabled: true },
  { id: 'two', title: '城市简称', type: 'TEXT', textAcceptedAnswers: ['沪', 'Shanghai'], enabled: false },
  { id: 'three', title: '多选题', content: '选择颜色', type: 'MULTIPLE', options: ['红', '蓝', '绿'], answers: ['A', 'C'], enabled: true },
]
describe('question bank filtering and pagination', () => {
  it('searches question titles, content and options and normalizes case and fullwidth input', () => {
    expect(filterQuestions(questions, {query: '首都', scope: 'title'}).map(q=>q.id)).toEqual(['one'])
    expect(filterQuestions(questions, {query: '颜色', scope: 'content'}).map(q=>q.id)).toEqual(['three'])
    expect(filterQuestions(questions, {query: '上海', scope: 'content'}).map(q=>q.id)).toEqual(['one'])
    expect(filterQuestions(questions, {query: 'ＳＨＡＮＧＨＡＩ'}).map(q=>q.id)).toEqual(['two'])
  })
  it('searches accepted answers and correct option text without matching wrong answers', () => {
    expect(filterQuestions(questions, {query: '北京', scope: 'answer'}).map(q=>q.id)).toEqual(['one'])
    expect(filterQuestions(questions, {query: '上海', scope: 'answer'})).toEqual([])
    expect(filterQuestions(questions, {query: '沪', scope: 'answer'}).map(q=>q.id)).toEqual(['two'])
    expect(filterQuestions(questions, {query: '红 绿', scope: 'answer'}).map(q=>q.id)).toEqual(['three'])
    expect(filterQuestions(questions, {query: '蓝', scope: 'answer'})).toEqual([])
  })
  it('combines text, type and enabled filters', () => {
    expect(filterQuestions(questions, {query: '城市', type: 'TEXT', enabled: 'disabled'}).map(q=>q.id)).toEqual(['two'])
    expect(filterQuestions(questions, {type: 'TEXT', enabled: 'enabled'})).toEqual([])
  })
  it('matches question groups by name, question and answer', () => {
    const sets=[{id:'set',name:'初赛',items:[{questionId:'one',title:'中国首都'}]}]
    expect(filterQuestionSets(sets, questions, '初赛')).toHaveLength(1)
    expect(filterQuestionSets(sets, questions, '首都')).toHaveLength(1)
    expect(filterQuestionSets(sets, questions, '北京', 'answer')).toHaveLength(1)
    expect(filterQuestionSets(sets, questions, '上海', 'answer')).toHaveLength(0)
  })
  it('limits every page to ten and clamps pages after filters or deletion', () => {
    const rows=Array.from({length:23},(_,i)=>({id:String(i)}))
    expect(questionPage(rows,1).items).toHaveLength(10)
    expect(questionPage(rows,2).items[0].id).toBe('10')
    expect(questionPage(rows,3).items).toHaveLength(3)
    expect(questionPage(rows,999).page).toBe(3)
    expect(questionPage(rows.slice(0,2),3).page).toBe(1)
    expect(questionPage([],3)).toMatchObject({page:1,pages:1,total:0,items:[]})
  })
  it('keeps earlier selections when adding another page or filtered result without duplicates', () => {
    expect(selectQuestionResults(['one'], questions.slice(1))).toEqual(['one','two','three'])
    expect(selectQuestionResults(['one','two'], questions)).toEqual(['one','two','three'])
  })
})

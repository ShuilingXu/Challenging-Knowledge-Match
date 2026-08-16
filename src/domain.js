export const questions = [
  {
    id: 'q-1',
    number: '01',
    type: '单选题',
    title: '以下哪项技术最贴近“将数据转化为集体智能”的核心定义？',
    helper: '请在 30 秒内选择最合适的答案',
    options: [
      ['A', '云端备份与自动归档'],
      ['B', '机器学习模型持续从数据中发现模式'],
      ['C', '通过压缩降低存储成本'],
      ['D', '以规则引擎替代人的决策'],
    ],
    answer: 'B',
  },
  {
    id: 'q-2',
    number: '02',
    type: '多选题',
    title: '构建可信赖的数据产品时，哪些原则应被优先考虑？',
    helper: '本题有多个正确选项',
    options: [
      ['A', '数据最小化'],
      ['B', '可追溯的决策过程'],
      ['C', '默认公开所有原始数据'],
      ['D', '清晰的用户告知与授权'],
    ],
    answer: ['A', 'B', 'D'],
  },
]

export const participants = [
  { rank: 1, name: '陈澈', initials: 'CC', team: 'NOVA Lab', score: 860, color: '#ff7657' },
  { rank: 2, name: '叶思远', initials: 'YS', team: 'Wavelength', score: 820, color: '#605de6' },
  { rank: 3, name: '许知行', initials: 'XZ', team: 'Aperture', score: 760, color: '#1eaa95' },
  { rank: 4, name: '林见山', initials: 'LJ', team: 'Prism', score: 710, color: '#eead3a' },
  { rank: 5, name: '宋柚', initials: 'SY', team: 'Aperture', score: 690, color: '#df5b91' },
]

export const activityStats = [
  { label: '报名参与者', value: '1,284', trend: '+12.8%', icon: 'users' },
  { label: '实时在线', value: '936', trend: '72.9%', icon: 'signal' },
  { label: '平均正确率', value: '68.4', suffix: '%', trend: '+4.2%', icon: 'chart' },
  { label: '待核销奖品', value: '87', trend: '14 今日', icon: 'gift' },
]

export function scoreAnswer(question, answer) {
  if (Array.isArray(question.answer)) {
    const submitted = Array.isArray(answer) ? answer.slice().sort() : []
    const expected = question.answer.slice().sort()
    if (submitted.length === expected.length && submitted.every((item, index) => item === expected[index])) return 100
    const containsOnlyCorrectSelections = submitted.every((item) => expected.includes(item))
    return submitted.length > 0 && containsOnlyCorrectSelections ? 40 : 0
  }
  return answer === question.answer ? 100 : 0
}

export function formatSeconds(seconds) {
  return `00:${String(Math.max(0, seconds)).padStart(2, '0')}`
}

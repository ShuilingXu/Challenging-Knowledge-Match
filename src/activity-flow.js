export const activityStatusLabels = {
  DRAFT: '草稿', REGISTRATION_OPEN: '报名中', LIVE: '进行中', PAUSED: '已暂停',
  FINISHED: '已结束', CANCELLED: '已终止',
}

export function activityActions(status, type) {
  if (type === 'EVENT' && status === 'DRAFT') return [['LIVE', '启用母活动']]
  if (['QUIZ', 'LOTTERY', 'TURTLE_SOUP'].includes(type) && status === 'DRAFT') return [['LIVE', '开始活动']]
  return {
    DRAFT: [['REGISTRATION_OPEN', '启用报名'], ['LIVE', '开始活动']],
    REGISTRATION_OPEN: [['LIVE', '开始活动']],
    LIVE: [['PAUSED', '暂停'], ['FINISHED', '结束']],
    PAUSED: [['LIVE', '恢复'], ['FINISHED', '结束']],
  }[status] || []
}

export function participantActivityId(activity) {
  if (activity?.participantActivityId) return activity.participantActivityId
  return activity?.activityType === 'LOTTERY' && activity.parentActivityId
    ? activity.parentActivityId : activity?.id
}

export function availableActivities(activities) {
  const open = (item) => ['REGISTRATION_OPEN', 'LIVE', 'PAUSED'].includes(item?.status)
  const ancestorsOpen = (item, seen = new Set()) => {
    if (!item.parentActivityId) return true
    if (seen.has(item.id)) return false
    seen.add(item.id)
    const parent = activities.find((entry) => entry.id === item.parentActivityId)
    // Public API hides mother activities; venue status remains the public gate.
    return parent ? open(parent) && ancestorsOpen(parent, seen) : item.activityType === 'VENUE'
  }
  return activities.filter((item) => item.featureEnabled !== false && !['EVENT', 'TURTLE_SOUP'].includes(item.activityType) && open(item) && ancestorsOpen(item))
}

export function activityEntry(activity) {
  return `${activity.activityType === 'LOTTERY' ? '/lottery' : '/join'}/${activity.id}`
}

// Public lists intentionally omit the mother event; its venues are still entry roots.
export function activityEntryGroups(activities) {
  const ids = new Set(activities.map((item) => item.id))
  return activities.filter((item) => !item.parentActivityId || !ids.has(item.parentActivityId))
    .map((activity) => ({ activity, features: activities.filter((item) => item.parentActivityId === activity.id) }))
}

export function participantFeatureLabel(activity) {
  const label = { QUIZ: '答题', LOTTERY: '抽奖', TURTLE_SOUP: '海龟汤' }[activity?.activityType]
  return !label ? activity?.name || '本会场' : !activity.name || activity.name === label ? label : `${label} · ${activity.name}`
}

export function participationLabel(activity, activities) {
  if (activity.activityType === 'VENUE') return `会场 · ${activity.name}`
  const parent = activities.find((item) => item.id === activity.parentActivityId)
  return `${parent?.name || '本会场'} / ${participantFeatureLabel(activity)}`
}

export function controlStatusHint(status) {
  return {
    DRAFT: '请先点击“开始活动”，再开题并计时。',
    REGISTRATION_OPEN: '报名已开放，请点击“开始活动”进入答题。',
    PAUSED: '活动已暂停，请先恢复活动再开题。',
    FINISHED: '活动已结束，不能再开启答题。',
    CANCELLED: '活动已终止，不能再开启答题。',
  }[status] || ''
}

export function screenAnswerHint(result, remaining, stage) {
  if (result) return '答案已公布，请等待主持人开启下一题'
  if (stage === 'QUESTION_OPEN') return remaining > 0 ? '请在倒计时结束前，用手机提交答案' : '本题答题已结束，请等待主持人公布答案'
  return '请等待主持人开启答题'
}

// 答题、抢答与海龟汤大屏常驻加入二维码：迟到的观众也能随时扫码入场。
export function screenJoinPrompt(mode) {
  if (mode === 'TURTLE_SOUP') return { caption: '扫码加入', hint: '手机扫码一起猜汤' }
  if (mode === 'QUESTION' || mode === 'RESULT') return { caption: '扫码加入', hint: '手机扫码答题 / 抢答' }
  return null
}

export function screenJoinUrl(origin, activityId) {
  const base = String(origin || '').replace(/\/+$/, '')
  return `${base}/join/${activityId}`
}

export function venueDisplayName(code, venues = []) {
  return venues.find((venue) => venue.code === code)?.name || (code === 'main' ? '本会场' : code || '本会场')
}

export function participantMatches(person, query) {
  const values = [person.name, person.contact, person.id, person.organization, person.venue,
    ...Object.values(person.customFields || {})]
  return values.join(' ').toLowerCase().includes(query.trim().toLowerCase())
}

export function activityNavigation(activity) {
  const type = activity?.activityType || 'EVENT'
  const main = type === 'EVENT'
  const lottery = type === 'LOTTERY'
  const soup = type === 'TURTLE_SOUP'
  if (type === 'VENUE' && Array.isArray(activity.features)) return {
    showQuiz: activity.features.includes('QUIZ'), showControl: activity.features.includes('QUIZ'),
    showRewards: activity.features.includes('QUIZ') || activity.features.includes('LOTTERY'),
    controlLabel: '答题控场', rewardsLabel: '奖品与核销',
  }
  return {
    showQuiz: Boolean(!soup && !lottery),
    showRewards: Boolean(!soup && !main && type !== 'VENUE'),
    showControl: soup || lottery || Boolean(!main && type !== 'VENUE'),
    controlLabel: soup ? '海龟汤控场' : lottery ? '摇奖控场' : main ? '原有答题控场' : '答题控场',
    rewardsLabel: lottery ? '自助抽奖与核销' : '奖品与核销',
  }
}

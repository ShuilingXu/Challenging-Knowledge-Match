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

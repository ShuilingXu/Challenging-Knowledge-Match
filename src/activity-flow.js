export const activityStatusLabels = {
  DRAFT: '草稿', REGISTRATION_OPEN: '报名中', LIVE: '进行中', PAUSED: '已暂停',
  FINISHED: '已结束', CANCELLED: '已终止',
}

export function activityActions(status) {
  return {
    DRAFT: [['REGISTRATION_OPEN', '启用报名'], ['LIVE', '开始活动']],
    REGISTRATION_OPEN: [['LIVE', '开始活动']],
    LIVE: [['PAUSED', '暂停'], ['FINISHED', '结束']],
    PAUSED: [['LIVE', '恢复'], ['FINISHED', '结束']],
  }[status] || []
}

export function participantActivityId(activity) {
  return activity?.activityType === 'LOTTERY' && activity.parentActivityId
    ? activity.parentActivityId : activity?.id
}

export function availableActivities(activities) {
  const open = (item) => ['REGISTRATION_OPEN', 'LIVE', 'PAUSED'].includes(item?.status)
  return activities.filter((item) => open(item) && (!item.parentActivityId
    || open(activities.find((parent) => parent.id === item.parentActivityId))))
}

export function activityEntry(activity) {
  return `${activity.activityType === 'LOTTERY' ? '/lottery' : '/join'}/${activity.id}`
}

export function participantMatches(person, query) {
  const values = [person.name, person.contact, person.id, person.organization, person.venue,
    ...Object.values(person.customFields || {})]
  return values.join(' ').toLowerCase().includes(query.trim().toLowerCase())
}

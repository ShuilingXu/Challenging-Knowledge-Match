const TOKEN_KEY = 'matrix.access-token'
const PARTICIPANT_TOKEN_PREFIX = 'matrix.participant-token.'
const SCREEN_TOKEN_PREFIX = 'matrix.screen-token.'

export class ApiError extends Error {
  constructor(message, status, details) {
    super(message)
    this.status = status
    this.details = details
  }
}

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return null
}

export function setSession(session) {
  localStorage.setItem(TOKEN_KEY, session.accessToken)
  localStorage.setItem('matrix.identity', JSON.stringify(normalizeIdentity(session)))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('matrix.identity')
}

export function getStoredIdentity() {
  try {
    return JSON.parse(localStorage.getItem('matrix.identity') || 'null')
  } catch {
    return null
  }
}

export function getParticipantToken(activityId) {
  return sessionStorage.getItem(`${PARTICIPANT_TOKEN_PREFIX}${activityId}`)
}

export function setParticipantToken(activityId, token) {
  sessionStorage.setItem(`${PARTICIPANT_TOKEN_PREFIX}${activityId}`, token)
}

export function getScreenSession(activityId, deviceId) {
  try {
    return JSON.parse(sessionStorage.getItem(`${SCREEN_TOKEN_PREFIX}${activityId}.${deviceId}`) || 'null')
  } catch {
    return null
  }
}

export function setScreenSession(activityId, deviceId, session) {
  sessionStorage.setItem(`${SCREEN_TOKEN_PREFIX}${activityId}.${deviceId}`, JSON.stringify(session))
}

function normalizeIdentity(session) {
  if (session.user) return session.user
  return {
    id: session.userId,
    username: session.username,
    displayName: session.displayName || session.username,
    role: session.systemRole,
  }
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const message = typeof body === 'object' ? body.message || body.error || '请求未成功' : body || '请求未成功'
    throw new ApiError(message, response.status, body)
  }
  return body
}

export async function request(path, { method = 'GET', body, auth = true, retry = true, headers = {} } = {}) {
  const token = getAccessToken()
  const requestHeaders = { Accept: 'application/json', ...headers }
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'
  if (auth && token) requestHeaders.Authorization = `Bearer ${token}`
  const response = await fetch(path, { method, headers: requestHeaders, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'include' })
  if (response.status === 401 && auth && retry && getAccessToken()) {
    await refreshSession()
    return request(path, { method, body, auth, retry: false, headers })
  }
  return parseResponse(response)
}

export async function login(email, password) {
  const session = await request('/api/auth/login', { method: 'POST', auth: false, body: { username: email, password } })
  setSession(session)
  return { ...session, user: normalizeIdentity(session) }
}

export async function refreshSession() {
  const session = await request('/api/auth/refresh', { method: 'POST', auth: false, body: {} })
  setSession(session)
  return { ...session, user: normalizeIdentity(session) }
}

export async function logout() {
  try {
    await request('/api/auth/logout', { method: 'POST', auth: false, body: {} })
  } finally {
    clearSession()
  }
}

export const api = {
  siteSettings: () => request('/api/site-settings', { auth: false }),
  updateSiteSettings: (payload) => request('/api/admin/site-settings', { method: 'PATCH', body: payload }),
  activities: () => request('/api/activities'),
  createActivity: (payload) => request('/api/activities', { method: 'POST', body: payload }),
  activity: (id) => request(`/api/activities/${id}`),
  updateActivity: (id, payload) => request(`/api/activities/${id}`, { method: 'PATCH', body: payload }),
  changeActivityStatus: (id, status) => request(`/api/activities/${id}/status`, { method: 'POST', body: { status } }),
  terminateActivity: (id) => request(`/api/activities/${id}`, { method: 'DELETE' }),
  venues: (id) => request(`/api/activities/${id}/venues`, { auth: false }),
  createVenue: (id, payload) => request(`/api/activities/${id}/venues`, { method: 'POST', body: payload }),
  updateVenue: (id, venueId, payload) => request(`/api/activities/${id}/venues/${venueId}`, { method: 'PATCH', body: payload }),
  deleteVenue: (id, venueId) => request(`/api/activities/${id}/venues/${venueId}`, { method: 'DELETE' }),
  registrationFields: (id) => request(`/api/activities/${id}/registration-fields`, { auth: false }),
  createRegistrationField: (id, payload) => request(`/api/activities/${id}/registration-fields`, { method: 'POST', body: payload }),
  updateRegistrationField: (id, fieldId, payload) => request(`/api/activities/${id}/registration-fields/${fieldId}`, { method: 'PATCH', body: payload }),
  deleteRegistrationField: (id, fieldId) => request(`/api/activities/${id}/registration-fields/${fieldId}`, { method: 'DELETE' }),
  register: (id, venue, payload) => request(`/api/activities/${id}/venues/${venue}/registrations`, { method: 'POST', body: payload, auth: false }),
  participants: (id, filters = '') => {
    if (typeof filters === 'string') return request(`/api/activities/${id}/participants${filters}`)
    const params = new URLSearchParams()
    if (filters?.venue) params.set('venue', filters.venue)
    if (filters?.query) params.set('query', filters.query)
    const suffix = params.size ? `?${params.toString()}` : ''
    return request(`/api/activities/${id}/participants${suffix}`)
  },
  participant: (id, participantId) => request(`/api/activities/${id}/participants/${participantId}`),
  updateParticipant: (id, participantId, payload) => request(`/api/activities/${id}/participants/${participantId}`, { method: 'PATCH', body: payload }),
  participantToken: (payload) => request('/api/auth/participant-token', { method: 'POST', auth: false, body: payload }),
  questions: (id, participantToken) => request(`/api/activities/${id}/questions`, { auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  questionsAdmin: (id) => request(`/api/activities/${id}/questions/admin`),
  createQuestion: (id, payload) => request(`/api/activities/${id}/questions`, { method: 'POST', body: payload }),
  updateQuestion: (id, questionId, payload) => request(`/api/activities/${id}/questions/${questionId}`, { method: 'PUT', body: payload }),
  deleteQuestion: (id, questionId) => request(`/api/activities/${id}/questions/${questionId}`, { method: 'DELETE' }),
  answer: (id, payload, participantToken) => request(`/api/activities/${id}/answers`, { method: 'POST', body: payload, auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  submissions: (id, participantId) => request(`/api/activities/${id}/participants/${participantId}/submissions`),
  gradeSubmission: (id, submissionId, payload) => request(`/api/activities/${id}/submissions/${submissionId}/grade`, { method: 'POST', body: payload }),
  scoreLedger: (id, participantId) => request(`/api/activities/${id}/participants/${participantId}/score-ledger`),
  adjustScore: (id, payload) => request(`/api/activities/${id}/scores/adjustments`, { method: 'POST', body: payload }),
  scoreboard: (id, participantToken) => request(`/api/activities/${id}/scoreboard`, { auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  controlState: (id, participantToken) => request(`/api/activities/${id}/control`, { auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  control: (id, payload) => request(`/api/activities/${id}/control`, { method: 'POST', body: payload }),
  prizePools: (id) => request(`/api/activities/${id}/prize-pools`),
  createPrizePool: (id, payload) => request(`/api/activities/${id}/prize-pools`, { method: 'POST', body: payload }),
  updatePrizePool: (id, poolId, payload) => request(`/api/activities/${id}/prize-pools/${poolId}`, { method: 'PATCH', body: payload }),
  deletePrizePool: (id, poolId) => request(`/api/activities/${id}/prize-pools/${poolId}`, { method: 'DELETE' }),
  awards: (id, participantId, participantToken) => request(`/api/activities/${id}/awards?participantId=${participantId}`, { auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  awardsAdmin: (id, status = '') => request(`/api/activities/${id}/awards/admin${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  issueAward: (id, payload) => request(`/api/activities/${id}/awards`, { method: 'POST', body: payload }),
  issueRankingAwards: (id, poolId) => request(`/api/activities/${id}/prize-pools/${poolId}/ranking-awards`, { method: 'POST' }),
  grantLotteryChances: (id, participantId, payload) => request(`/api/activities/${id}/participants/${participantId}/lottery-chances`, { method: 'POST', body: payload }),
  lotteryChance: (id, participantId, participantToken) => request(`/api/activities/${id}/participants/${participantId}/lottery-chances`, { auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  draw: (id, payload, participantToken) => request(`/api/activities/${id}/draws`, { method: 'POST', body: payload, auth: !participantToken, headers: participantToken ? { Authorization: `Bearer ${participantToken}` } : {} }),
  redeem: (id, awardId) => request(`/api/activities/${id}/awards/${awardId}/redeem`, { method: 'POST' }),
  reverseRedemption: (id, awardId) => request(`/api/activities/${id}/awards/${awardId}/reverse-redemption`, { method: 'POST' }),
  voidAward: (id, awardId, payload) => request(`/api/activities/${id}/awards/${awardId}/void`, { method: 'POST', body: payload }),
  templates: (id) => request(`/api/activities/${id}/screens/templates`),
  template: (id, templateId) => request(`/api/activities/${id}/screens/templates/${templateId}`),
  createTemplate: (id, payload) => request(`/api/activities/${id}/screens/templates`, { method: 'POST', body: payload }),
  updateTemplate: (id, templateId, payload) => request(`/api/activities/${id}/screens/templates/${templateId}`, { method: 'PUT', body: payload }),
  deleteTemplate: (id, templateId) => request(`/api/activities/${id}/screens/templates/${templateId}`, { method: 'DELETE' }),
  applyTemplate: (id, templateId, payload) => request(`/api/activities/${id}/screens/templates/${templateId}/apply`, { method: 'POST', body: payload }),
  devices: (id) => request(`/api/activities/${id}/screens/devices`),
  registerScreen: (id, payload) => request(`/api/activities/${id}/screens/devices/register`, { method: 'POST', body: payload }),
  renameScreen: (id, deviceId, payload) => request(`/api/activities/${id}/screens/devices/${deviceId}`, { method: 'PATCH', body: payload }),
  rotateScreenPairing: (id, deviceId) => request(`/api/activities/${id}/screens/devices/${deviceId}/pairing-token`, { method: 'POST' }),
  updateScreenSettings: (id, deviceId, payload) => request(`/api/activities/${id}/screens/devices/${deviceId}/settings`, { method: 'PUT', body: payload }),
  setScreenDisplay: (id, deviceId, payload) => request(`/api/activities/${id}/screens/devices/${deviceId}/display`, { method: 'PUT', body: payload }),
  exchangeScreenPairing: (id, deviceId, pairingToken) => request(`/api/activities/${id}/screens/devices/${deviceId}/session`, { method: 'POST', auth: false, headers: { 'X-Screen-Pairing': pairingToken } }),
  screenState: (id, deviceId, deviceToken) => request(`/api/activities/${id}/screens/devices/${deviceId}/state`, { auth: false, headers: { Authorization: `Bearer ${deviceToken}` } }),
  screenHeartbeat: (id, deviceId, deviceToken, payload) => request(`/api/activities/${id}/screens/devices/${deviceId}/heartbeat`, { method: 'POST', body: payload, auth: false, headers: { Authorization: `Bearer ${deviceToken}` } }),
  users: () => request('/api/admin/users'),
  createUser: (payload) => request('/api/admin/users', { method: 'POST', body: payload }),
  setUserEnabled: (userId, enabled) => request(`/api/admin/users/${userId}/enabled`, { method: 'PATCH', body: { enabled } }),
  memberships: (id) => request(`/api/activities/${id}/memberships`),
  upsertMembership: (id, payload) => request(`/api/activities/${id}/memberships`, { method: 'POST', body: payload }),
  createMembershipUser: (id, payload) => request(`/api/activities/${id}/memberships/users`, { method: 'POST', body: payload }),
  deleteMembership: (id, userId) => request(`/api/activities/${id}/memberships/${userId}`, { method: 'DELETE' }),
}

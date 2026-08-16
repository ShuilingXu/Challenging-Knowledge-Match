const RESERVED_REGISTRATION_KEYS = new Set(['name', 'contact', 'organization', 'venue'])

export function registrationFieldKey(field) {
  return field?.fieldKey || field?.key || ''
}

export function activeVenueCode(venues, requestedCode) {
  const activeVenues = (venues || []).filter((venue) => venue?.enabled)
  if (activeVenues.some((venue) => venue.code === requestedCode)) return requestedCode
  return activeVenues[0]?.code || ''
}

export function splitRegistrationOptions(value) {
  return String(value || '').split('\n').map((option) => option.trim()).filter(Boolean)
}

export function buildRegistrationPayload(values, customValues) {
  const customFields = Object.fromEntries(
    Object.entries(customValues || {})
      .filter(([key, value]) => key && !RESERVED_REGISTRATION_KEYS.has(key) && String(value || '').trim())
      .map(([key, value]) => [key, String(value).trim()]),
  )
  return {
    name: values.name,
    contact: values.contact,
    organization: values.organization || null,
    customFields,
  }
}

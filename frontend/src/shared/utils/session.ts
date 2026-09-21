const TOKEN_KEY = 'stockpilot.accessToken'
const EXPIRES_AT_KEY = 'stockpilot.expiresAt'

export function getAccessToken(): string | null {
  const token = localStorage.getItem(TOKEN_KEY)
  const expiresAt = localStorage.getItem(EXPIRES_AT_KEY)
  if (!token || !expiresAt || Date.parse(expiresAt) <= Date.now()) {
    clearSession()
    return null
  }
  return token
}

export function saveSession(accessToken: string, expiresAt: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(EXPIRES_AT_KEY, expiresAt)
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
}

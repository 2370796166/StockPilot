// Keep only a user-bound opaque session ID in this browser tab. Facts stay on the server.
export function loadSession(userId: number): string | null {
  try {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem(`stockpilot.agent.${userId}`)
  } catch {
    return null
  }
}
export function saveSession(userId: number, id: string) {
  try {
    if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(`stockpilot.agent.${userId}`, id)
  } catch {
    /* storage may be disabled */
  }
}
export function forgetSession(userId: number) {
  try {
    if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(`stockpilot.agent.${userId}`)
  } catch {
    /* storage may be disabled */
  }
}

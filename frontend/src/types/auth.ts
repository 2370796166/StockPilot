export interface LoginRequest {
  username: string
  password: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
}

export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  roles: string[]
  authorities: string[]
}

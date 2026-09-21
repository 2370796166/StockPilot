import type { DataStatus } from '@/shared/types/api'
export interface UserRecord {
  id: number
  username: string
  displayName: string
  status: DataStatus
  roleIds: number[]
  createdAt: string
  updatedAt: string
  version: number
}
export interface RoleRecord {
  id: number
  code: string
  name: string
  status: DataStatus
  permissionIds: number[]
  createdAt: string
  updatedAt: string
  version: number
}
export interface PermissionRecord {
  id: number
  code: string
  name: string
  description: string | null
  status: DataStatus
  createdAt: string
  updatedAt: string
  version: number
}

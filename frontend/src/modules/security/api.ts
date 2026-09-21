import type { DataStatus, PageResult } from '@/shared/types/api'
import type { PermissionRecord, RoleRecord, UserRecord } from '@/modules/security/types'
import { request } from '@/shared/utils/request'
export const pageUsers = (page: number, size: number) =>
  request<PageResult<UserRecord>>({ method: 'GET', url: '/security/users', params: { page, size } })
export const createUser = (data: { username: string; password: string; displayName: string }) =>
  request<UserRecord>({ method: 'POST', url: '/security/users', data })
export const updateUser = (id: number, data: { displayName: string; newPassword?: string; version: number }) =>
  request<UserRecord>({ method: 'PUT', url: `/security/users/${id}`, data })
export const setUserStatus = (id: number, status: DataStatus, version: number) =>
  request<UserRecord>({ method: 'PATCH', url: `/security/users/${id}/status`, data: { status, version } })
export const setUserRoles = (id: number, ids: number[]) =>
  request<UserRecord>({ method: 'PUT', url: `/security/users/${id}/roles`, data: { ids } })
export const listRoles = () => request<RoleRecord[]>({ method: 'GET', url: '/security/roles' })
export const createRole = (data: { code: string; name: string }) =>
  request<RoleRecord>({ method: 'POST', url: '/security/roles', data })
export const updateRole = (id: number, data: { name: string; version: number }) =>
  request<RoleRecord>({ method: 'PUT', url: `/security/roles/${id}`, data })
export const setRoleStatus = (id: number, status: DataStatus, version: number) =>
  request<RoleRecord>({ method: 'PATCH', url: `/security/roles/${id}/status`, data: { status, version } })
export const setRolePermissions = (id: number, ids: number[]) =>
  request<RoleRecord>({ method: 'PUT', url: `/security/roles/${id}/permissions`, data: { ids } })
export const listPermissions = () => request<PermissionRecord[]>({ method: 'GET', url: '/security/permissions' })

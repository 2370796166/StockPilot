<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn as TableColumn,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/checkbox-group/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, listRoles, pageUsers, setUserRoles, setUserStatus, updateUser } from '@/modules/security/api'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import type { RoleRecord, UserRecord } from '@/modules/security/types'
import type { DataStatus } from '@/shared/types/api'
const loading = ref(false),
  records = ref<UserRecord[]>([]),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  dialog = ref(false),
  grantDialog = ref(false),
  saving = ref(false),
  editing = ref<UserRecord | null>(null),
  granting = ref<UserRecord | null>(null),
  roles = ref<RoleRecord[]>([])
const form = reactive({ username: '', displayName: '', password: '', newPassword: '' }),
  roleIds = ref<number[]>([])
const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const r = await pageUsers(page.value, size.value)
    if (!listRequest.isCurrent(sequence)) return
    records.value = r.records
    total.value = r.total
  } catch {
    // Request errors are displayed by the shared interceptor.
  } finally {
    if (listRequest.isCurrent(sequence)) loading.value = false
  }
}
function openCreate() {
  editing.value = null
  Object.assign(form, { username: '', displayName: '', password: '', newPassword: '' })
  dialog.value = true
}
function edit(r: UserRecord) {
  editing.value = r
  Object.assign(form, { username: r.username, displayName: r.displayName, password: '', newPassword: '' })
  dialog.value = true
}
async function save() {
  if (
    saving.value ||
    !form.displayName.trim() ||
    (!editing.value && (!form.username.match(/^[A-Za-z][A-Za-z0-9_.-]{2,63}$/) || !form.password))
  ) {
    ElMessage.warning('请检查用户名、显示名和密码')
    return
  }
  saving.value = true
  try {
    if (editing.value)
      await updateUser(editing.value.id, {
        displayName: form.displayName,
        newPassword: form.newPassword || undefined,
        version: editing.value.version,
      })
    else await createUser({ username: form.username, password: form.password, displayName: form.displayName })
    ElMessage.success('用户已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
async function status(r: UserRecord) {
  const next: DataStatus = r.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(`确认${next === 'ENABLED' ? '启用' : '停用'}用户 ${r.username}？`, '确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await setUserStatus(r.id, next, r.version)
  await load()
}
async function grant(r: UserRecord) {
  granting.value = r
  roleIds.value = [...r.roleIds]
  if (!roles.value.length) roles.value = await listRoles()
  grantDialog.value = true
}
async function saveRoles() {
  if (!granting.value) return
  saving.value = true
  try {
    await setUserRoles(granting.value.id, roleIds.value)
    ElMessage.success('角色已更新')
    grantDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
const ElTableColumn = TableColumn<UserRecord>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">系统管理</p>
        <h1>用户管理</h1>
        <p>管理用户账号、启停状态和角色授权</p>
      </div>
      <PermissionGate authority="SECURITY_USER_WRITE"
        ><el-button
          type="primary"
          @click="openCreate"
          >新增用户</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="table-card"
      ><el-table
        v-loading="loading"
        :data="records"
        ><el-table-column
          prop="username"
          label="用户名"
        /><el-table-column
          prop="displayName"
          label="显示名"
        /><el-table-column label="状态"
          ><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column
        ><el-table-column label="角色数"
          ><template #default="s">{{ s.row.roleIds.length }}</template></el-table-column
        ><el-table-column
          prop="createdAt"
          label="创建时间"
        /><el-table-column
          label="操作"
          width="220"
          ><template #default="s"
            ><PermissionGate authority="SECURITY_USER_WRITE"
              ><el-button
                link
                type="primary"
                @click="edit(s.row)"
                >编辑</el-button
              ><el-button
                link
                type="danger"
                @click="status(s.row)"
                >{{ s.row.status === 'ENABLED' ? '停用' : '启用' }}</el-button
              ></PermissionGate
            ><PermissionGate authority="SECURITY_GRANT"
              ><el-button
                link
                type="primary"
                @click="grant(s.row)"
                >分配角色</el-button
              ></PermissionGate
            ></template
          ></el-table-column
        ></el-table
      ><ServerPagination
        v-model:page="page"
        v-model:size="size"
        :total="total"
        @change="load" /></el-card
    ><el-dialog
      v-model="dialog"
      :title="editing ? '编辑用户' : '新增用户'"
      width="500px"
      ><el-form label-position="top"
        ><el-form-item label="用户名"
          ><el-input
            v-model="form.username"
            :disabled="!!editing" /></el-form-item
        ><el-form-item label="显示名"
          ><el-input
            v-model="form.displayName"
            maxlength="100" /></el-form-item
        ><el-form-item
          v-if="editing"
          label="新密码（留空不修改）"
          ><el-input
            v-model="form.newPassword"
            maxlength="100"
            type="password"
            show-password /></el-form-item
        ><el-form-item
          v-else
          label="密码"
          ><el-input
            v-model="form.password"
            maxlength="100"
            type="password"
            show-password /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    ><el-dialog
      v-model="grantDialog"
      title="分配角色"
      width="500px"
      ><el-checkbox-group v-model="roleIds"
        ><el-checkbox
          v-for="r in roles"
          :key="r.id"
          :value="r.id"
          :disabled="r.status !== 'ENABLED'"
          >{{ r.code }} · {{ r.name }}</el-checkbox
        ></el-checkbox-group
      ><template #footer
        ><el-button @click="grantDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="saveRoles"
          >保存授权</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>

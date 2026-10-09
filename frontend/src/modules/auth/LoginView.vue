<script setup lang="ts">
import { ElButton, ElForm, ElFormItem, ElIcon, ElInput } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Box } from '@element-plus/icons-vue'
import { useAuthStore } from '@/modules/auth/store'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '用户名不能超过 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 100, message: '密码不能超过 100 个字符', trigger: 'blur' },
  ],
}

async function submit() {
  if (auth.loading) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
  } catch {
    // The shared request interceptor displays the error; keep the form available for retry.
    return
  }
  const redirect =
    typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/dashboard'
  await router.replace(redirect)
}
</script>
<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="login-brand">
        <div class="login-logo">
          <el-icon><Box /></el-icon>
        </div>
        <strong>StockPilot</strong><span>智能仓储与库存管理</span>
      </div>
      <h1>让每一笔库存变化<br />都有据可循</h1>
      <p>面向采购、仓储和销售协作的库存管理平台。</p>
      <div class="login-capabilities">
        <div><span>采购入库</span><span>销售出库</span></div>
        <div><span>多仓调拨</span><span>库存盘点</span></div>
      </div>
      <small class="login-intro-foot">从业务单据到库存流水，全程可追溯</small>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-login-brand">
          <el-icon><Box /></el-icon><strong>StockPilot</strong>
        </div>
        <p class="eyebrow">管理后台</p>
        <h2>欢迎回来</h2>
        <p class="muted">使用 StockPilot 账号登录</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="submit"
        >
          <el-form-item
            label="用户名"
            prop="username"
            ><el-input
              v-model="form.username"
              autocomplete="username"
              placeholder="请输入用户名"
          /></el-form-item>
          <el-form-item
            label="密码"
            prop="password"
            ><el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              show-password
              placeholder="请输入密码"
          /></el-form-item>
          <el-button
            type="primary"
            :loading="auth.loading"
            class="login-submit"
            @click="submit"
            >登录</el-button
          >
        </el-form>
        <p class="login-help">如需开通账号或调整权限，请联系管理员。</p>
      </div>
    </section>
  </main>
</template>

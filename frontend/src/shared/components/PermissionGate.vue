<script setup lang="ts">
import { useAuthStore } from '@/modules/auth/store'
// 权限门只负责隐藏当前用户无权使用的界面元素，不能替代后端接口授权。
defineProps<{ authority: string }>()
const auth = useAuthStore()
</script>
<template><slot v-if="auth.can(authority)" /></template>

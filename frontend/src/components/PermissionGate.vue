<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
defineProps<{ authority: string }>()
const auth = useAuthStore()
</script>
<template><slot v-if="auth.can(authority)" /></template>

<script setup lang="ts">
defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ 'update:page': [value: number]; 'update:size': [value: number]; change: [] }>()
function changePage(value: number) {
  emit('update:page', value)
  emit('change')
}
function changeSize(value: number) {
  emit('update:size', value)
  emit('update:page', 1)
  emit('change')
}
</script>
<template>
  <div class="pagination-wrap">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :current-page="page"
      :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @update:current-page="changePage"
      @update:page-size="changeSize"
    />
  </div>
</template>

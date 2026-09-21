<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { resolveReferenceLabel } from '@/modules/master-data/api'
import type { MasterDataResource } from '@/modules/master-data/types'
const cache = new Map<string, Promise<string>>()
const props = defineProps<{ resource: MasterDataResource | 'locations'; id: number }>()
const label = ref(`#${props.id}`)
async function load() {
  const key = `${props.resource}:${props.id}`
  label.value = `#${props.id}`
  let pending = cache.get(key)
  if (!pending) {
    pending = resolveReferenceLabel(props.resource, props.id)
    cache.set(key, pending)
  }
  try {
    label.value = await pending
  } catch {
    cache.delete(key)
    label.value = `#${props.id}`
  }
}
watch(() => [props.resource, props.id], load)
onMounted(load)
</script>
<template>
  <span>{{ label }}</span>
</template>

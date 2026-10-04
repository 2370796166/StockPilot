<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { resolveReferenceLabel } from '@/modules/master-data/reference-options'
import type { MasterDataResource } from '@/modules/master-data/types'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
const props = defineProps<{ resource: MasterDataResource | 'locations'; id: number }>()
const label = ref(`#${props.id}`)
const request = useLatestRequest()
async function load() {
  const sequence = request.next()
  label.value = `#${props.id}`
  try {
    const resolved = await resolveReferenceLabel(props.resource, props.id)
    if (request.isCurrent(sequence)) label.value = resolved
  } catch {
    if (request.isCurrent(sequence)) label.value = `#${props.id}`
  }
}
watch(() => [props.resource, props.id], load)
onMounted(load)
</script>
<template>
  <span>{{ label }}</span>
</template>

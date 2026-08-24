<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getLocation, getMasterData, getSku } from '../api/masterData'
import type { MasterDataResource } from '../types/masterData'
const cache = new Map<string, Promise<string>>()
const props = defineProps<{ resource: MasterDataResource | 'locations'; id: number }>()
const label = ref(`#${props.id}`)
async function load() { const key = `${props.resource}:${props.id}`; let pending = cache.get(key); if (!pending) { pending = (async () => { const item = props.resource === 'locations' ? await getLocation(props.id) : props.resource === 'skus' ? await getSku(props.id) : await getMasterData(props.resource, props.id); return `${item.code} · ${item.name}` })(); cache.set(key, pending) } try { label.value = await pending } catch { cache.delete(key); label.value = `#${props.id}` } }
watch(() => [props.resource, props.id], load)
onMounted(load)
</script>
<template><span>{{ label }}</span></template>

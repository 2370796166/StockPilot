import { onBeforeUnmount, watch, type WatchSource } from 'vue'

export function useLiveSearch(sources: WatchSource[], search: () => void, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => {
    if (timer) clearTimeout(timer)
    timer = undefined
  }
  watch(
    sources,
    () => {
      cancel()
      timer = setTimeout(search, delay)
    },
    { flush: 'sync' },
  )
  onBeforeUnmount(cancel)
  return cancel
}

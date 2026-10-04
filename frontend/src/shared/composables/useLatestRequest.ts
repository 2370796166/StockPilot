import { onBeforeUnmount } from 'vue'

// Invalidate at user intent as well as request start, including the debounce window.
export function useLatestRequest() {
  let sequence = 0
  const invalidate = () => ++sequence
  onBeforeUnmount(invalidate)
  return { next: invalidate, invalidate, isCurrent: (value: number) => value === sequence }
}

import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/modules/auth/store'
import { transitionCount, cancelCount } from '@/modules/inventory-count/api'
import type { CountSummary } from '@/modules/inventory-count/types'

export interface InventoryCountAction {
  key: string
  label: string
  authority: string
  requiresVersion: boolean
}

export function useInventoryCountActions(reload: () => Promise<void>) {
  const auth = useAuthStore()
  const acting = ref<number>()

  function actions(count: CountSummary) {
    const available: InventoryCountAction[] = []
    if (count.status === 'DRAFT') {
      available.push({ key: 'start', label: '开始盘点', authority: 'INVENTORY_COUNT_WRITE', requiresVersion: true })
    }
    if (count.status === 'COUNTING') {
      available.push({ key: 'submit', label: '提交审核', authority: 'INVENTORY_COUNT_WRITE', requiresVersion: true })
    }
    if (count.status === 'SUBMITTED') {
      available.push({ key: 'approve', label: '审核', authority: 'INVENTORY_COUNT_APPROVE', requiresVersion: true })
    }
    if (count.status === 'APPROVED') {
      available.push({ key: 'adjust', label: '执行调整', authority: 'INVENTORY_COUNT_ADJUST', requiresVersion: false })
    }
    if (['DRAFT', 'COUNTING', 'SUBMITTED', 'APPROVED'].includes(count.status)) {
      available.push({ key: 'cancel', label: '取消盘点', authority: 'INVENTORY_COUNT_WRITE', requiresVersion: true })
    }
    return available.filter((action) => auth.can(action.authority))
  }

  async function execute(count: CountSummary, action: InventoryCountAction) {
    if (acting.value !== undefined) return
    acting.value = count.id
    try {
      if (action.key === 'cancel') {
        const { value } = await ElMessageBox.prompt('取消后释放盘点锁，保留原实盘记录。请填写取消原因。', '取消盘点', {
          type: 'warning',
          inputValidator: (value: string) =>
            (!!value?.trim() && value.trim().length <= 255) || '原因不能为空且最多255字',
        })
        await cancelCount(count.id, count.version, value.trim())
      } else {
        await ElMessageBox.confirm(`确认执行“${action.label}”吗？`, '关键操作确认', { type: 'warning' })
        await transitionCount(count.id, action.key, action.requiresVersion ? count.version : undefined)
      }
      ElMessage.success(`${action.label}成功`)
      await reload()
    } catch {
      // Confirmation cancellation is expected; HTTP errors are shown by the request interceptor.
    } finally {
      acting.value = undefined
    }
  }

  return { acting, actions, execute }
}

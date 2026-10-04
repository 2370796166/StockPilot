import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/modules/auth/store'
import { transitionTransfer } from '@/modules/transfer/api'
import type { TransferSummary } from '@/modules/transfer/types'

export interface TransferAction {
  key: string
  label: string
  authority: string
  requiresVersion: boolean
}

export function useTransferActions(reload: () => Promise<void>) {
  const auth = useAuthStore()
  const acting = ref<number>()

  function actions(transfer: TransferSummary) {
    const available: TransferAction[] = []
    if (transfer.status === 'DRAFT') {
      available.push({ key: 'submit', label: '提交并冻结', authority: 'TRANSFER_WRITE', requiresVersion: true })
    }
    if (transfer.status === 'SUBMITTED') {
      available.push({ key: 'approve', label: '审核', authority: 'TRANSFER_APPROVE', requiresVersion: true })
    }
    if (transfer.status === 'APPROVED') {
      available.push({ key: 'dispatch', label: '确认调出', authority: 'TRANSFER_OUTBOUND', requiresVersion: false })
    }
    if (transfer.status === 'OUTBOUND_COMPLETED') {
      available.push({ key: 'start-transit', label: '开始运输', authority: 'TRANSFER_OUTBOUND', requiresVersion: true })
    }
    if (transfer.status === 'IN_TRANSIT') {
      available.push({ key: 'receive', label: '确认收货', authority: 'TRANSFER_INBOUND', requiresVersion: false })
    }
    if (transfer.status === 'SUBMITTED' || transfer.status === 'APPROVED') {
      available.push({ key: 'cancel', label: '取消并释放', authority: 'TRANSFER_WRITE', requiresVersion: false })
    }
    return available.filter((action) => auth.can(action.authority))
  }

  async function execute(transfer: TransferSummary, action: TransferAction) {
    if (acting.value !== undefined) return
    acting.value = transfer.id
    try {
      await ElMessageBox.confirm(`确认执行“${action.label}”吗？`, '关键操作确认', { type: 'warning' })
      await transitionTransfer(transfer.id, action.key, action.requiresVersion ? transfer.version : undefined)
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

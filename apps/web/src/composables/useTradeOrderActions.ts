import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { getTradeOrderV2, changeTradeOrderStateV2 } from '@/api/trade'

export function useTradeOrderActions(module: 'sales' | 'procurement', reload: () => Promise<void>) {
  const auth = useAuthStore()
  const { t } = useI18n()
  const busyOrderId = ref('')
  const canManageOrders = computed(() => auth.hasPermission(`${module}:view`) && auth.hasPermission(`${module}:submit`))

  async function changeOrderState(action: 'confirm' | 'cancel', id: string, number: string) {
    if (!canManageOrders.value || busyOrderId.value) return
    busyOrderId.value = id
    try {
      if (action === 'cancel') {
        await ElMessageBox.confirm(t('tradeActions.cancelPrompt', { number }), t('tradeActions.cancelOrder'), {
          type: 'warning', confirmButtonText: t('tradeActions.confirmCancellation'), cancelButtonText: t('tradeActions.keepOrder'),
        })
      }
      // Compat lists do not carry a version. Read the current scoped native
      // document, then let its optimistic version guard any intervening change.
      const document = await getTradeOrderV2(module, id)
      await changeTradeOrderStateV2(module, id, action, document.version)
      ElMessage.success(t(action === 'confirm' ? 'tradeActions.confirmed' : 'tradeActions.cancelled'))
      await reload()
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      const conflict = axios.isAxiosError(error) && error.response?.status === 409
      ElMessage.error(t(conflict ? 'tradeActions.conflict' : 'tradeActions.failed'))
      await reload()
    } finally {
      busyOrderId.value = ''
    }
  }
  return { busyOrderId, canManageOrders, changeOrderState }
}

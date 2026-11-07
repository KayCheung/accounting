import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useAppStore = defineStore('app', () => {
  // 侧边栏折叠状态
  const isCollapse = ref(false)
  // 当前会计日期（由后端 AccountingDateCache 权威提供）
  const accountingDate = ref('')
  // 日切营业状态
  const eodStatus = ref('NORMAL')

  function toggleSidebar() {
    isCollapse.value = !isCollapse.value
  }

  /**
   * 刷新系统与会计日期状态
   * 默认不传 accountingDate，由后端 AccountingDateCache 权威兜底返回全局当前会计日
   */
  async function fetchSystemStatus(targetDate?: string) {
    try {
      const params = targetDate ? { accountingDate: targetDate } : undefined
      const res = await request.get('/eod/status', params)
      if (res && res.accountingDate) {
        accountingDate.value = res.accountingDate
      }
      if (res && res.status !== undefined) {
        // 1: 未开始, 8: 已完成 -> 视为正常营业；其余状态显示对应中文状态
        eodStatus.value = res.status === 1 || res.status === 8 ? 'NORMAL' : (res.statusDesc || '日切中')
      }
    } catch {
      // 本地未联通时静默降级，避免阻断页面
      if (!accountingDate.value) {
        accountingDate.value = new Date().toISOString().slice(0, 10)
      }
    }
  }

  return {
    isCollapse,
    accountingDate,
    eodStatus,
    toggleSidebar,
    fetchSystemStatus
  }
})

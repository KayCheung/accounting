import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useAppStore = defineStore('app', () => {
  // 侧边栏折叠状态
  const isCollapse = ref(false)
  // 当前会计日期（从日切状态或后端接口获取）
  const accountingDate = ref('2026-06-25')
  // 日切状态
  const eodStatus = ref('NORMAL')

  function toggleSidebar() {
    isCollapse.value = !isCollapse.value
  }

  /**
   * 刷新系统与会计日期状态
   */
  async function fetchSystemStatus() {
    try {
      // 允许选传 accountingDate，后端未传时自动兜底当前会计日
      const params = accountingDate.value ? { accountingDate: accountingDate.value } : undefined
      const res = await request.get('/eod/status', params)
      if (res && res.accountingDate) {
        accountingDate.value = res.accountingDate
      }
      if (res && res.status !== undefined) {
        // 1: 未开始, 8: 已完成 -> 视为正常营业；其余状态显示对应中文状态
        eodStatus.value = res.status === 1 || res.status === 8 ? 'NORMAL' : (res.statusDesc || '日切中')
      }
    } catch {
      // 本地未联通时静默降级
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

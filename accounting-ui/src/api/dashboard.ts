import request from '@/utils/request'

/**
 * 系统概览大盘与待办数据响应结构
 */
export interface DashboardOverviewResponse {
  accountingDate: string
  eodStatus: number
  eodStatusDesc: string
  trialBalanced: boolean
  trialBalanceDiff: number | string
  trialDebitTotal: number | string
  trialCreditTotal: number | string

  // 待办事项统计（面向 MAKER / AUDITOR / REVIEWER / BOOKKEEPER）
  makerDraftCount: number
  makerRejectedCount: number
  pendingAuditCount: number
  pendingReviewCount: number
  pendingBookkeepingCount: number

  // 异常治理待办（面向 OPERATOR / ADMIN）
  failedPostingCount: number
  hangingJournalCount: number
  pendingBufferCount: number
  activeFreezeCount: number

  // 资产与业务规模指标（面向 ADMIN / OPERATOR）
  todayVoucherCount: number
  todayPostedVoucherCount: number
  todayJournalCount: number
  activeAccountCount: number
}

/**
 * 查询系统概览大盘与多角色待办数据
 */
export function getDashboardOverview(): Promise<DashboardOverviewResponse> {
  return request.get<DashboardOverviewResponse>('/dashboard/overview')
}

// 文件路径：src/api/buffer-monitor.ts
import request from '@/utils/request'
import type { SelectOption } from './types'

/**
 * 缓冲入账模式枚举：1-异步逐条，2-日间批量，3-日终批量
 */
export enum BufferModeEnum {
  ASYNC_SINGLE = 1,
  DAILY_BATCH = 2,
  EOD_BATCH = 3
}

/**
 * 缓冲入账状态枚举：1-待入账，2-处理中，3-成功，4-失败
 */
export enum BufferStatusEnum {
  PENDING = 1,
  PROCESSING = 2,
  SUCCESS = 3,
  FAILED = 4
}

/**
 * 缓冲模式选项
 */
export const BUFFER_MODE_OPTIONS: SelectOption<number>[] = [
  { label: '异步逐条', value: 1, tagType: 'primary' },
  { label: '日间批量', value: 2, tagType: 'success' },
  { label: '日终批量', value: 3, tagType: 'warning' }
]

/**
 * 缓冲状态选项
 */
export const BUFFER_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '待入账', value: 1, tagType: 'info' },
  { label: '处理中', value: 2, tagType: 'warning' },
  { label: '成功', value: 3, tagType: 'success' },
  { label: '失败', value: 4, tagType: 'danger' }
]

/**
 * 待入账统计响应 DTO
 */
export interface BufferPendingStatsResponse {
  accountingDate: string
  mode1Count: number
  mode1Amount: number
  mode2Count: number
  mode2Amount: number
  mode3Count: number
  mode3Amount: number
  totalAccounts: number
  oldestPendingTime?: string | null
}

/**
 * 状态细分统计项
 */
export interface BufferStatusCount {
  status: number
  statusDesc: string
  count: number
  amount: number
}

/**
 * 失败 Top 账户项
 */
export interface FailedAccountInfo {
  accountNo: string
  failedCount: number
  totalAmount: number
}

/**
 * Running Balance 差异告警项
 */
export interface BalanceAlertInfo {
  accountNo: string
  actualBalance: number
  calculatedBalance: number
  diff: number
}

/**
 * 缓冲监控响应 DTO
 */
export interface BufferMonitorResponse {
  totalRecords: number
  statusBreakdown: BufferStatusCount[]
  failedTopAccounts: FailedAccountInfo[]
  runningBalanceAlerts: BalanceAlertInfo[]
}

/**
 * 缓冲记账执行失败项
 */
export interface BufferFailedItem {
  detailId: number
  accountNo: string
  amount: number
  failReason: string
}

/**
 * 缓冲记账执行请求 DTO
 */
export interface BufferExecuteRequest {
  accountingDate: string
  bufferMode: number
  accountNo?: string
  maxBatchSize?: number
}

/**
 * 缓冲记账执行响应 DTO
 */
export interface BufferExecuteResponse {
  totalCount: number
  successCount: number
  failedCount: number
  durationMs: number
  failedList: BufferFailedItem[]
}

/**
 * 查询待入账统计信息
 */
export function getBufferPendingStats(accountingDate: string): Promise<BufferPendingStatsResponse> {
  return request.get<BufferPendingStatsResponse>('/buffer/pending-stats', { accountingDate })
}

/**
 * 查询缓冲监控大盘数据
 */
export function getBufferMonitor(params: { accountingDate?: string; status?: number }): Promise<BufferMonitorResponse> {
  return request.get<BufferMonitorResponse>('/buffer/monitor', params)
}

/**
 * 手动触发缓冲记账
 */
export function executeBufferPosting(data: BufferExecuteRequest): Promise<BufferExecuteResponse> {
  return request.post<BufferExecuteResponse>('/buffer/execute', data)
}

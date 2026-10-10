import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 业务记账流水列表项结构
 */
export interface JournalRecordItem {
  traceNo: string
  traceSeq: number
  businessCode: string
  tradingCode: string
  payChannel: string
  tradeType: number
  tradeTypeDesc: string
  amount: number
  tradeTime: string
  accountingDate: string
  summary: string
  origFreezeNo?: string
  status: number
  statusDesc: string
  createTime: string
}

/**
 * 账务事务列表项结构
 */
export interface TransactionRecordItem {
  txnNo: string
  traceNo: string
  accountingDate: string
  relateAccountCount: number
  amount: number
  currency: string
  status: number
  statusDesc: string
  failReason?: string
  finishTime?: string
  createTime: string
}

/**
 * 业务流水记录分页查询请求
 */
export interface JournalPageQueryRequest {
  pageNo?: number
  pageSize?: number
  traceNo?: string
  businessCode?: string
  tradeType?: number | string
  status?: number | string
  origFreezeNo?: string
  startDate?: string
  endDate?: string
}

/**
 * 账务事务记录分页查询请求
 */
export interface TransactionPageQueryRequest {
  pageNo?: number
  pageSize?: number
  txnNo?: string
  traceNo?: string
  status?: number | string
  startDate?: string
  endDate?: string
}

/**
 * 分录明细结构
 */
export interface EntryInfo {
  entryId: string
  rowNum: number
  subjectCode: string
  accountNo: string
  debitCredit: number
  debitCreditDesc: string
  amount: number
  unilateral: number
  buffered: number
  changeDirection: number
  status: number
  statusDesc: string
}

/**
 * 凭证信息结构
 */
export interface VoucherInfo {
  voucherNo: string
  voucherType: string
  status: number
  statusDesc: string
  amount: number
  entries: EntryInfo[]
}

/**
 * 资金冻结明细结构
 */
export interface FreezeInfo {
  freezeId: string
  accountNo: string
  freezeAmount: number
  origFreezeAmount: number
  unfrozenAmount: number
  deductedAmount: number
  expireTime?: string
  status: number
  statusDesc: string
}

/**
 * 异步本地消息结构
 */
export interface AsyncMessageInfo {
  messageId: string
  businessKey: string
  topic: string
  tag: string
  status: number
  statusDesc: string
  retryCount: number
  nextRetryTime?: string
  createTime: string
}

/**
 * 缓冲记账明细结构
 */
export interface BufferDetailInfo {
  id: number
  ruleId: number
  bufferMode: number
  bufferModeDesc: string
  voucherNo: string
  entryId: string
  accountNo: string
  amount: number
  changeDirection: number
  status: number
  statusDesc: string
  triggerTime?: string
  postTime?: string
  failReason?: string
}

/**
 * 过账汇总与监控结构
 */
export interface PostingSummaryInfo {
  realtimeTotal: number
  realtimeSuccessCount: number
  asyncTotal: number
  asyncSuccessCount: number
  bufferTotal: number
  bufferSuccessCount: number
  asyncMessages: AsyncMessageInfo[]
  bufferDetails: BufferDetailInfo[]
}

/**
 * 全流程全景总览响应结构
 */
export interface JournalOverviewResponse {
  record: JournalRecordItem
  transaction?: TransactionRecordItem
  vouchers?: VoucherInfo[]
  freeze?: FreezeInfo
  processStage: 'RECORDED' | 'VOUCHERED' | 'POSTING' | 'SUCCESS' | 'FAILED' | string
  stageDesc: string
  progressPercent: number
  canRetry: boolean
  canRollback: boolean
  postingSummary?: PostingSummaryInfo
}

/**
 * 流水处理状态枚举选项
 */
export const JOURNAL_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '全部状态', value: '' as any },
  { label: '处理中', value: 1, tagType: 'warning' },
  { label: '记账成功', value: 2, tagType: 'success' },
  { label: '记账失败', value: 3, tagType: 'danger' }
]

/**
 * 事务处理状态枚举选项
 */
export const TRANSACTION_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '全部状态', value: '' as any },
  { label: '处理中', value: 1, tagType: 'warning' },
  { label: '成功', value: 2, tagType: 'success' },
  { label: '失败', value: 3, tagType: 'danger' }
]

/**
 * 交易类型枚举选项
 */
export const JOURNAL_TRADE_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '全部类别', value: '' as any },
  { label: '正常记账', value: 1, tagType: 'primary' },
  { label: '调账', value: 2, tagType: 'warning' },
  { label: '红冲', value: 3, tagType: 'danger' },
  { label: '蓝补', value: 4, tagType: 'info' },
  { label: '业务预冻结', value: 5, tagType: 'warning' },
  { label: '预冻结解冻', value: 6, tagType: 'info' }
]

/**
 * 分页查询业务流水列表
 */
export function getJournalPage(params: JournalPageQueryRequest): Promise<PageResponse<JournalRecordItem>> {
  return request.get<PageResponse<JournalRecordItem>>('/journal/page', params)
}

/**
 * 分页查询账务事务列表
 */
export function getTransactionPage(params: TransactionPageQueryRequest): Promise<PageResponse<TransactionRecordItem>> {
  return request.get<PageResponse<TransactionRecordItem>>('/journal/transaction/page', params)
}

/**
 * 查询单笔流水全流程全景总览
 */
export function getJournalOverview(traceNo: string): Promise<JournalOverviewResponse> {
  return request.get<JournalOverviewResponse>(`/journal/${traceNo}/overview`)
}

/**
 * 触发记账失败重试
 */
export function retryJournal(traceNo: string): Promise<JournalOverviewResponse> {
  return request.post<JournalOverviewResponse>(`/journal/${traceNo}/retry`)
}

/**
 * 触发流水冲正与回滚
 */
export function rollbackJournal(traceNo: string, reason?: string): Promise<JournalOverviewResponse> {
  return request.post<JournalOverviewResponse>(`/journal/${traceNo}/rollback`, null, {
    params: { reason }
  })
}

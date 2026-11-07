// accounting-ui/src/api/transfer.ts
import request from '@/utils/request'
import type { PageResponse } from './types'

/**
 * 结转类型枚举
 */
export enum TransferTypeEnum {
  PROFIT_LOSS = 1, // 损益结转
  COST = 2,        // 成本结转
  CUSTOM = 3       // 自定义结转
}

/**
 * 结转方向枚举
 */
export enum TransferDirectionEnum {
  DEBIT_TO_CREDIT = 1, // 借方余额结转到贷方
  CREDIT_TO_DEBIT = 2  // 贷方余额结转到借方
}

/**
 * 结转执行状态枚举
 */
export enum TransferRecordStatusEnum {
  PROCESSING = 1, // 处理中
  SUCCESS = 2,    // 成功
  FAILED = 3      // 失败
}

/**
 * 结转周期枚举
 */
export enum PeriodCycleEnum {
  DAILY = 1,     // 每日/日结
  MONTHLY = 2,   // 月末/月结
  QUARTERLY = 3, // 季末/季结
  YEARLY = 4,    // 年末/年结
  MANUAL = 5     // 仅手动/自定义
}

/**
 * 结转规则响应结构
 */
export interface TransferRuleItem {
  id: number
  ruleCode: string
  ruleName: string
  transferType: number
  transferTypeDesc: string
  sourceSubjectCode: string
  targetSubjectCode: string
  targetSubjectName?: string
  transferDirection: number
  transferDirectionDesc: string
  summaryTemplate: string
  executeOrder: number
  status: number
  autoTransfer?: boolean
  periodCycle?: number
  periodCycleDesc?: string
  createTime?: string
  updateTime?: string
}

/**
 * 规则分页查询参数
 */
export interface TransferRuleQueryRequest {
  pageNo?: number
  pageSize?: number
  ruleCode?: string
  ruleName?: string
  transferType?: number
  status?: number
  autoTransfer?: boolean
  periodCycle?: number
}

/**
 * 创建结转规则参数
 */
export interface TransferRuleCreateRequest {
  ruleCode: string
  ruleName: string
  transferType: number
  sourceSubjectCode: string
  targetSubjectCode: string
  transferDirection: number
  summaryTemplate?: string
  executeOrder?: number
  autoTransfer?: boolean
  periodCycle?: number
}

/**
 * 修改结转规则参数
 */
export interface TransferRuleUpdateRequest {
  ruleName: string
  transferType: number
  sourceSubjectCode: string
  targetSubjectCode: string
  transferDirection: number
  summaryTemplate?: string
  executeOrder?: number
  autoTransfer?: boolean
  periodCycle?: number
}

/**
 * 结转试算分录项
 */
export interface TransferPreviewEntryItem {
  rowNum: number
  subjectCode: string
  subjectName?: string
  accountNo?: string
  debitCredit: number
  debitCreditDesc: string
  debitAmount?: number | null
  creditAmount?: number | null
  currency: string
  summary: string
}

/**
 * 单规则试算预览详情
 */
export interface TransferPreviewRuleItem {
  ruleCode: string
  ruleName: string
  transferType: number
  transferTypeDesc: string
  sourceSubjectCode: string
  targetSubjectCode: string
  targetSubjectName?: string
  matchedAccountCount: number
  totalAmount: number
  totalDebitAmount: number
  totalCreditAmount: number
  balanced: boolean
  autoTransfer?: boolean
  periodCycle?: number
  periodCycleDesc?: string
  entries: TransferPreviewEntryItem[]
}


/**
 * 期末结转试算预览总响应
 */
export interface TransferPreviewResponse {
  accountingDate: string
  totalRules: number
  activeRules: number
  grandTotalAmount: number
  allBalanced: boolean
  rulePreviews: TransferPreviewRuleItem[]
}

/**
 * 结转执行请求参数
 */
export interface TransferExecuteRequest {
  accountingDate: string
  ruleCode?: string
  transferType?: number
  forceRetry?: boolean
}

/**
 * 单条规则执行结果
 */
export interface TransferExecuteItem {
  ruleCode: string
  ruleName: string
  transferNo: string
  voucherNo?: string
  totalAmount: number
  status: number
  statusDesc: string
  failReason?: string
}

/**
 * 批量执行响应结果
 */
export interface TransferExecuteBatchResponse {
  accountingDate: string
  totalRules: number
  successCount: number
  failedCount: number
  totalAmount: number
  totalDurationMs: number
  items: TransferExecuteItem[]
}

/**
 * 结转历史记录项
 */
export interface TransferRecordItem {
  id: number
  transferNo: string
  accountingDate: string
  transferType: number
  transferTypeDesc: string
  ruleCode: string
  ruleName?: string
  voucherNo?: string
  totalAmount: number
  status: number
  statusDesc: string
  failReason?: string
  executeTime: string
  finishTime?: string
}

/**
 * 结转历史记录分页查询参数
 */
export interface TransferRecordQueryRequest {
  pageNo?: number
  pageSize?: number
  startDate?: string
  endDate?: string
  transferNo?: string
  ruleCode?: string
  voucherNo?: string
  status?: number
}

// ==================== 接口方法封装 ====================

/**
 * 分页查询结转规则列表
 */
export function getTransferRulePage(params: TransferRuleQueryRequest): Promise<PageResponse<TransferRuleItem>> {
  return request.get<PageResponse<TransferRuleItem>>('/transfer/rule/page', params)
}

/**
 * 查询单条结转规则详情
 */
export function getTransferRuleById(id: number): Promise<TransferRuleItem> {
  return request.get<TransferRuleItem>(`/transfer/rule/${id}`)
}

/**
 * 新增结转规则
 */
export function createTransferRule(data: TransferRuleCreateRequest): Promise<number> {
  return request.post<number>('/transfer/rule', data)
}

/**
 * 修改结转规则
 */
export function updateTransferRule(id: number, data: TransferRuleUpdateRequest): Promise<void> {
  return request.put<void>(`/transfer/rule/${id}`, data)
}

/**
 * 切换结转规则状态（1-启用, 2-停用）
 */
export function updateTransferRuleStatus(id: number, status: number): Promise<void> {
  return request.put<void>(`/transfer/rule/${id}/status`, { status })
}

/**
 * 删除结转规则（逻辑删除）
 */
export function deleteTransferRule(id: number): Promise<void> {
  return request.delete<void>(`/transfer/rule/${id}`)
}

/**
 * 期末结转试算分录预览
 */
export function previewTransfer(data: TransferExecuteRequest): Promise<TransferPreviewResponse> {
  return request.post<TransferPreviewResponse>('/transfer/preview', data)
}

/**
 * 手动执行期末结转
 */
export function executeTransfer(data: TransferExecuteRequest): Promise<TransferExecuteBatchResponse> {
  return request.post<TransferExecuteBatchResponse>('/transfer/execute', data)
}

/**
 * 分页查询结转历史记录
 */
export function getTransferRecordPage(params: TransferRecordQueryRequest): Promise<PageResponse<TransferRecordItem>> {
  return request.get<PageResponse<TransferRecordItem>>('/transfer/record/page', params)
}

/**
 * 查询结转记录详情
 */
export function getTransferRecordDetail(transferNo: string): Promise<TransferRecordItem> {
  return request.get<TransferRecordItem>(`/transfer/record/${transferNo}`)
}


// 文件路径：src/api/eod.ts
import request from '@/utils/request'
import type { SelectOption } from './types'

/**
 * 日切状态码枚举
 */
export enum EodStatusEnum {
  NOT_STARTED = 1,
  SWITCHING = 2,
  CLEANING = 3,
  SNAPSHOT = 4,
  TRIAL_BALANCE = 5,
  TRANSFER = 6,
  ARCHIVING = 7,
  COMPLETED = 8,
  FAILED = 9
}

/**
 * 日切状态响应结构
 */
export interface EodStatusResponse {
  accountingDate: string
  status: number
  statusDesc: string
  switchDateTime?: string | null
  archiveDateTime?: string | null
  failedStage?: string | null
  failReason?: string | null
  totalDurationMs: number
}

/**
 * 手动切日请求
 */
export interface DateSwitchRequest {
  targetDate?: string
}

/**
 * 手动切日响应
 */
export interface DateSwitchResponse {
  previousDate: string
  newDate: string
  alreadySwitched: boolean
  switchedAt: string
}

/**
 * 前置检查细项
 */
export interface CheckItem {
  name: string
  count: number
  passed: boolean
}

/**
 * 前置检查响应结构
 */
export interface EodPreCheckResponse {
  accountingDate: string
  allPassed: boolean
  checks: CheckItem[]
}

/**
 * 科目试算明细
 */
export interface SubjectDetail {
  subjectCode: string
  subjectName: string
  totalDebit: number
  totalCredit: number
  netDiff: number
}

/**
 * 试算平衡响应结构
 */
export interface TrialBalanceResponse {
  accountingDate: string
  passed: boolean
  totalDebit: number
  totalCredit: number
  diff: number
  subjectDetails: SubjectDetail[]
  imbalancedSubjects: SubjectDetail[]
}

/**
 * 总账分户核对差异明细
 */
export interface GlDiffDetail {
  subjectCode: string
  subjectName: string
  accountType?: number
  accountTypeDesc?: string
  glEndBalance: number
  accountBalance: number
  diff: number
  accountCount: number
  reasonDesc?: string
  repairable: boolean
}

/**
 * 总账分户核对响应结构
 */
export interface GlReconciliationResponse {
  accountingDate: string
  passed: boolean
  totalGlBalance: number
  totalAccountBalance: number
  totalDiff: number
  diffDetails: GlDiffDetail[]
}

/**
 * 账户级总分核对差异明细
 */
export interface GlAccountDiffResponse {
  accountNo: string
  accountName: string
  glEndBalance: number
  accountBalance: number
  diff: number
  latestVoucherDate?: string | null
  statusDesc: string
}

/**
 * 总分核对智能修复请求
 */
export interface GlRepairRequest {
  accountingDate: string
  subjectCode?: string
}

/**
 * 总分核对智能修复响应
 */
export interface GlRepairResponse {
  success: boolean
  repairedDateCount: number
  repairedAccountCount: number
  repairedAmount: number
  remainingDiff: number
  message: string
  repairLogs: string[]
}

/**
 * 期末结转明细记录
 */
export interface TransferRecord {
  ruleCode: string
  ruleName: string
  transferNo: string
  voucherNo: string
  totalAmount: number
  status: number
}

/**
 * 手动日切请求参数
 */
export interface EodExecuteRequest {
  accountingDate: string
  skipPreCheck?: boolean
  executeTransfer?: boolean
}

/**
 * 手动日切响应结构
 */
export interface EodExecuteResponse {
  accountingDate: string
  preCheckPassed: boolean
  preCheckDetails?: EodPreCheckResponse | null
  balanceCalculated: boolean
  balanceCount: number
  trialBalancePassed: boolean
  trialBalanceDetails?: TrialBalanceResponse | null
  glReconciliationPassed?: boolean
  glReconciliationDetails?: GlReconciliationResponse | null
  transferResults?: TransferRecord[]
  snapshotGenerated: boolean
  snapshotCount: number
  totalDurationMs: number
}

/**
 * 日切状态选项
 */
export const EOD_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '未开始', value: 1, tagType: 'info' },
  { label: '切日中', value: 2, tagType: 'primary' },
  { label: '清理中', value: 3, tagType: 'warning' },
  { label: '快照中', value: 4, tagType: 'warning' },
  { label: '试算中', value: 5, tagType: 'warning' },
  { label: '结转中', value: 6, tagType: 'warning' },
  { label: '归档中', value: 7, tagType: 'warning' },
  { label: '已完成', value: 8, tagType: 'success' },
  { label: '失败', value: 9, tagType: 'danger' }
]

/**
 * 查询指定会计日的日切执行状态（不传默认当前会计日）
 */
export function getEodStatus(accountingDate?: string): Promise<EodStatusResponse> {
  return request.get<EodStatusResponse>('/eod/status', { accountingDate })
}

/**
 * 手动触发切日
 */
export function switchDate(data: DateSwitchRequest): Promise<DateSwitchResponse> {
  return request.post<DateSwitchResponse>('/eod/switch-date', data)
}

/**
 * 查询日切前置检查结果
 */
export function getPreCheckResult(accountingDate: string): Promise<EodPreCheckResponse> {
  return request.get<EodPreCheckResponse>('/eod/precheck', { accountingDate })
}

/**
 * 查询试算平衡结果
 */
export function getTrialBalance(accountingDate: string): Promise<TrialBalanceResponse> {
  return request.get<TrialBalanceResponse>('/eod/trial-balance', { accountingDate })
}

/**
 * 手动触发日切全流程
 */
export function executeEod(data: EodExecuteRequest): Promise<EodExecuteResponse> {
  return request.post<EodExecuteResponse>('/eod/execute', data)
}

/**
 * 查询总账分户核对结果
 */
export function getGlReconciliation(accountingDate: string): Promise<GlReconciliationResponse> {
  return request.get<GlReconciliationResponse>('/eod/gl-reconciliation', { accountingDate })
}

/**
 * 查询账户级总分核对差异明细（二级穿透）
 */
export function getGlAccountDiffs(accountingDate: string, subjectCode?: string): Promise<GlAccountDiffResponse[]> {
  return request.get<GlAccountDiffResponse[]>('/eod/gl-reconciliation/accounts', { accountingDate, subjectCode })
}

/**
 * 触发总分核对一键智能修复
 */
export function repairGlReconciliation(data: GlRepairRequest): Promise<GlRepairResponse> {
  return request.post<GlRepairResponse>('/eod/gl-reconciliation/repair', data)
}

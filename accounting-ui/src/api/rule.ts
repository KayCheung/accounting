import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 辅助核算项响应
 */
export interface RuleAuxiliaryResponse {
  id?: number
  auxType: string
  auxCode: string
  allocationMethod: number // 1-不分摊，2-固定金额，3-按比例
  allocationValue?: number | string
  extendScript?: string
}

/**
 * 规则明细分录响应
 */
export interface RuleEntryResponse {
  id?: number
  rowNum: number
  fundsType: string
  subjectCode: string
  accountScope: number // 1-内部，2-外部
  debitCredit: number // 1-借，2-贷
  currency?: string
  isUnilateral?: boolean
  extendScript?: string
  summary?: string
  auxiliaries?: RuleAuxiliaryResponse[]
}

/**
 * 记账规则响应实体
 */
export interface RuleResponse {
  id: number
  ruleName: string
  voucherType: string
  businessCode: string
  tradingCode: string
  payChannel: string
  isOpenAccount: boolean
  freezeDuration?: number
  preRuleId?: number
  status: number // 1-待启用，2-启用，3-停用
  entries?: RuleEntryResponse[]
}

/**
 * 记账规则分页查询请求
 */
export interface RuleQueryRequest {
  pageNo?: number
  pageSize?: number
  businessCode?: string
  tradingCode?: string
  status?: number
}

/**
 * 辅助核算项请求
 */
export interface RuleAuxiliaryRequest {
  auxType: string
  auxCode: string
  allocationMethod: number
  allocationValue?: number | string
  extendScript?: string
}

/**
 * 规则明细分录请求
 */
export interface RuleEntryRequest {
  rowNum: number
  fundsType: string
  subjectCode: string
  accountScope: number
  debitCredit: number
  currency?: string
  isUnilateral?: boolean
  extendScript?: string
  summary?: string
  auxiliaries?: RuleAuxiliaryRequest[]
}

/**
 * 创建记账规则请求
 */
export interface RuleCreateRequest {
  ruleName: string
  voucherType: string
  businessCode: string
  tradingCode: string
  payChannel: string
  isOpenAccount?: boolean
  freezeDuration?: number
  preRuleId?: number
  status: number
  entries: RuleEntryRequest[]
}

/**
 * 更新记账规则请求
 */
export interface RuleUpdateRequest {
  ruleName?: string
  voucherType?: string
  isOpenAccount?: boolean
  freezeDuration?: number
  preRuleId?: number
  status?: number
  entries?: RuleEntryRequest[]
}

/**
 * 规则状态常量映射
 */
export const RULE_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '待启用', value: 1, tagType: 'warning' },
  { label: '启用', value: 2, tagType: 'success' },
  { label: '停用', value: 3, tagType: 'danger' }
]

/**
 * 借贷方向常量映射
 */
export const DEBIT_CREDIT_OPTIONS: SelectOption<number>[] = [
  { label: '借 (Debit)', value: 1, tagType: 'primary' },
  { label: '贷 (Credit)', value: 2, tagType: 'warning' }
]

/**
 * 账户作用域常量映射
 */
export const ACCOUNT_SCOPE_OPTIONS: SelectOption<number>[] = [
  { label: '内部分户', value: 1, tagType: 'info' },
  { label: '外部分户', value: 2, tagType: 'primary' }
]

/**
 * 辅助核算分摊方式常量映射
 */
export const ALLOCATION_METHOD_OPTIONS: SelectOption<number>[] = [
  { label: '不分摊', value: 1, tagType: 'info' },
  { label: '固定金额', value: 2, tagType: 'warning' },
  { label: '按比例', value: 3, tagType: 'success' }
]

/**
 * 分页查询记账规则
 */
export function getRulePage(params: RuleQueryRequest): Promise<PageResponse<RuleResponse>> {
  return request.get<PageResponse<RuleResponse>>('/config/rule/page', params)
}

/**
 * 查询单个记账规则（含明细及辅助核算）
 */
export function getRuleById(ruleId: number): Promise<RuleResponse> {
  return request.get<RuleResponse>(`/config/rule/${ruleId}`)
}

/**
 * 创建记账规则
 */
export function createRule(data: RuleCreateRequest): Promise<void> {
  return request.post<void>('/config/rule', data)
}

/**
 * 更新记账规则
 */
export function updateRule(ruleId: number, data: RuleUpdateRequest): Promise<void> {
  return request.put<void>(`/config/rule/${ruleId}`, data)
}

/**
 * 启用记账规则
 */
export function enableRule(ruleId: number): Promise<void> {
  return request.post<void>(`/config/rule/${ruleId}/enable`)
}

/**
 * 停用记账规则（联动校验是否有关联凭证）
 */
export function disableRule(ruleId: number): Promise<void> {
  return request.delete<void>(`/config/rule/${ruleId}`)
}

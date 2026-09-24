import request from '@/utils/request'

export interface BufferRuleResponse {
  id: number
  ruleName: string
  bufferMode: number
  businessCode: string
  tradingCode: string
  payChannel: string
  subjectCode?: string
  accountNo?: string
  debitCredit: number
  effectiveTime: string
  expirationTime: string
  status: number
}

export interface BufferRuleCreateRequest {
  ruleName: string
  bufferMode: number
  businessCode: string
  tradingCode: string
  payChannel: string
  subjectCode?: string
  accountNo?: string
  debitCredit: number
  effectiveTime: string
  expirationTime: string
}

export interface BufferRuleUpdateRequest {
  ruleName?: string
  bufferMode?: number
  subjectCode?: string
  accountNo?: string
  debitCredit?: number
  effectiveTime?: string
  expirationTime?: string
}

export interface BufferRuleQueryRequest {
  pageNo: number
  pageSize: number
  businessCode?: string
  bufferMode?: number
  status?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  pages?: number
  current?: number
}

// 缓冲入账模式选项
export const BUFFER_MODE_OPTIONS = [
  { label: '异步逐条', value: 1, tagType: 'primary', desc: '准实时异步逐笔削峰入账' },
  { label: '日间批量', value: 2, tagType: 'warning', desc: '日间按批次定时汇总过账' },
  { label: '日终批量汇总', value: 3, tagType: 'danger', desc: '极高频微额在日终日切窗口统一合账' }
]

// 规则状态选项
export const RULE_STATUS_OPTIONS = [
  { label: '待启用', value: 1, tagType: 'info' },
  { label: '启用', value: 2, tagType: 'success' },
  { label: '停用', value: 3, tagType: 'danger' }
]

// 借贷方向选项
export const DEBIT_CREDIT_OPTIONS = [
  { label: '借方 (Debit)', value: 1 },
  { label: '贷方 (Credit)', value: 2 }
]

/**
 * 分页查询缓冲入账规则
 */
export function getBufferRulePage(params: BufferRuleQueryRequest): Promise<PageResult<BufferRuleResponse>> {
  return request.get<PageResult<BufferRuleResponse>>('/config/buffer-rule/page', params)
}

/**
 * 按ID查询单个缓冲入账规则
 */
export function getBufferRuleById(ruleId: number): Promise<BufferRuleResponse> {
  return request.get<BufferRuleResponse>(`/config/buffer-rule/${ruleId}`)
}

/**
 * 创建缓冲入账规则
 */
export function createBufferRule(data: BufferRuleCreateRequest): Promise<void> {
  return request.post<void>('/config/buffer-rule', data)
}

/**
 * 更新缓冲入账规则
 */
export function updateBufferRule(ruleId: number, data: BufferRuleUpdateRequest): Promise<void> {
  return request.put<void>(`/config/buffer-rule/${ruleId}`, data)
}

/**
 * 启用缓冲入账规则
 */
export function enableBufferRule(ruleId: number): Promise<void> {
  return request.post<void>(`/config/buffer-rule/${ruleId}/enable`)
}

/**
 * 停用缓冲入账规则
 */
export function disableBufferRule(ruleId: number): Promise<void> {
  return request.delete<void>(`/config/buffer-rule/${ruleId}`)
}

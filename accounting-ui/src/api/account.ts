import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 账户分页查询入参
 */
export interface AccountPageQueryRequest {
  pageNo?: number
  pageSize?: number
  accountCategory?: 'CUSTOMER' | 'INTERNAL' | ''
  accountNo?: string
  accountName?: string
  ownerId?: string
  ownerType?: number
  subjectCode?: string
  accountType?: string
  currency?: string
  status?: number
  riskStatus?: number
  startDate?: string
  endDate?: string
}

/**
 * 账户分页行实体
 */
export interface AccountPageItem {
  id: number
  accountNo: string
  accountName: string
  ownerId: string
  ownerType: number
  ownerTypeDesc: string
  subjectCode: string
  subjectName: string
  accountType: string
  accountTypeName: string
  currency: string
  balanceDirection: number
  balanceDirectionDesc: string
  openingBalance: number
  balance: number
  availableBalance: number
  frozenBalance: number
  status: number
  statusDesc: string
  riskStatus: number
  riskStatusDesc: string
  requestNo: string
  openDate: string
  inactiveDate?: string
  createdAt: string
}

/**
 * 聚合余额响应结构
 */
export interface AggregateBalanceResponse {
  accountNo: string
  accountName: string
  subjectCode: string
  currency: string
  balanceDirection: number
  totalBalance: number
  availableBalance: number
  frozenBalance: number
  bufferEstimateBalance?: number
  status: number
  riskStatus: number
}

/**
 * 账户变动流水明细实体
 */
export interface AccountDetailItem {
  id: number
  accountNo: string
  voucherNo: string
  tradeNo: string
  tradeType: string
  tradeTime: string
  debitCredit: number
  amount: number
  postBalance: number
  digest: string
}

/**
 * 账户明细查询入参
 */
export interface AccountDetailQueryRequest {
  pageNo?: number
  pageSize?: number
  accountNo: string
  startDate?: string
  endDate?: string
  tradeType?: string
  debitCredit?: number
}

/**
 * 外部开户入参
 */
export interface AccountOpenRequest {
  businessCode: string
  customerId: string
  customerName?: string
  customerType: number
  subjectCode?: string
  requestNo: string
}

/**
 * 内部开户入参
 */
export interface InternalAccountOpenRequest {
  subjectCode: string
}

/**
 * 账户状态变更入参
 */
export interface AccountStatusChangeRequest {
  accountNo: string
  reason?: string
  riskStatus?: number
}

/**
 * 账户状态选项
 */
export const ACCOUNT_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '正常', value: 1, tagType: 'success' },
  { label: '冻结', value: 2, tagType: 'danger' },
  { label: '注销', value: 3, tagType: 'info' }
]

/**
 * 风控状态选项
 */
export const RISK_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '正常', value: 1, tagType: 'success' },
  { label: '止入', value: 2, tagType: 'warning' },
  { label: '止出', value: 3, tagType: 'warning' },
  { label: '止入止出', value: 4, tagType: 'danger' }
]

/**
 * 客户类型选项
 */
export const OWNER_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '个人', value: 1, tagType: 'success' },
  { label: '企业', value: 2, tagType: 'primary' },
  { label: '其他', value: 99, tagType: 'info' }
]

/**
 * 余额借贷方向选项
 */
export const DIRECTION_OPTIONS: SelectOption<number>[] = [
  { label: '借', value: 1, tagType: 'primary' },
  { label: '贷', value: 2, tagType: 'warning' }
]

/**
 * 分页查询账户列表
 */
export function getAccountPage(params: AccountPageQueryRequest): Promise<PageResponse<AccountPageItem>> {
  return request.get<PageResponse<AccountPageItem>>('/account/page', params)
}

/**
 * 聚合查询账户余额信息（主账户 + 可用 + 冻结 + 缓冲预估）
 */
export function getAggregateBalance(accountNo: string): Promise<AggregateBalanceResponse> {
  return request.get<AggregateBalanceResponse>('/account/balance/aggregate', { accountNo })
}

/**
 * 查询账户交易流水明细
 */
export function getAccountDetails(params: AccountDetailQueryRequest): Promise<PageResponse<AccountDetailItem>> {
  return request.get<PageResponse<AccountDetailItem>>('/account/balance/details', params)
}

/**
 * 外部客户开户（单开）
 */
export function openExternalAccount(data: AccountOpenRequest): Promise<any> {
  return request.post<any>('/account/opening/external', data)
}

/**
 * 外部客户开户（批量开出模板所有科目账户）
 */
export function openExternalBatch(data: AccountOpenRequest): Promise<any[]> {
  return request.post<any[]>('/account/opening/external/batch', data)
}

/**
 * 内部账户开户
 */
export function openInternalAccount(data: InternalAccountOpenRequest): Promise<any> {
  return request.post<any>('/account/opening/internal', data)
}

/**
 * 批量扫描全科目自动初始化内部账户
 */
export function batchScanInternalAccounts(): Promise<any> {
  return request.post<any>('/account/opening/internal/batch')
}

/**
 * 冻结账户
 */
export function freezeAccount(accountNo: string, reason?: string): Promise<any> {
  return request.post<any>('/account/status/freeze', { accountNo, reason: reason || '后台管理冻结' })
}

/**
 * 解冻账户
 */
export function unfreezeAccount(accountNo: string, reason?: string): Promise<any> {
  return request.post<any>('/account/status/unfreeze', { accountNo, reason: reason || '后台管理解冻' })
}

/**
 * 注销账户
 */
export function cancelAccount(accountNo: string, reason?: string): Promise<any> {
  return request.post<any>('/account/status/cancel', { accountNo, reason: reason || '后台管理注销' })
}

/**
 * 变更风控状态
 */
export function changeRiskStatus(accountNo: string, riskStatus: number, reason?: string): Promise<any> {
  return request.post<any>('/account/status/risk', { accountNo, riskStatus, reason: reason || '后台调整风控状态' })
}

/**
 * 冻结记录实体
 */
export interface FreezeRecordItem {
  freezeId: string
  accountNo: string
  freezeAmount: number
  status: number
  statusDesc: string
  expireTime: string
  tradeTime: string
  createTime: string
  summary: string
}

/**
 * 冻结记录查询入参
 */
export interface FreezeRecordQueryRequest {
  pageNo?: number
  pageSize?: number
  accountNo: string
  status?: number
}

/**
 * 查询账户冻结记录
 */
export function getFreezeRecords(params: FreezeRecordQueryRequest): Promise<PageResponse<FreezeRecordItem>> {
  return request.get<PageResponse<FreezeRecordItem>>('/account/balance/freeze-records', params)
}

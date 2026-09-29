import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 聚合余额响应数据结构
 */
export interface AggregateBalanceResponse {
  accountNo: string
  accountName: string
  subjectCode: string
  mainBalance: number
  availableBalance: number
  frozenBalance: number
  bufferEstimate: number
  totalBalance: number
  status: number
  statusDesc: string
  riskStatus: number
  riskStatusDesc: string
  balanceDirection: number
  balanceDirectionDesc: string
  currency: string
  queryTime: string
}

/**
 * 账户明细项（分录流水）
 */
export interface AccountDetailItem {
  voucherNo: string
  entryId: string
  txnNo: string
  traceNo: string
  subjectCode: string
  accountNo: string
  businessCode: string
  tradingCode: string
  tradeType: number
  tradeTypeDesc: string
  tradeTime: string
  debitCredit: number
  debitCreditDesc: string
  changeDirection: number
  changeDirectionDesc: string
  currency: string
  preBalance: number
  amount: number
  postBalance: number
  accountingDate: string
  summary: string
}

/**
 * 账户明细分页查询入参
 */
export interface AccountDetailQueryRequest {
  pageNo?: number
  pageSize?: number
  accountNo: string
  startDate?: string
  endDate?: string
  tradeType?: number
  debitCredit?: number
}

/**
 * 资金冻结记录项
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
 * 资金冻结记录分页查询入参
 */
export interface FreezeRecordQueryRequest {
  pageNo?: number
  pageSize?: number
  accountNo: string
  status?: number
}

/**
 * 交易类别选项
 */
export const TRADE_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '正常交易', value: 1, tagType: 'success' },
  { label: '调账交易', value: 2, tagType: 'warning' },
  { label: '红冲交易', value: 3, tagType: 'danger' },
  { label: '蓝冲交易', value: 4, tagType: 'primary' }
]

/**
 * 借贷方向选项
 */
export const DEBIT_CREDIT_OPTIONS: SelectOption<number>[] = [
  { label: '借方 (Dr)', value: 1, tagType: 'primary' },
  { label: '贷方 (Cr)', value: 2, tagType: 'warning' }
]

/**
 * 冻结状态选项
 */
export const FREEZE_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '冻结中', value: 1, tagType: 'danger' },
  { label: '已解冻', value: 2, tagType: 'info' }
]

/**
 * 查询指定账户的聚合余额全景
 */
export function getAggregateBalance(accountNo: string): Promise<AggregateBalanceResponse> {
  return request.get<AggregateBalanceResponse>('/account/balance/aggregate', { accountNo })
}

/**
 * 分页查询账户交易流水变动明细
 */
export function getAccountDetails(params: AccountDetailQueryRequest): Promise<PageResponse<AccountDetailItem>> {
  return request.get<PageResponse<AccountDetailItem>>('/account/balance/details', params)
}

/**
 * 分页查询账户资金冻结记录
 */
export function getFreezeRecords(params: FreezeRecordQueryRequest): Promise<PageResponse<FreezeRecordItem>> {
  return request.get<PageResponse<FreezeRecordItem>>('/account/balance/freeze-records', params)
}

/**
 * 快速联想模糊匹配候选账户
 */
export function searchAccountCandidates(keyword: string): Promise<PageResponse<any>> {
  return request.get<PageResponse<any>>('/account/page', {
    accountNo: keyword,
    pageSize: 15
  })
}

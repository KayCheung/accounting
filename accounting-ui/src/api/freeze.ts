import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 冻结记录项结构
 */
export interface FreezeRecordItem {
  freezeId: string
  accountNo: string
  freezeAmount: number
  origFreezeAmount?: number
  unfrozenAmount?: number
  deductedAmount?: number
  status: number
  statusDesc: string
  expireTime: string
  tradeTime: string
  createTime: string
  summary: string
}

/**
 * 资金冻结综合分页查询入参
 */
export interface FreezePageQueryRequest {
  pageNo?: number
  pageSize?: number
  accountNo?: string
  freezeId?: string
  status?: number
  startDate?: string
  endDate?: string
}

/**
 * 资金冻结申请入参
 */
export interface FundFreezeRequest {
  accountNo: string
  freezeAmount: number
  expireTime?: string
  reason?: string
}

/**
 * 资金解冻申请入参
 */
export interface FundUnfreezeRequest {
  freezeId: string
  unfreezeAmount: number
  reason?: string
}

/**
 * 冻结扣款申请入参
 */
export interface FreezeDeductRequest {
  freezeId: string
  deductAmount: number
  reason?: string
}

/**
 * 冻结状态选项
 */
export const FREEZE_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '冻结中', value: 1, tagType: 'danger' },
  { label: '已解冻', value: 2, tagType: 'info' }
]

/**
 * 分页查询资金冻结记录
 */
export function getFreezePage(params: FreezePageQueryRequest): Promise<PageResponse<FreezeRecordItem>> {
  return request.get<PageResponse<FreezeRecordItem>>('/account/freeze/page', params)
}

/**
 * 查询指定冻结记录详情
 */
export function getFreezeDetail(freezeId: string): Promise<FreezeRecordItem> {
  return request.get<FreezeRecordItem>(`/account/freeze/detail/${freezeId}`)
}

/**
 * 执行资金冻结
 */
export function freezeFund(data: FundFreezeRequest): Promise<FreezeRecordItem> {
  return request.post<FreezeRecordItem>('/account/freeze/fund', data)
}

/**
 * 执行资金解冻
 */
export function unfreezeFund(data: FundUnfreezeRequest): Promise<any> {
  return request.post<any>('/account/freeze/unfreeze', data)
}

/**
 * 执行冻结扣款
 */
export function deductFromFreeze(data: FreezeDeductRequest): Promise<any> {
  return request.post<any>('/account/freeze/deduct', data)
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

/**
 * 查询指定账户的可用余额与聚合资产
 */
export function getAccountBalance(accountNo: string): Promise<any> {
  return request.get<any>('/account/balance/aggregate', { accountNo })
}

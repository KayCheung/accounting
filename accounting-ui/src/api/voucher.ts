import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 凭证分录行结构
 */
export interface VoucherEntryItem {
  entryId: string
  rowNum: number
  subjectCode: string
  subjectName?: string
  accountNo: string
  debitCredit: number // 1-借, 2-贷
  amount: number
  currency: string
  summary: string
  isUnilateral: boolean
  isBuffered: boolean
  accountingDate: string
}

/**
 * 辅助核算分摊项结构
 */
export interface VoucherAuxiliaryItem {
  entryId: string
  subjectCode: string
  auxType: string
  auxCode: string
  auxName: string
  changeDirection: number // 1-增, 2-减
  amount: number
  accountingDate: string
}

/**
 * 凭证附件结构
 */
export interface VoucherAttachmentItem {
  id: number
  voucherNo: string
  filePath: string
  createTime: string
}

/**
 * 凭证列表项响应结构
 */
export interface VoucherPageItem {
  id: number
  voucherNo: string
  txnNo?: string
  traceNo?: string
  traceSeq?: number
  voucherType?: string
  voucherTypeName?: string
  voucherWord?: string
  voucherTitle?: string
  totalAmountInWords?: string
  postingType?: number // 1-手工, 2-机制
  postingTypeDesc?: string
  businessCode?: string
  tradingCode?: string
  payChannel?: string
  tradeType?: number // 1-正常, 2-调账, 3-红, 4-蓝
  tradeTypeDesc?: string
  tradeTime?: string
  amount: number
  status: number // 1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销
  statusDesc?: string
  postTime?: string
  accountingDate: string
  summary?: string
  attachmentCount?: number
  origVoucherNo?: string
  bookkeeperName?: string
  reviewerName?: string
  failReason?: string
  retryCount?: number
  skipFlag?: number
  createTime?: string
  entries?: VoucherEntryItem[]
  debitCount?: number
  creditCount?: number
  debitAmount?: number
  creditAmount?: number
  isBalanced?: boolean
}

/**
 * 凭证全景档案详情结构
 */
export interface VoucherFullDetail extends VoucherPageItem {
  auxiliaries?: VoucherAuxiliaryItem[]
  attachments?: VoucherAttachmentItem[]
  reversalVoucherNo?: string
  canReversal?: boolean
}

/**
 * 记账凭证多维分页查询入参
 */
export interface VoucherPageQueryRequest {
  pageNo?: number
  pageSize?: number
  voucherNo?: string
  traceNo?: string
  txnNo?: string
  status?: number
  voucherType?: string
  postingType?: number
  businessCode?: string
  tradingCode?: string
  payChannel?: string
  tradeType?: number
  startDate?: string
  endDate?: string
  createStartTime?: string
  createEndTime?: string
}

/**
 * 凭证过账请求入参
 */
export interface PostingExecuteRequest {
  voucherNo: string
}

/**
 * 凭证红冲请求入参
 */
export interface ReversalRequest {
  origVoucherNo: string
  bookkeeperName: string
  summary?: string
}

/**
 * 红冲记录项结构
 */
export interface ReversalRecordItem {
  reversalVoucherNo: string
  origVoucherNo: string
  createTime: string
  bookkeeperName?: string
  amount: number
  summary?: string
}

/**
 * 凭证状态下拉选项
 */
export const VOUCHER_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '全部状态', value: '' as any },
  { label: '未过账', value: 1, tagType: 'info' },
  { label: '过账中', value: 2, tagType: 'warning' },
  { label: '已过账', value: 3, tagType: 'success' },
  { label: '过账失败', value: 4, tagType: 'danger' },
  { label: '已冲销', value: 5, tagType: 'info' }
]

/**
 * 交易类别下拉选项
 */
export const TRADE_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '全部类别', value: '' as any },
  { label: '正常', value: 1, tagType: 'primary' },
  { label: '调账', value: 2, tagType: 'warning' },
  { label: '红冲', value: 3, tagType: 'danger' },
  { label: '蓝补', value: 4, tagType: 'info' }
]

/**
 * 入账类型下拉选项
 */
export const POSTING_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '全部入账', value: '' as any },
  { label: '手工凭证', value: 1 },
  { label: '机制凭证', value: 2 }
]

/**
 * 分页查询凭证列表
 */
export function getVoucherPage(params: VoucherPageQueryRequest): Promise<PageResponse<VoucherPageItem>> {
  return request.get<PageResponse<VoucherPageItem>>('/voucher/page', params)
}

/**
 * 查询凭证全景档案详情
 */
export function getVoucherDetail(voucherNo: string): Promise<VoucherFullDetail> {
  return request.get<VoucherFullDetail>(`/voucher/detail/${voucherNo}`)
}

/**
 * 执行凭证手工过账
 */
export function executePosting(data: PostingExecuteRequest): Promise<any> {
  return request.post<any>('/posting/execute', data)
}

/**
 * 执行凭证红冲
 */
export function executeReversal(data: ReversalRequest): Promise<any> {
  return request.post<any>('/reversal/execute', data)
}

/**
 * 校验凭证是否允许红冲
 */
export function checkReversal(voucherNo: string): Promise<boolean> {
  return request.get<boolean>('/reversal/check', { voucherNo })
}

/**
 * 查询凭证关联红冲记录
 */
export function getReversalRecords(origVoucherNo: string): Promise<ReversalRecordItem[]> {
  return request.get<ReversalRecordItem[]>('/reversal/records', { origVoucherNo })
}

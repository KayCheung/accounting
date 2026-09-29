import request from '@/utils/request'
import type { PageResponse } from './types'

/**
 * 申请分录明细结构
 */
export interface ApplyEntryItem {
  id?: number
  applyNo?: string
  rowNum: number
  debitCredit: number // 1-借, 2-贷
  debitCreditDesc?: string
  subjectCode: string
  subjectName?: string
  accountNo?: string
  amount: number
  currency?: string
  summary?: string
  unilateral?: number // 1-实时, 0-普通
}

/**
 * 手工记账申请列表项
 */
export interface ManualVoucherApplyPageItem {
  id: number
  applyNo: string
  voucherType: string
  tradeType: number
  tradeTypeDesc: string
  accountingDate: string
  summary: string
  totalDebitAmount: number
  totalCreditAmount: number
  isBalanced: boolean
  applyStatus: number // 1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废
  applyStatusDesc: string
  makerName: string
  auditorName?: string
  auditTime?: string
  auditOpinion?: string
  reviewerName?: string
  reviewTime?: string
  reviewOpinion?: string
  bookkeeperName?: string
  bookkeepingTime?: string
  voucherNo?: string
  createTime: string
  entries: ApplyEntryItem[]
}

/**
 * 审批流可追溯审计日志项
 */
export interface ManualVoucherAuditLogItem {
  id: number
  applyNo: string
  action: string
  actionDesc: string
  operatorName: string
  operatorRole: string
  preStatus?: number
  preStatusDesc?: string
  postStatus: number
  postStatusDesc: string
  opinion?: string
  operateTime: string
}

/**
 * 辅助核算分摊项
 */
export interface ManualVoucherAuxiliaryItem {
  id?: number
  applyNo?: string
  entryRowNum: number
  subjectCode?: string
  subjectName?: string
  auxType: string
  auxTypeName?: string
  auxCode: string
  auxName: string
  changeDirection: number // 1-增, 2-减
  changeDirectionDesc?: string
  amount: number
}

/**
 * 凭证附件项
 */
export interface ManualVoucherAttachmentItem {
  id?: number
  applyNo?: string
  fileName: string
  filePath: string
  fileSize?: number
  fileType?: string
  fileSizeFormatted?: string
  createTime?: string
}

/**
 * 申请全景档案与凭证印签详情
 */
export interface ManualVoucherApplyDetail {
  applyNo: string
  voucherNo?: string
  voucherType: string
  tradeType: number
  tradeTypeDesc: string
  accountingDate: string
  summary: string
  attachmentCount: number
  totalDebitAmount: number
  totalCreditAmount: number
  totalAmountInWords: string
  isBalanced: boolean
  applyStatus: number
  applyStatusDesc: string
  makerName: string
  auditorName?: string
  auditTime?: string
  auditOpinion?: string
  reviewerName?: string
  reviewTime?: string
  reviewOpinion?: string
  bookkeeperName?: string
  bookkeepingTime?: string
  createTime: string
  entries: ApplyEntryItem[]
  auditLogs: ManualVoucherAuditLogItem[]
  auxiliaries?: ManualVoucherAuxiliaryItem[]
  attachments?: ManualVoucherAttachmentItem[]
}

/**
 * 制单保存请求
 */
export interface ManualVoucherApplySaveForm {
  applyNo?: string
  voucherType?: string
  tradeType?: number
  accountingDate: string
  summary: string
  makerName: string
  action: 'DRAFT' | 'SUBMIT'
  entries: ApplyEntryItem[]
  auxiliaries?: ManualVoucherAuxiliaryItem[]
  attachments?: ManualVoucherAttachmentItem[]
}

/**
 * 审核请求（初审/复核）
 */
export interface ManualVoucherApplyAuditForm {
  applyNo: string
  action: 'PASS' | 'REJECT'
  operatorName: string
  operatorRole?: string
  opinion?: string
}

/**
 * 记账请求
 */
export interface ManualVoucherApplyPostForm {
  applyNo: string
  bookkeeperName: string
  remark?: string
}

/**
 * 查询参数
 */
export interface ManualVoucherApplyQuery {
  pageNo?: number
  pageSize?: number
  applyNo?: string
  voucherNo?: string
  applyStatus?: number
  makerName?: string
  summary?: string
  startDate?: string
  endDate?: string
  createStartTime?: string
  createEndTime?: string
}

/**
 * 可记账末级科目项
 */
export interface LeafSubjectOption {
  subjectCode: string
  subjectName: string
  subjectLevel: number
  balanceDirection: number
}

// ==================== API 接口请求 ====================

/**
 * 分页查询手工记账申请单
 */
export function getManualVoucherPage(params: ManualVoucherApplyQuery): Promise<PageResponse<ManualVoucherApplyPageItem>> {
  return request.get<PageResponse<ManualVoucherApplyPageItem>>('/manual-voucher/page', params)
}

/**
 * 查询申请全景档案与经典印签详情
 */
export function getManualVoucherDetail(applyNo: string): Promise<ManualVoucherApplyDetail> {
  return request.get<ManualVoucherApplyDetail>(`/manual-voucher/detail/${applyNo}`)
}

/**
 * 保存草稿或提交初审
 */
export function saveOrSubmitManualVoucher(data: ManualVoucherApplySaveForm): Promise<{ applyNo: string; message: string }> {
  return request.post<{ applyNo: string; message: string }>('/manual-voucher/save', data)
}

/**
 * 初审操作（通过/驳回）
 */
export function auditManualVoucher(data: ManualVoucherApplyAuditForm): Promise<void> {
  return request.post<void>('/manual-voucher/audit', data)
}

/**
 * 复核操作（通过/驳回）
 */
export function reviewManualVoucher(data: ManualVoucherApplyAuditForm): Promise<void> {
  return request.post<void>('/manual-voucher/review', data)
}

/**
 * 确认记账（转正落库法定凭证并过账）
 */
export function postManualVoucher(data: ManualVoucherApplyPostForm): Promise<{ voucherNo: string; message: string }> {
  return request.post<{ voucherNo: string; message: string }>('/manual-voucher/post', data)
}

/**
 * 作废草稿或被驳回单据
 */
export function cancelManualVoucher(applyNo: string, operatorName: string, reason?: string): Promise<void> {
  return request.post<void>('/manual-voucher/cancel', null, { params: { applyNo, operatorName, reason } })
}

/**
 * 看板流转状态统计
 */
export function getManualVoucherStatistics(): Promise<{
  total: number
  pendingAudit: number
  pendingReview: number
  pendingBookkeeping: number
  booked: number
  rejected: number
}> {
  return request.get<{
    total: number
    pendingAudit: number
    pendingReview: number
    pendingBookkeeping: number
    booked: number
    rejected: number
  }>('/manual-voucher/statistics')
}

/**
 * 获取允许记账的末级科目列表
 */
export function getLeafSubjects(): Promise<LeafSubjectOption[]> {
  return request.get<LeafSubjectOption[]>('/manual-voucher/leaf-subjects')
}

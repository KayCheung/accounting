// accounting-ui/src/api/auxiliary-report.ts
import request from '@/utils/request'
import type { PageResponse } from '@/api/types'
import type { DictResponse } from '@/api/dict'

/**
 * 辅助核算项响应
 */
export interface AuxiliaryItemResponse {
  auxCode: string
  auxName: string
  recordCount: number
  totalAmount: number
}

/**
 * 辅助核算项金额与占比响应
 */
export interface AuxiliaryItemShareResponse {
  auxCode: string
  auxName: string
  amount: number
  percentage: number
}

/**
 * 辅助核算统计 KPI 概览响应
 */
export interface AuxiliarySummaryResponse {
  totalAmount: number
  debitAmount: number
  creditAmount: number
  itemCount: number
  subjectCount: number
  voucherCount: number
  topItems: AuxiliaryItemShareResponse[]
}

/**
 * 辅助核算交叉对比矩阵行响应
 */
export interface AuxiliaryMatrixRowResponse {
  subjectCode: string
  subjectName: string
  subjectCategory?: number
  subjectCategoryDesc?: string
  amounts: Record<string, number>
  totalAmount: number
  percentage: number
}

/**
 * 辅助核算交叉对比矩阵总响应
 */
export interface AuxiliaryMatrixResponse {
  columns: AuxiliaryItemResponse[]
  rows: AuxiliaryMatrixRowResponse[]
  columnTotals: Record<string, number>
  grandTotal: number
}

/**
 * 辅助核算科目分布明细响应
 */
export interface AuxiliarySubjectDetailResponse {
  subjectCode: string
  subjectName: string
  subjectCategory?: number
  subjectCategoryDesc?: string
  debitAmount: number
  creditAmount: number
  totalAmount: number
  entryCount: number
  percentage: number
}

/**
 * 辅助核算凭证分录记录响应
 */
export interface AuxiliaryEntryRecordResponse {
  id: number
  accountingDate: string
  voucherNo: string
  voucherWord: string
  entryId: string
  summary: string
  auxType: string
  auxCode: string
  auxName: string
  subjectCode: string
  subjectName: string
  changeDirection: number
  changeDirectionDesc: string
  debitCredit: number
  debitCreditDesc: string
  debitAmount: number
  creditAmount: number
  amount: number
}

/**
 * 辅助核算多维查询入参
 */
export interface AuxiliaryLedgerQueryParams {
  auxType: string
  auxCode?: string
  startDate?: string
  endDate?: string
  subjectCode?: string
  subjectCategory?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}

// ==================== 接口方法封装 ====================

/**
 * 获取系统支持的所有辅助核算类别列表
 */
export function getAuxiliaryTypes(): Promise<DictResponse[]> {
  return request.get<DictResponse[]>('/report/auxiliary/types')
}

/**
 * 获取指定类别下有凭证入账记录的核算项目列表
 */
export function getAuxiliaryItems(params: {
  auxType: string
  startDate?: string
  endDate?: string
}): Promise<AuxiliaryItemResponse[]> {
  return request.get<AuxiliaryItemResponse[]>('/report/auxiliary/items', params)
}

/**
 * 查询辅助核算账簿统计 KPI 概览指标
 */
export function getAuxiliarySummary(
  params: AuxiliaryLedgerQueryParams
): Promise<AuxiliarySummaryResponse> {
  return request.get<AuxiliarySummaryResponse>('/report/auxiliary/summary', params)
}

/**
 * 查询辅助核算交叉汇总对比矩阵（透视表）
 */
export function getAuxiliaryMatrix(
  params: AuxiliaryLedgerQueryParams
): Promise<AuxiliaryMatrixResponse> {
  return request.get<AuxiliaryMatrixResponse>('/report/auxiliary/matrix', params)
}

/**
 * 查询辅助核算科目分布明细
 */
export function getAuxiliarySubjectBreakdown(
  params: AuxiliaryLedgerQueryParams
): Promise<AuxiliarySubjectDetailResponse[]> {
  return request.get<AuxiliarySubjectDetailResponse[]>('/report/auxiliary/subjects', params)
}

/**
 * 分页查询辅助核算凭证分录记录
 */
export function getAuxiliaryEntries(
  params: AuxiliaryLedgerQueryParams
): Promise<PageResponse<AuxiliaryEntryRecordResponse>> {
  return request.get<PageResponse<AuxiliaryEntryRecordResponse>>('/report/auxiliary/entries', params)
}

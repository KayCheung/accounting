import request from '@/utils/request'
import type { PageResponse } from './types'

/**
 * 会计科目响应数据结构
 */
export interface SubjectItem {
  id: number
  subjectCode: string
  subjectName: string
  subjectLevel: number
  parentSubjectId: number
  subjectCategory: number // 0=表外, 1=资产, 2=负债, 3=权益, 4=共同, 5=成本, 6=损益
  nature: number          // 1=非特殊, 2=销账, 3=贷款, 4=现金
  debitCredit: number     // 1=借, 2=贷
  leaf: boolean
  allowPost: boolean
  allowOpenAccount: boolean
  status: number          // 1=启用, 2=停用
  parentSubjectCode?: string
  parentSubjectName?: string
  hasChildren?: boolean   // 用于 Element Plus 树表懒加载识别
  children?: SubjectItem[]
}

/**
 * 创建科目请求入参
 */
export interface SubjectCreateRequest {
  subjectCode: string
  subjectName: string
  subjectLevel: number
  parentSubjectId: number
  subjectCategory: number
  nature: number
  debitCredit: number
  leaf?: boolean
  allowPost?: boolean
  allowOpenAccount?: boolean
  status?: number
}

/**
 * 更新科目请求入参
 */
export interface SubjectUpdateRequest {
  subjectName?: string
  subjectCategory?: number
  nature?: number
  leaf?: boolean
  allowPost?: boolean
  allowOpenAccount?: boolean
  status?: number
}

/**
 * 科目分页查询参数
 */
export interface SubjectQueryRequest {
  pageNo?: number
  pageSize?: number
  subjectCategory?: number
  status?: number
  leaf?: boolean
}

/**
 * 科目辅助核算项响应模型
 */
export interface AuxiliaryItem {
  id: number
  subjectCode: string
  auxiliaryType: string
  required: boolean
  defaultAuxCode?: string
}

/**
 * 创建辅助核算项入参
 */
export interface AuxiliaryCreateRequest {
  auxiliaryType: string
  required: boolean
  defaultAuxCode?: string
}

/**
 * 更新辅助核算项入参
 */
export interface AuxiliaryUpdateRequest {
  required?: boolean
  defaultAuxCode?: string
}

/**
 * 账类常量定义
 */
export const SUBJECT_CATEGORIES = [
  { value: 1, label: '资产类', tagType: 'primary' },
  { value: 2, label: '负债类', tagType: 'danger' },
  { value: 3, label: '权益类', tagType: 'warning' },
  { value: 4, label: '共同类', tagType: 'info' },
  { value: 5, label: '成本类', tagType: 'success' },
  { value: 6, label: '损益类', tagType: '' },
  { value: 0, label: '表外科目', tagType: 'info' }
] as const

/**
 * 科目性质常量定义
 */
export const SUBJECT_NATURES = [
  { value: 1, label: '非特殊性科目' },
  { value: 2, label: '销账类科目' },
  { value: 3, label: '贷款类科目' },
  { value: 4, label: '现金类科目' }
] as const

/**
 * 借贷方向常量定义
 */
export const DEBIT_CREDIT_TYPES = [
  { value: 1, label: '借', tagType: 'primary' },
  { value: 2, label: '贷', tagType: 'success' }
] as const

// ==================== 科目基础与树形接口 ====================

/**
 * 查询科目树（懒加载 + 根节点按条件过滤）
 */
export function querySubjectTree(params?: {
  parentId?: number
  status?: number
  subjectCategory?: number
  keyword?: string
}): Promise<SubjectItem[]> {
  return request.get<SubjectItem[]>('/config/subject/tree', params)
}

/**
 * 分页查询科目（平铺）
 */
export function querySubjectPage(params: SubjectQueryRequest): Promise<PageResponse<SubjectItem>> {
  return request.get<PageResponse<SubjectItem>>('/config/subject/page', params)
}

/**
 * 按编码查询单个科目
 */
export function getSubjectByCode(subjectCode: string): Promise<SubjectItem> {
  return request.get<SubjectItem>(`/config/subject/${subjectCode}`)
}

/**
 * 创建科目
 */
export function createSubject(data: SubjectCreateRequest): Promise<void> {
  return request.post<void>('/config/subject', data)
}

/**
 * 更新科目
 */
export function updateSubject(subjectCode: string, data: SubjectUpdateRequest): Promise<void> {
  return request.put<void>(`/config/subject/${subjectCode}`, data)
}

/**
 * 停用科目
 */
export function disableSubject(subjectCode: string): Promise<void> {
  return request.delete<void>(`/config/subject/${subjectCode}`)
}

// ==================== 辅助核算项从表接口 ====================

/**
 * 查询科目的辅助核算项列表
 */
export function listSubjectAuxiliary(subjectCode: string): Promise<AuxiliaryItem[]> {
  return request.get<AuxiliaryItem[]>(`/config/subject/${subjectCode}/auxiliary`)
}

/**
 * 为科目添加辅助核算项
 */
export function createSubjectAuxiliary(subjectCode: string, data: AuxiliaryCreateRequest): Promise<void> {
  return request.post<void>(`/config/subject/${subjectCode}/auxiliary`, data)
}

/**
 * 更新辅助核算项
 */
export function updateSubjectAuxiliary(
  subjectCode: string,
  auxiliaryType: string,
  data: AuxiliaryUpdateRequest
): Promise<void> {
  return request.put<void>(`/config/subject/${subjectCode}/auxiliary/${auxiliaryType}`, data)
}

/**
 * 删除辅助核算项
 */
export function deleteSubjectAuxiliary(subjectCode: string, auxiliaryType: string): Promise<void> {
  return request.delete<void>(`/config/subject/${subjectCode}/auxiliary/${auxiliaryType}`)
}

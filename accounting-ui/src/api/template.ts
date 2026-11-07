import request from '@/utils/request'
import type { PageResponse, SelectOption } from './types'

/**
 * 开户模板响应实体
 */
export interface TemplateResponse {
  id: number
  templateName: string
  businessCode: string
  customerType: number
  autoOpen: boolean
  status: number
  subjectCode: string
  accountType: string
  currency: string
  balanceDirection: number
  acctNoRule: string
  acctNameRule: string
}

export type TemplateItem = TemplateResponse

/**
 * 开户模板分页查询请求
 */
export interface TemplateQueryRequest {
  pageNo?: number
  pageSize?: number
  businessCode?: string
  customerType?: number
  status?: number
}

/**
 * 创建开户模板请求
 */
export interface TemplateCreateRequest {
  templateName: string
  businessCode: string
  customerType: number
  autoOpen: boolean
  subjectCode: string
  accountType: string
  currency?: string
  balanceDirection: number
  acctNoRule: string
  acctNameRule: string
  status: number
}

/**
 * 更新开户模板请求
 */
export interface TemplateUpdateRequest {
  templateName?: string
  accountType?: string
  currency?: string
  balanceDirection?: number
  acctNoRule?: string
  acctNameRule?: string
  autoOpen?: boolean
  status?: number
}

/**
 * 模板科目明细项请求（单科目账户配置）
 */
export interface TemplateItemRequest {
  id?: number
  subjectCode: string
  accountType: string
  currency?: string
  balanceDirection: number
  acctNoRule: string
  acctNameRule: string
}

/**
 * 批量保存开户模板请求（主信息 + 多个科目账户明细）
 */
export interface TemplateGroupSaveRequest {
  templateName: string
  businessCode: string
  customerType: number
  autoOpen: boolean
  status: number
  items: TemplateItemRequest[]
}

/**
 * 前端开户模板组模型（以模板名称+业务线+客户类型聚合展示）
 */
export interface TemplateGroupItem {
  id: string | number
  templateName: string
  businessCode: string
  customerType: number
  autoOpen: boolean
  status: number
  items: TemplateResponse[]
}

/**
 * 客户类型选项映射
 */
export const CUSTOMER_TYPE_OPTIONS: SelectOption<number>[] = [
  { label: '个人', value: 1, tagType: 'success' },
  { label: '企业', value: 2, tagType: 'primary' },
  { label: '其他', value: 99, tagType: 'info' }
]

/**
 * 模板状态选项映射
 */
export const TEMPLATE_STATUS_OPTIONS: SelectOption<number>[] = [
  { label: '待启用', value: 1, tagType: 'warning' },
  { label: '启用', value: 2, tagType: 'success' },
  { label: '停用', value: 3, tagType: 'danger' }
]

/**
 * 余额方向选项映射
 */
export const BALANCE_DIRECTION_OPTIONS: SelectOption<number>[] = [
  { label: '借', value: 1, tagType: 'primary' },
  { label: '贷', value: 2, tagType: 'warning' }
]

/**
 * 分页查询开户模板
 */
export function getTemplatePage(params: TemplateQueryRequest): Promise<PageResponse<TemplateResponse>> {
  return request.get<PageResponse<TemplateResponse>>('/config/template/page', params)
}

/**
 * 查询单个开户模板
 */
export function getTemplateById(templateId: number): Promise<TemplateResponse> {
  return request.get<TemplateResponse>(`/config/template/${templateId}`)
}

/**
 * 创建开户模板
 */
export function createTemplate(data: TemplateCreateRequest): Promise<void> {
  return request.post<void>('/config/template', data)
}

/**
 * 更新开户模板
 */
export function updateTemplate(templateId: number, data: TemplateUpdateRequest): Promise<void> {
  return request.put<void>(`/config/template/${templateId}`, data)
}

/**
 * 批量保存开户模板（多会计科目账户明细）
 */
export function saveTemplateGroup(data: TemplateGroupSaveRequest): Promise<void> {
  return request.post<void>('/config/template/group', data)
}

/**
 * 停用开户模板（联动校验是否有关联账户）
 */
export function disableTemplate(templateId: number): Promise<void> {
  return request.delete<void>(`/config/template/${templateId}`)
}

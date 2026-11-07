/**
 * 接口与数据模型通用类型定义
 */

/**
 * 后端统一返回包装类 Result<T>
 */
export interface ApiResponse<T = any> {
  code: string | number
  message: string
  data: T
  success?: boolean
  timestamp?: number
  traceId?: string
  traceNo?: string
}

/**
 * 后端通用分页响应 PageResponse<T>
 */
export interface PageResponse<T = any> {
  list: T[]
  total: number
  current?: number
  pages?: number
  records?: T[]
  pageNo?: number
  pageSize?: number
}

/**
 * 通用分页请求入参
 */
export interface PageParam {
  pageNo: number
  pageSize: number
  [key: string]: any
}

/**
 * 通用字典/下拉选项
 */
export interface SelectOption<T = string | number> {
  label: string
  value: T
  disabled?: boolean
  tagType?: 'primary' | 'success' | 'warning' | 'info' | 'danger' | ''
  [key: string]: any
}

/**
 * 后端字典项实体 DTO
 */
export interface DictItemDTO {
  id?: number
  dictType: string
  itemCode: string
  itemValue: string
  itemSort?: number
  status?: number
  remark?: string
}

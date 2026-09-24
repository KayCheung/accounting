import request from '@/utils/request'
import type { PageResponse } from './types'

/**
 * 字典响应 DTO
 */
export interface DictResponse {
  id?: number
  dictType: string
  dictCode: string
  dictName: string
  dictNameEn?: string
  sortOrder?: number
  groupKey?: string
  status: number
  system?: boolean
  extJson?: string
  createTime?: string
  updateTime?: string
}

export type DictItem = DictResponse

/**
 * 字典分页查询参数
 */
export interface DictQueryRequest {
  pageNo?: number
  pageSize?: number
  dictType?: string
  status?: number
  groupKey?: string
}

/**
 * 创建字典项入参
 */
export interface DictCreateRequest {
  dictType: string
  dictCode: string
  dictName: string
  dictNameEn?: string
  sortOrder?: number
  groupKey?: string
  status: number
  extJson?: string
}

/**
 * 更新字典项入参
 */
export interface DictUpdateRequest {
  dictName?: string
  dictNameEn?: string
  sortOrder?: number
  groupKey?: string
  status?: number
  extJson?: string
}

/**
 * 分页查询字典项
 */
export function getDictPage(params: DictQueryRequest): Promise<PageResponse<DictResponse>> {
  return request.get<PageResponse<DictResponse>>('/config/dict/page', params)
}

/**
 * 按类型获取字典项列表（供下拉框/缓存使用）
 */
export function getDictByType(dictType: string): Promise<DictResponse[]> {
  return request.get<DictResponse[]>(`/config/dict/${dictType}`)
}

/**
 * 创建字典项
 */
export function createDict(data: DictCreateRequest): Promise<void> {
  return request.post<void>('/config/dict', data)
}

/**
 * 更新字典项
 */
export function updateDict(dictType: string, dictCode: string, data: DictUpdateRequest): Promise<void> {
  return request.put<void>(`/config/dict/${dictType}/${dictCode}`, data)
}

/**
 * 删除字典项（系统内置项不允许删除）
 */
export function deleteDict(dictType: string, dictCode: string): Promise<void> {
  return request.delete<void>(`/config/dict/${dictType}/${dictCode}`)
}

/**
 * 手动刷新字典 Redis 缓存
 */
export function refreshDictCache(dictType?: string): Promise<void> {
  return request.post<void>('/config/dict/cache/refresh', null, {
    params: dictType ? { dictType } : undefined
  })
}

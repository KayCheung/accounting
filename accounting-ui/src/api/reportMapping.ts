import request from '@/utils/request'

export interface ReportItemMappingResponse {
  id: number
  tenantId: number
  reportType: string
  itemCode: string
  itemName: string
  itemCategory: string
  lineNo: number
  displayLevel: number
  subjectCodes?: string
  calcOperator: string
  balanceDirection?: string
  formulaExpr?: string
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface ReportItemMappingQueryRequest {
  pageNo: number
  pageSize: number
  reportType?: string
  keyword?: string
  itemCategory?: string
  displayLevel?: number
  status?: number
}

export interface ReportItemMappingCreateRequest {
  reportType: string
  itemCode: string
  itemName: string
  itemCategory?: string
  lineNo: number
  displayLevel: number
  subjectCodes?: string
  calcOperator: string
  balanceDirection?: string
  formulaExpr?: string
  status?: number
  remark?: string
}

export interface ReportItemMappingUpdateRequest {
  itemName: string
  itemCategory?: string
  lineNo: number
  displayLevel: number
  subjectCodes?: string
  calcOperator: string
  balanceDirection?: string
  formulaExpr?: string
  status?: number
  remark?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pages?: number
  current?: number
}

// 报表类型常量选项
export const REPORT_TYPE_OPTIONS = [
  { label: '资产负债表', value: 'BALANCE_SHEET', tagType: 'primary' },
  { label: '利润表', value: 'INCOME_STATEMENT', tagType: 'success' },
  { label: '现金流量表', value: 'CASH_FLOW', tagType: 'warning' }
]

// 展示层级选项
export const DISPLAY_LEVEL_OPTIONS = [
  { label: '1 - 大类标题', value: 1, tagType: 'info' },
  { label: '2 - 数据明细项', value: 2, tagType: 'primary' },
  { label: '3 - 合计/小计项', value: 3, tagType: 'success' }
]

// 计算规则选项
export const CALC_OPERATOR_OPTIONS = [
  { label: '+ (科目累加)', value: '+' },
  { label: '- (科目抵扣/减项)', value: '-' },
  { label: 'SUM_CATEGORY (分类小计)', value: 'SUM_CATEGORY' },
  { label: 'SUM_TOTAL (报表总计)', value: 'SUM_TOTAL' },
  { label: 'UNDISTRIBUTED_PROFIT (未分配利润/含未结转损益)', value: 'UNDISTRIBUTED_PROFIT' },
  { label: 'FORMULA (公式计算)', value: 'FORMULA' }
]

// 分类选项
export const CATEGORY_OPTIONS = [
  { label: '流动资产 (CURRENT_ASSET)', value: 'CURRENT_ASSET' },
  { label: '非流动资产 (NON_CURRENT_ASSET)', value: 'NON_CURRENT_ASSET' },
  { label: '资产总计 (ASSET_TOTAL)', value: 'ASSET_TOTAL' },
  { label: '流动负债 (CURRENT_LIABILITY)', value: 'CURRENT_LIABILITY' },
  { label: '非流动负债 (NON_CURRENT_LIABILITY)', value: 'NON_CURRENT_LIABILITY' },
  { label: '负债合计 (LIAB_TOTAL)', value: 'LIAB_TOTAL' },
  { label: '所有者权益 (EQUITY)', value: 'EQUITY' },
  { label: '负债和权益总计 (LIAB_EQUITY_TOTAL)', value: 'LIAB_EQUITY_TOTAL' },
  { label: '营业收入 (OPERATING_REVENUE)', value: 'OPERATING_REVENUE' },
  { label: '营业成本 (OPERATING_COST)', value: 'OPERATING_COST' },
  { label: '期间费用与税金 (EXPENSE)', value: 'EXPENSE' },
  { label: '投资与营业外收益 (INCOME)', value: 'INCOME' },
  { label: '利润指标 (PROFIT)', value: 'PROFIT' }
]

/**
 * 分页查询报表映射配置
 */
export function pageReportItemMappings(params: ReportItemMappingQueryRequest) {
  return request.get<PageResult<ReportItemMappingResponse>>('/config/report-mapping/page', params)
}

/**
 * 查询单条配置详情
 */
export function getReportItemMappingById(id: number) {
  return request.get<ReportItemMappingResponse>(`/config/report-mapping/${id}`)
}

/**
 * 新增报表配置项
 */
export function createReportItemMapping(data: ReportItemMappingCreateRequest) {
  return request.post<number>('/config/report-mapping', data)
}

/**
 * 修改报表配置项
 */
export function updateReportItemMapping(id: number, data: ReportItemMappingUpdateRequest) {
  return request.put<void>(`/config/report-mapping/${id}`, data)
}

/**
 * 切换启用/停用状态
 */
export function updateReportItemMappingStatus(id: number, status: number) {
  return request.put<void>(`/config/report-mapping/${id}/status`, null, {
    params: { status }
  })
}

/**
 * 删除报表配置项
 */
export function deleteReportItemMapping(id: number) {
  return request.delete<void>(`/config/report-mapping/${id}`)
}

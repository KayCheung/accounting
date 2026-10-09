// accounting-ui/src/api/report.ts
import request from '@/utils/request'

/**
 * 资产负债表查询参数
 */
export interface BalanceSheetQueryParams {
  accountingDate: string
  compareYearStart?: boolean
}

/**
 * 资产负债表行项目
 */
export interface BalanceSheetItem {
  rowNo: number
  itemName: string
  itemLevel: number
  subjectCodes?: string
  endAmount: number
  beginAmount: number
}

/**
 * 资产负债表总响应
 */
export interface BalanceSheetResponse {
  accountingDate: string
  compareDate: string
  unitName: string
  currency: string
  balanced: boolean
  diffAmount: number
  totalAssetEnd: number
  totalAssetBegin: number
  totalLiabilityAndEquityEnd: number
  totalLiabilityAndEquityBegin: number
  assetItems: BalanceSheetItem[]
  liabilityAndEquityItems: BalanceSheetItem[]
}

/**
 * 利润表查询参数
 */
export interface IncomeStatementQueryParams {
  year: number
  month: number
  compareType?: number // 1-上年同期, 2-上月环比, 0-不对比
}

/**
 * 利润表行项目
 */
export interface IncomeStatementItem {
  rowNo: number
  itemName: string
  itemLevel: number
  currentAmount: number
  yearTotalAmount: number
  compareAmount: number
  growthRate: number
}

/**
 * 利润表顶层财务核心 KPI
 */
export interface IncomeStatementKpi {
  revenueMonth: number
  operatingProfitMonth: number
  netProfitMonth: number
  grossMarginRate: number
  netProfitYearTotal: number
  revenueYoY: number
  netProfitYoY: number
}

/**
 * 利润表总响应
 */
export interface IncomeStatementResponse {
  periodDesc: string
  accountingDate: string
  currency: string
  unitName: string
  kpi: IncomeStatementKpi
  items: IncomeStatementItem[]
}

/**
 * 科目总账查询参数
 */
export interface GeneralLedgerQueryParams {
  startDate: string
  endDate: string
  startSubjectCode?: string
  endSubjectCode?: string
  subjectLevel?: number
  showZeroBalance?: boolean
  keyword?: string
}

/**
 * 科目总账行项目
 */
export interface GeneralLedgerItem {
  subjectCode: string
  subjectName: string
  subjectLevel: number
  balanceDirection: number
  balanceDirectionDesc: string
  beginBalance: number
  debitAmount: number
  creditAmount: number
  endBalance: number
}

/**
 * 科目总账总响应
 */
export interface GeneralLedgerResponse {
  startDate: string
  endDate: string
  totalSubjectCount: number
  totalBeginDebit: number
  totalBeginCredit: number
  totalPeriodDebit: number
  totalPeriodCredit: number
  totalEndDebit: number
  totalEndCredit: number
  isBalanced: boolean
  items: GeneralLedgerItem[]
}

/**
 * 科目明细账查询参数
 */
export interface SubsidiaryLedgerQueryParams {
  subjectCode: string
  accountNo?: string
  startDate: string
  endDate: string
  summaryKeyword?: string
  minAmount?: number
  maxAmount?: number
}

/**
 * 科目明细账行流水
 */
export interface SubsidiaryLedgerItem {
  rowType: 'BEGIN_BALANCE' | 'ENTRY' | 'PERIOD_TOTAL' | 'YEAR_TOTAL'
  accountingDate: string
  voucherNo?: string
  entryId?: string
  summary: string
  debitAmount: number
  creditAmount: number
  balanceDirection: number
  balanceDirectionDesc: string
  balance: number
}

/**
 * 科目明细账总响应
 */
export interface SubsidiaryLedgerResponse {
  subjectCode: string
  subjectName: string
  subjectLevel: number
  balanceDirectionDesc: string
  startDate: string
  endDate: string
  beginBalance: number
  endBalance: number
  totalDebitAmount: number
  totalCreditAmount: number
  items: SubsidiaryLedgerItem[]
}

// ==================== 接口方法封装 ====================

/**
 * 获取资产负债表
 */
export function getBalanceSheet(params: BalanceSheetQueryParams): Promise<BalanceSheetResponse> {
  return request.get<BalanceSheetResponse>('/report/balance-sheet', params)
}

/**
 * 获取利润表
 */
export function getIncomeStatement(params: IncomeStatementQueryParams): Promise<IncomeStatementResponse> {
  return request.get<IncomeStatementResponse>('/report/income-statement', params)
}

/**
 * 获取科目总账
 */
export function getGeneralLedger(params: GeneralLedgerQueryParams): Promise<GeneralLedgerResponse> {
  return request.get<GeneralLedgerResponse>('/report/general-ledger', params)
}

/**
 * 获取科目明细账
 */
export function getSubsidiaryLedger(params: SubsidiaryLedgerQueryParams): Promise<SubsidiaryLedgerResponse> {
  return request.get<SubsidiaryLedgerResponse>('/report/subsidiary-ledger', params)
}

/**
 * 触发报表重新生成与预热
 */
export function generateReports(accountingDate?: string): Promise<void> {
  return request.post<void>('/report/generate', null, { params: { accountingDate } })
}

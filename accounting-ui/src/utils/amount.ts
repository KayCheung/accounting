/**
 * 金融金额格式化与处理工具
 * 严格遵循 docs/ai-rules/agents/frontend.md 规范
 */

/**
 * 格式化金额：千分位 + 固定保留小数位
 * 示例：1234567.89 -> '1,234,567.89'
 *
 * @param value 金额（数字、字符串或空）
 * @param decimals 小数位数，默认 2
 * @returns 格式化后的金额字符串，非法值返回 '0.00'
 */
export function formatAmount(value: string | number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || value === '') return (0).toFixed(decimals)
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return (0).toFixed(decimals)
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  })
}

/**
 * 判断金额是否为负数
 */
export function isNegativeAmount(value: string | number | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false
  const num = typeof value === 'string' ? parseFloat(value) : value
  return !isNaN(num) && num < 0
}

/**
 * 解析输入金额为合法的数字字符串，过滤非法字符
 */
export function cleanAmountInput(input: string): string {
  // 仅允许数字和一个小数点
  let cleaned = input.replace(/[^\d.]/g, '')
  const parts = cleaned.split('.')
  if (parts.length > 2) {
    cleaned = parts[0] + '.' + parts.slice(1).join('')
  }
  return cleaned
}

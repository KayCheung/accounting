<!-- 文件路径：src/views/report/income-statement/index.vue -->
<template>
  <div class="income-statement-page">
    <!-- 顶部检索与操作栏 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="报表模式">
          <el-radio-group v-model="periodMode" @change="handleSearch">
            <el-radio-button label="monthly">自然月度</el-radio-button>
            <el-radio-button label="custom">自定义期间</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 自然月度模式选项 -->
        <template v-if="periodMode === 'monthly'">
          <el-form-item label="会计年度">
            <el-select v-model="queryForm.year" style="width: 100px;" @change="handleSearch">
              <el-option :label="2026" :value="2026" />
              <el-option :label="2025" :value="2025" />
              <el-option :label="2024" :value="2024" />
            </el-select>
          </el-form-item>

          <el-form-item label="月份">
            <el-select v-model="queryForm.month" style="width: 110px;" @change="handleSearch">
              <el-option
                v-for="m in 12"
                :key="m"
                :label="`${m}月`"
                :value="m"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="对比方式">
            <el-select v-model="queryForm.compareType" style="width: 140px;" @change="handleSearch">
              <el-option label="上年同期对比" :value="1" />
              <el-option label="上月环比对比" :value="2" />
              <el-option label="不对比" :value="0" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 自定义期间模式选项 -->
        <template v-else>
          <el-form-item label="报告期间">
            <el-date-picker
              v-model="reportDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              :clearable="false"
              style="width: 240px;"
              @change="handleReportRangeChange"
            />
          </el-form-item>

          <el-form-item label="对比期间">
            <el-date-picker
              v-model="compareDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              clearable
              style="width: 240px;"
              @change="handleCompareRangeChange"
            />
          </el-form-item>

          <el-form-item label="快捷对比">
            <el-button-group>
              <el-button size="small" @click="setCustomComparePreset('last-year')">上年同期</el-button>
              <el-button size="small" @click="setCustomComparePreset('last-period')">上期环比</el-button>
            </el-button-group>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            生成报表
          </el-button>
          <el-button :icon="Download" @click="handleExport">
            导出 Excel
          </el-button>
          <el-button :icon="Printer" @click="handlePrint">
            打印
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 顶层核心财务 KPI 摘要卡片 -->
    <div v-if="reportData?.kpi" class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">{{ periodMode === 'monthly' ? '本月营业收入' : '本期营业收入' }}</div>
        <div class="kpi-value mono">{{ formatMoney(reportData.kpi.revenueMonth) }}</div>
        <div class="kpi-sub">
          <span :class="reportData.kpi.revenueYoY >= 0 ? 'up' : 'down'">
            {{ reportData.kpi.revenueYoY >= 0 ? '▲' : '▼' }} {{ Math.abs(reportData.kpi.revenueYoY) }}%
          </span>
          较对比期
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">{{ periodMode === 'monthly' ? '本月营业利润' : '本期营业利润' }}</div>
        <div class="kpi-value green mono">{{ formatMoney(reportData.kpi.operatingProfitMonth) }}</div>
        <div class="kpi-sub">
          <span class="up">▲ 稳健</span> 运营健康度
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">{{ periodMode === 'monthly' ? '本月净利润' : '本期净利润' }}</div>
        <div class="kpi-value green mono">{{ formatMoney(reportData.kpi.netProfitMonth) }}</div>
        <div class="kpi-sub">
          <span :class="reportData.kpi.netProfitYoY >= 0 ? 'up' : 'down'">
            {{ reportData.kpi.netProfitYoY >= 0 ? '▲' : '▼' }} {{ Math.abs(reportData.kpi.netProfitYoY) }}%
          </span>
          较对比期
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">{{ periodMode === 'monthly' ? '本月毛利率' : '本期毛利率' }}</div>
        <div class="kpi-value mono">{{ reportData.kpi.grossMarginRate }}%</div>
        <div class="kpi-sub">
          <span class="up">▲ 核心盈利指标</span>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">本年累计净利润</div>
        <div class="kpi-value green mono">{{ formatMoney(reportData.kpi.netProfitYearTotal) }}</div>
        <div class="kpi-sub">
          累计至报告期末
        </div>
      </div>
    </div>

    <!-- 多步式利润表正文卡片 -->
    <div class="fin-card sheet-card">
      <div class="report-header">
        <h2 class="report-title">利 润 表</h2>
        <div class="report-meta">
          <span>编制单位：{{ reportData?.unitName || '智能账务核心企业' }}</span>
          <span>期间：{{ reportData?.periodDesc }}</span>
          <span>币种：{{ reportData?.currency || 'CNY' }} (元)</span>
        </div>
      </div>

      <div v-loading="loading" class="table-container">
        <table class="report-table">
          <thead>
            <tr>
              <th style="width: 35%; text-align: left;">项 目</th>
              <th style="width: 8%; text-align: center;">行次</th>
              <th style="width: 17%; text-align: right;">{{ periodMode === 'monthly' ? '本月金额' : '本期金额' }}</th>
              <th style="width: 17%; text-align: right;">本年累计金额</th>
              <th style="width: 15%; text-align: right;">对比期金额</th>
              <th style="width: 8%; text-align: right;">增长率</th>
            </tr>
          </thead>
          <tbody>
            <template
              v-for="item in reportData?.items || []"
              :key="item.rowNo"
            >
              <tr :class="getRowClass(item)">
                <td class="col-name" :style="{ paddingLeft: getIndent(item.itemLevel) }">
                  <span
                    v-if="item.detailSubjects && item.detailSubjects.length > 0"
                    class="expand-icon"
                    title="展开/折叠明细科目"
                    @click="toggleExpand(item.rowNo)"
                  >
                    <el-icon :class="{ 'is-rotated': isExpanded(item.rowNo) }">
                      <ArrowRight />
                    </el-icon>
                  </span>
                  <span :class="{ 'is-bold': item.itemLevel === 1 || item.itemLevel === 3 }">
                    {{ item.itemName }}
                  </span>
                  <el-tag
                    v-if="item.detailSubjects && item.detailSubjects.length > 1"
                    size="small"
                    type="info"
                    effect="light"
                    class="subject-count-tag"
                    @click="toggleExpand(item.rowNo)"
                  >
                    {{ item.detailSubjects.length }}科目
                  </el-tag>
                </td>
                <td class="col-row-no">{{ item.rowNo }}</td>
                <td class="col-amount mono">{{ formatMoney(item.currentAmount) }}</td>
                <td class="col-amount mono">{{ formatMoney(item.yearTotalAmount) }}</td>
                <td class="col-amount mono">{{ formatMoney(item.compareAmount) }}</td>
                <td class="col-growth" :class="item.growthRate >= 0 ? 'text-up' : 'text-down'">
                  {{ item.growthRate !== 0 ? (item.growthRate > 0 ? '+' : '') + item.growthRate + '%' : '-' }}
                </td>
              </tr>

              <!-- 损益明细展开行 -->
              <tr
                v-if="isExpanded(item.rowNo) && item.detailSubjects && item.detailSubjects.length > 0"
                class="row-detail-expanded"
              >
                <td colspan="6" class="expanded-cell">
                  <div class="sub-detail-wrapper">
                    <div class="sub-detail-header">包含底层损益科目明细 ({{ item.itemName }})：</div>
                    <table class="inner-detail-table">
                      <thead>
                        <tr>
                          <th style="width: 25%;">科目编码</th>
                          <th style="width: 30%;">科目名称</th>
                          <th style="width: 15%; text-align: right;">{{ periodMode === 'monthly' ? '本月发生额' : '本期发生额' }}</th>
                          <th style="width: 15%; text-align: right;">本年累计发生额</th>
                          <th style="width: 15%; text-align: right;">对比期发生额</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="sub in item.detailSubjects" :key="sub.subjectCode">
                          <td class="mono sub-code">{{ sub.subjectCode }}</td>
                          <td>{{ sub.subjectName }}</td>
                          <td class="mono" style="text-align: right;">{{ formatMoney(sub.currentAmount) }}</td>
                          <td class="mono" style="text-align: right;">{{ formatMoney(sub.yearTotalAmount) }}</td>
                          <td class="mono" style="text-align: right;">{{ formatMoney(sub.compareAmount) }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  Search,
  Download,
  Printer,
  ArrowRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getIncomeStatement,
  type IncomeStatementResponse,
  type IncomeStatementItem
} from '@/api/report'

const now = new Date()
const currentYear = now.getFullYear()
const currentMonth = now.getMonth() + 1
const curDay = String(now.getDate()).padStart(2, '0')
const curMonthStr = String(currentMonth).padStart(2, '0')

const defaultStart = `${currentYear}-${curMonthStr}-01`
const defaultEnd = `${currentYear}-${curMonthStr}-${curDay}`
const defaultCompareStart = `${currentYear - 1}-${curMonthStr}-01`
const defaultCompareEnd = `${currentYear - 1}-${curMonthStr}-${curDay}`

const periodMode = ref<'monthly' | 'custom'>('monthly')

const queryForm = reactive({
  year: currentYear,
  month: currentMonth,
  compareType: 1,
  startDate: defaultStart,
  endDate: defaultEnd,
  compareStartDate: defaultCompareStart,
  compareEndDate: defaultCompareEnd
})

const reportDateRange = ref<[string, string]>([defaultStart, defaultEnd])
const compareDateRange = ref<[string, string]>([defaultCompareStart, defaultCompareEnd])

const loading = ref(false)
const reportData = ref<IncomeStatementResponse | null>(null)

// 展开折叠状态
const expandedRows = ref<Set<number>>(new Set())

function isExpanded(rowNo: number): boolean {
  return expandedRows.value.has(rowNo)
}

function toggleExpand(rowNo: number) {
  if (expandedRows.value.has(rowNo)) {
    expandedRows.value.delete(rowNo)
  } else {
    expandedRows.value.add(rowNo)
  }
}

function handleReportRangeChange(val: [string, string]) {
  if (val && val.length === 2) {
    queryForm.startDate = val[0]
    queryForm.endDate = val[1]
  }
}

function handleCompareRangeChange(val: [string, string]) {
  if (val && val.length === 2) {
    queryForm.compareStartDate = val[0]
    queryForm.compareEndDate = val[1]
  }
}

function setCustomComparePreset(type: 'last-year' | 'last-period') {
  if (!queryForm.startDate || !queryForm.endDate) return
  const sParts = queryForm.startDate.split('-').map(Number)
  const eParts = queryForm.endDate.split('-').map(Number)
  const sDate = new Date(sParts[0], sParts[1] - 1, sParts[2])
  const eDate = new Date(eParts[0], eParts[1] - 1, eParts[2])

  if (type === 'last-year') {
    const cs = `${sParts[0] - 1}-${String(sParts[1]).padStart(2, '0')}-${String(sParts[2]).padStart(2, '0')}`
    const ce = `${eParts[0] - 1}-${String(eParts[1]).padStart(2, '0')}-${String(eParts[2]).padStart(2, '0')}`
    queryForm.compareStartDate = cs
    queryForm.compareEndDate = ce
    compareDateRange.value = [cs, ce]
  } else if (type === 'last-period') {
    const diffMs = eDate.getTime() - sDate.getTime()
    const ceDate = new Date(sDate.getTime() - 24 * 3600 * 1000)
    const csDate = new Date(ceDate.getTime() - diffMs)
    const cs = csDate.toISOString().split('T')[0]
    const ce = ceDate.toISOString().split('T')[0]
    queryForm.compareStartDate = cs
    queryForm.compareEndDate = ce
    compareDateRange.value = [cs, ce]
  }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    let params: any
    if (periodMode.value === 'monthly') {
      params = {
        year: queryForm.year,
        month: queryForm.month,
        compareType: queryForm.compareType
      }
    } else {
      params = {
        startDate: queryForm.startDate,
        endDate: queryForm.endDate,
        compareStartDate: queryForm.compareStartDate || undefined,
        compareEndDate: queryForm.compareEndDate || undefined
      }
    }
    const res = await getIncomeStatement(params)
    reportData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载利润表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

function handleExport() {
  ElMessage.info('正在导出利润表 Excel 文件...')
}

function handlePrint() {
  window.print()
}

function formatMoney(num?: number | null): string {
  if (num === null || num === undefined) return '0.00'
  return Number(num).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function getIndent(level: number): string {
  if (level === 2) return '24px'
  if (level === 3) return '8px'
  return '8px'
}

function getRowClass(item: IncomeStatementItem): string {
  if (item.itemLevel === 3) return 'row-highlight'
  if (item.itemLevel === 1) return 'row-main'
  return 'row-detail'
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.income-statement-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fin-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

.kpi-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 8px;

  .kpi-label {
    font-size: 13px;
    color: #8c8c8c;
  }

  .kpi-value {
    font-size: 22px;
    font-weight: 700;
    color: #1f2229;

    &.green {
      color: #389e0d;
    }
  }

  .kpi-sub {
    font-size: 12px;
    color: #8c8c8c;

    .up {
      color: #389e0d;
      font-weight: 600;
    }

    .down {
      color: #cf1322;
      font-weight: 600;
    }
  }
}

.sheet-card {
  padding: 24px;
}

.report-header {
  text-align: center;
  margin-bottom: 24px;

  .report-title {
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 4px;
    color: #1f2229;
    margin: 0 0 10px 0;
  }

  .report-meta {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    color: #595959;
    padding: 0 8px;
  }
}

.table-container {
  overflow-x: auto;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    background: #fafafa;
    color: #262626;
    font-weight: 600;
    padding: 10px 8px;
    border-top: 1px solid #d9d9d9;
    border-bottom: 1px solid #d9d9d9;
  }

  td {
    padding: 8px;
    border-bottom: 1px solid #f0f0f0;
    color: #262626;
  }

  .col-row-no {
    text-align: center;
    color: #8c8c8c;
    font-size: 12px;
  }

  .col-amount {
    text-align: right;
  }

  .col-growth {
    text-align: right;
    font-size: 12px;
    font-weight: 600;
  }

  .text-up {
    color: #389e0d;
  }

  .text-down {
    color: #cf1322;
  }

  .is-bold {
    font-weight: 600;
  }

  .row-main td {
    background: #fafafa;
    font-weight: 600;
    color: #262626;
  }

  .row-highlight td {
    background: #f6ffed;
    font-weight: 700;
    border-top: 1px solid #b7eb8f;
    border-bottom: 2px solid #52c41a;
    color: #135200;
  }

  .expand-icon {
    display: inline-flex;
    align-items: center;
    cursor: pointer;
    margin-right: 4px;
    vertical-align: middle;
    color: #409eff;

    .el-icon {
      transition: transform 0.2s ease-in-out;
      font-size: 13px;

      &.is-rotated {
        transform: rotate(90deg);
      }
    }
  }

  .subject-count-tag {
    cursor: pointer;
    margin-left: 6px;
    font-size: 11px;
    padding: 0 4px;
    height: 18px;
    line-height: 16px;
  }

  .row-detail-expanded {
    background-color: #fcfdfe;

    td.expanded-cell {
      padding: 6px 12px 12px 12px;
      border-bottom: 1px dashed #dcdfe6;
    }
  }

  .sub-detail-wrapper {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 8px 12px;

    .sub-detail-header {
      font-size: 12px;
      font-weight: 600;
      color: #3b82f6;
      margin-bottom: 6px;
    }

    .inner-detail-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;

      th {
        background: #edf2f7;
        color: #4a5568;
        font-weight: 600;
        padding: 5px 8px;
        border: 1px solid #e2e8f0;
      }

      td {
        padding: 5px 8px;
        border: 1px solid #e2e8f0;
        color: #2d3748;
      }

      .sub-code {
        color: #3182ce;
        font-weight: 500;
      }
    }
  }
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>

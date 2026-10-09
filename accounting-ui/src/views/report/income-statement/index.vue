<!-- 文件路径：src/views/report/income-statement/index.vue -->
<template>
  <div class="income-statement-page">
    <!-- 顶部检索与操作栏 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="会计年度">
          <el-select v-model="queryForm.year" style="width: 120px;" @change="handleSearch">
            <el-option :label="2026" :value="2026" />
            <el-option :label="2025" :value="2025" />
            <el-option :label="2024" :value="2024" />
          </el-select>
        </el-form-item>

        <el-form-item label="报告期(月份)">
          <el-select v-model="queryForm.month" style="width: 140px;" @change="handleSearch">
            <el-option
              v-for="m in 12"
              :key="m"
              :label="`${m}月 (1-${m}月累计)`"
              :value="m"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="对比方式">
          <el-select v-model="queryForm.compareType" style="width: 150px;" @change="handleSearch">
            <el-option label="上年同期对比" :value="1" />
            <el-option label="上月环比对比" :value="2" />
            <el-option label="不对比" :value="0" />
          </el-select>
        </el-form-item>

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

    <!-- 顶层核心财务 KPI 摘要卡片（吸纳原型亮点） -->
    <div v-if="reportData?.kpi" class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">本月营业收入</div>
        <div class="kpi-value mono">{{ formatMoney(reportData.kpi.revenueMonth) }}</div>
        <div class="kpi-sub">
          <span :class="reportData.kpi.revenueYoY >= 0 ? 'up' : 'down'">
            {{ reportData.kpi.revenueYoY >= 0 ? '▲' : '▼' }} {{ Math.abs(reportData.kpi.revenueYoY) }}%
          </span>
          较上年同期
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">本月营业利润</div>
        <div class="kpi-value green mono">{{ formatMoney(reportData.kpi.operatingProfitMonth) }}</div>
        <div class="kpi-sub">
          <span class="up">▲ 稳健</span> 运营健康度
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">本月净利润</div>
        <div class="kpi-value green mono">{{ formatMoney(reportData.kpi.netProfitMonth) }}</div>
        <div class="kpi-sub">
          <span :class="reportData.kpi.netProfitYoY >= 0 ? 'up' : 'down'">
            {{ reportData.kpi.netProfitYoY >= 0 ? '▲' : '▼' }} {{ Math.abs(reportData.kpi.netProfitYoY) }}%
          </span>
          较上年同期
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">本月毛利率</div>
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
              <th style="width: 17%; text-align: right;">本月金额</th>
              <th style="width: 17%; text-align: right;">本年累计金额</th>
              <th style="width: 15%; text-align: right;">对比期金额</th>
              <th style="width: 8%; text-align: right;">增长率</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in reportData?.items || []"
              :key="item.rowNo"
              :class="getRowClass(item)"
            >
              <td class="col-name" :style="{ paddingLeft: getIndent(item.itemLevel) }">
                <span :class="{ 'is-bold': item.itemLevel === 1 || item.itemLevel === 3 }">
                  {{ item.itemName }}
                </span>
              </td>
              <td class="col-row-no">{{ item.rowNo }}</td>
              <td class="col-amount mono">{{ formatMoney(item.currentAmount) }}</td>
              <td class="col-amount mono">{{ formatMoney(item.yearTotalAmount) }}</td>
              <td class="col-amount mono">{{ formatMoney(item.compareAmount) }}</td>
              <td class="col-growth" :class="item.growthRate >= 0 ? 'text-up' : 'text-down'">
                {{ item.growthRate !== 0 ? (item.growthRate > 0 ? '+' : '') + item.growthRate + '%' : '-' }}
              </td>
            </tr>
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
  Printer
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

const queryForm = reactive({
  year: currentYear,
  month: currentMonth,
  compareType: 1
})

const loading = ref(false)
const reportData = ref<IncomeStatementResponse | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getIncomeStatement({
      year: queryForm.year,
      month: queryForm.month,
      compareType: queryForm.compareType
    })
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
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>

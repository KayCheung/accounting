<!-- 文件路径：src/views/report/balance-sheet/index.vue -->
<template>
  <div class="balance-sheet-page">
    <!-- 顶部检索与操作栏 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="报告期截止日期">
          <el-date-picker
            v-model="queryForm.accountingDate"
            type="date"
            placeholder="选择报告期截止日"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 170px;"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item label="对比年初">
          <el-switch
            v-model="queryForm.compareYearStart"
            active-text="对比"
            inactive-text="不对比"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            生成报表
          </el-button>
          <el-button :icon="Refresh" @click="handleTriggerGenerate">
            预热刷新
          </el-button>
          <el-button :icon="Download" @click="handleExport">
            导出报表
          </el-button>
          <el-button :icon="Printer" @click="handlePrint">
            打印
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 试算平衡状态提醒栏 -->
    <div v-if="reportData" class="balance-status-bar" :class="{ 'is-balanced': reportData.balanced, 'is-unbalanced': !reportData.balanced }">
      <div class="status-left">
        <el-icon :size="18">
          <CircleCheckFilled v-if="reportData.balanced" />
          <WarningFilled v-else />
        </el-icon>
        <span class="status-title">
          {{ reportData.balanced ? '资产负债试算借贷平衡' : '警告：资产负债未轧平！' }}
        </span>
        <span class="status-desc">
          编制基准：{{ reportData.accountingDate }} | 对比基准：{{ reportData.compareDate }} | 单位：{{ reportData.currency }}（元）
        </span>
      </div>
      <div class="status-right">
        <span>资产总计: <b class="mono">{{ formatMoney(reportData.totalAssetEnd) }}</b></span>
        <span class="divider">|</span>
        <span>负债与权益总计: <b class="mono">{{ formatMoney(reportData.totalLiabilityAndEquityEnd) }}</b></span>
        <span v-if="!reportData.balanced" class="diff-tag">
          差额: {{ formatMoney(reportData.diffAmount) }}
        </span>
      </div>
    </div>

    <!-- 报表标题与抬头 -->
    <div class="fin-card sheet-card">
      <div class="report-header">
        <h2 class="report-title">资 产 负 债 表</h2>
        <div class="report-meta">
          <span>编制单位：{{ reportData?.unitName || '智能账务核心企业' }}</span>
          <span>报告日期：{{ reportData?.accountingDate || queryForm.accountingDate }}</span>
          <span>币种：{{ reportData?.currency || 'CNY' }} (元)</span>
        </div>
      </div>

      <!-- 经典账户式双栏对称表格 -->
      <div v-loading="loading" class="sheet-dual-container">
        <!-- 左侧：资产方 -->
        <div class="sheet-column asset-column">
          <table class="report-table">
            <thead>
              <tr>
                <th style="width: 45%;">资 产</th>
                <th style="width: 10%; text-align: center;">行次</th>
                <th style="width: 22.5%; text-align: right;">期末余额</th>
                <th style="width: 22.5%; text-align: right;">年初余额</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in reportData?.assetItems || []"
                :key="'asset_' + item.rowNo"
                :class="getRowClass(item)"
              >
                <td class="col-name" :style="{ paddingLeft: item.itemLevel === 2 ? '24px' : '8px' }">
                  <span :class="{ 'is-bold': item.itemLevel !== 2 }">{{ item.itemName }}</span>
                  <span v-if="item.subjectCodes" class="sub-hint">({{ item.subjectCodes }})</span>
                </td>
                <td class="col-row-no">{{ item.rowNo }}</td>
                <td class="col-amount mono">
                  {{ item.itemLevel === 1 ? '' : formatMoney(item.endAmount) }}
                </td>
                <td class="col-amount mono">
                  {{ item.itemLevel === 1 ? '' : formatMoney(item.beginAmount) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 中间轻度分割线 -->
        <div class="sheet-divider"></div>

        <!-- 右侧：负债及所有者权益方 -->
        <div class="sheet-column liability-column">
          <table class="report-table">
            <thead>
              <tr>
                <th style="width: 45%;">负债和所有者权益</th>
                <th style="width: 10%; text-align: center;">行次</th>
                <th style="width: 22.5%; text-align: right;">期末余额</th>
                <th style="width: 22.5%; text-align: right;">年初余额</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in reportData?.liabilityAndEquityItems || []"
                :key="'liab_' + item.rowNo"
                :class="getRowClass(item)"
              >
                <td class="col-name" :style="{ paddingLeft: item.itemLevel === 2 ? '24px' : '8px' }">
                  <span :class="{ 'is-bold': item.itemLevel !== 2 }">{{ item.itemName }}</span>
                  <span v-if="item.subjectCodes" class="sub-hint">({{ item.subjectCodes }})</span>
                </td>
                <td class="col-row-no">{{ item.rowNo }}</td>
                <td class="col-amount mono">
                  {{ item.itemLevel === 1 ? '' : formatMoney(item.endAmount) }}
                </td>
                <td class="col-amount mono">
                  {{ item.itemLevel === 1 ? '' : formatMoney(item.beginAmount) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  Search,
  Refresh,
  Download,
  Printer,
  CircleCheckFilled,
  WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getBalanceSheet,
  generateReports,
  type BalanceSheetResponse,
  type BalanceSheetItem
} from '@/api/report'

// 默认日期为当月最后一天或当前日期
const today = new Date().toISOString().split('T')[0]

const queryForm = reactive({
  accountingDate: today,
  compareYearStart: true
})

const loading = ref(false)
const reportData = ref<BalanceSheetResponse | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getBalanceSheet({
      accountingDate: queryForm.accountingDate,
      compareYearStart: queryForm.compareYearStart
    })
    reportData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载资产负债表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

async function handleTriggerGenerate() {
  try {
    await generateReports(queryForm.accountingDate)
    ElMessage.success('财务报表预热刷新成功')
    fetchData()
  } catch (err: any) {
    ElMessage.error(err.message || '刷新预热失败')
  }
}

function handleExport() {
  ElMessage.info('正在导出资产负债表 Excel 文件...')
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

function getRowClass(item: BalanceSheetItem): string {
  if (item.itemLevel === 3) return 'row-total'
  if (item.itemLevel === 1) return 'row-header'
  return 'row-detail'
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.balance-sheet-page {
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

.balance-status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 14px;

  &.is-balanced {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
    color: #389e0d;
  }

  &.is-unbalanced {
    background: #fff2f0;
    border: 1px solid #ffccc7;
    color: #cf1322;
  }

  .status-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .status-title {
      font-weight: 600;
    }

    .status-desc {
      color: #8c8c8c;
      font-size: 13px;
      margin-left: 12px;
    }
  }

  .status-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .divider {
      color: #d9d9d9;
    }

    .diff-tag {
      background: #cf1322;
      color: #fff;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 12px;
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

.sheet-dual-container {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.sheet-column {
  flex: 1;
  overflow-x: auto;
}

.sheet-divider {
  width: 1px;
  background-color: #dcdfe6;
  align-self: stretch;
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

  .sub-hint {
    font-size: 11px;
    color: #8c8c8c;
    margin-left: 4px;
  }

  .is-bold {
    font-weight: 600;
  }

  .row-header td {
    background: #fafafa;
    font-weight: 600;
    color: #262626;
  }

  .row-total td {
    background: #fbfbfb;
    font-weight: 700;
    border-top: 1px solid #d9d9d9;
    border-bottom: 2px solid #595959;
    color: #1f2229;
  }
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>

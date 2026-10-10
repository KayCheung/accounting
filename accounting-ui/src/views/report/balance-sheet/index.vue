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
            style="width: 160px;"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item label="对比基准日期">
          <el-date-picker
            v-model="queryForm.compareDate"
            type="date"
            placeholder="对比时点(默认年初)"
            value-format="YYYY-MM-DD"
            clearable
            style="width: 170px;"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item label="快捷对比">
          <el-button-group>
            <el-button size="small" @click="setComparePreset('year-start')">年初数</el-button>
            <el-button size="small" @click="setComparePreset('month-end')">上月月末</el-button>
            <el-button size="small" @click="setComparePreset('last-year')">上年同期</el-button>
          </el-button-group>
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
                <th style="width: 22.5%; text-align: right;">
                  {{ reportData?.compareDate ? `对比余额 (${reportData.compareDate})` : '年初余额' }}
                </th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="item in reportData?.assetItems || []"
                :key="'asset_' + item.rowNo"
              >
                <tr :class="getRowClass(item)">
                  <td class="col-name" :style="{ paddingLeft: item.itemLevel === 2 ? '24px' : '8px' }">
                    <span
                      v-if="item.detailSubjects && item.detailSubjects.length > 0"
                      class="expand-icon"
                      title="展开/折叠明细科目"
                      @click="toggleExpand('asset', item.rowNo)"
                    >
                      <el-icon :class="{ 'is-rotated': isExpanded('asset', item.rowNo) }">
                        <ArrowRight />
                      </el-icon>
                    </span>
                    <span :class="{ 'is-bold': item.itemLevel !== 2 }">{{ item.itemName }}</span>
                    <span v-if="item.subjectCodes" class="sub-hint">({{ item.subjectCodes }})</span>
                    <el-tag
                      v-if="item.detailSubjects && item.detailSubjects.length > 1"
                      size="small"
                      type="info"
                      effect="light"
                      class="subject-count-tag"
                      @click="toggleExpand('asset', item.rowNo)"
                    >
                      {{ item.detailSubjects.length }}科目
                    </el-tag>
                  </td>
                  <td class="col-row-no">{{ item.rowNo }}</td>
                  <td class="col-amount mono">
                    {{ item.itemLevel === 1 ? '' : formatMoney(item.endAmount) }}
                  </td>
                  <td class="col-amount mono">
                    {{ item.itemLevel === 1 ? '' : formatMoney(item.beginAmount) }}
                  </td>
                </tr>

                <!-- 明细展开行 -->
                <tr
                  v-if="isExpanded('asset', item.rowNo) && item.detailSubjects && item.detailSubjects.length > 0"
                  class="row-detail-expanded"
                >
                  <td colspan="4" class="expanded-cell">
                    <div class="sub-detail-wrapper">
                      <div class="sub-detail-header">包含底层科目明细 ({{ item.itemName }})：</div>
                      <table class="inner-detail-table">
                        <thead>
                          <tr>
                            <th style="width: 25%;">科目编码</th>
                            <th style="width: 35%;">科目名称</th>
                            <th style="width: 20%; text-align: right;">期末余额</th>
                            <th style="width: 20%; text-align: right;">对比/年初余额</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="sub in item.detailSubjects" :key="sub.subjectCode">
                            <td class="mono sub-code">{{ sub.subjectCode }}</td>
                            <td>{{ sub.subjectName }}</td>
                            <td class="mono" style="text-align: right;">{{ formatMoney(sub.endAmount) }}</td>
                            <td class="mono" style="text-align: right;">{{ formatMoney(sub.beginAmount) }}</td>
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
                <th style="width: 22.5%; text-align: right;">
                  {{ reportData?.compareDate ? `对比余额 (${reportData.compareDate})` : '年初余额' }}
                </th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="item in reportData?.liabilityAndEquityItems || []"
                :key="'liab_' + item.rowNo"
              >
                <tr :class="getRowClass(item)">
                  <td class="col-name" :style="{ paddingLeft: item.itemLevel === 2 ? '24px' : '8px' }">
                    <span
                      v-if="item.detailSubjects && item.detailSubjects.length > 0"
                      class="expand-icon"
                      title="展开/折叠明细科目"
                      @click="toggleExpand('liability', item.rowNo)"
                    >
                      <el-icon :class="{ 'is-rotated': isExpanded('liability', item.rowNo) }">
                        <ArrowRight />
                      </el-icon>
                    </span>
                    <span :class="{ 'is-bold': item.itemLevel !== 2 }">{{ item.itemName }}</span>
                    <span v-if="item.subjectCodes" class="sub-hint">({{ item.subjectCodes }})</span>
                    <el-tag
                      v-if="item.detailSubjects && item.detailSubjects.length > 1"
                      size="small"
                      type="info"
                      effect="light"
                      class="subject-count-tag"
                      @click="toggleExpand('liability', item.rowNo)"
                    >
                      {{ item.detailSubjects.length }}科目
                    </el-tag>
                  </td>
                  <td class="col-row-no">{{ item.rowNo }}</td>
                  <td class="col-amount mono">
                    {{ item.itemLevel === 1 ? '' : formatMoney(item.endAmount) }}
                  </td>
                  <td class="col-amount mono">
                    {{ item.itemLevel === 1 ? '' : formatMoney(item.beginAmount) }}
                  </td>
                </tr>

                <!-- 明细展开行 -->
                <tr
                  v-if="isExpanded('liability', item.rowNo) && item.detailSubjects && item.detailSubjects.length > 0"
                  class="row-detail-expanded"
                >
                  <td colspan="4" class="expanded-cell">
                    <div class="sub-detail-wrapper">
                      <div class="sub-detail-header">包含底层科目明细 ({{ item.itemName }})：</div>
                      <table class="inner-detail-table">
                        <thead>
                          <tr>
                            <th style="width: 25%;">科目编码</th>
                            <th style="width: 35%;">科目名称</th>
                            <th style="width: 20%; text-align: right;">期末余额</th>
                            <th style="width: 20%; text-align: right;">对比/年初余额</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="sub in item.detailSubjects" :key="sub.subjectCode">
                            <td class="mono sub-code">{{ sub.subjectCode }}</td>
                            <td>{{ sub.subjectName }}</td>
                            <td class="mono" style="text-align: right;">{{ formatMoney(sub.endAmount) }}</td>
                            <td class="mono" style="text-align: right;">{{ formatMoney(sub.beginAmount) }}</td>
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
  WarningFilled,
  ArrowRight
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
  compareDate: '',
  compareYearStart: true
})

const loading = ref(false)
const reportData = ref<BalanceSheetResponse | null>(null)

// 展开折叠状态管理
const expandedAssetRows = ref<Set<number>>(new Set())
const expandedLiabilityRows = ref<Set<number>>(new Set())

function isExpanded(side: 'asset' | 'liability', rowNo: number): boolean {
  return side === 'asset' ? expandedAssetRows.value.has(rowNo) : expandedLiabilityRows.value.has(rowNo)
}

function toggleExpand(side: 'asset' | 'liability', rowNo: number) {
  const set = side === 'asset' ? expandedAssetRows.value : expandedLiabilityRows.value
  if (set.has(rowNo)) {
    set.delete(rowNo)
  } else {
    set.add(rowNo)
  }
}

function setComparePreset(type: 'year-start' | 'month-end' | 'last-year') {
  if (!queryForm.accountingDate) return
  const parts = queryForm.accountingDate.split('-').map(Number)
  const y = parts[0]
  const m = parts[1]
  const d = parts[2]

  if (type === 'year-start') {
    queryForm.compareDate = `${y}-01-01`
  } else if (type === 'month-end') {
    const prevMonthEnd = new Date(y, m - 1, 0)
    const py = prevMonthEnd.getFullYear()
    const pm = String(prevMonthEnd.getMonth() + 1).padStart(2, '0')
    const pd = String(prevMonthEnd.getDate()).padStart(2, '0')
    queryForm.compareDate = `${py}-${pm}-${pd}`
  } else if (type === 'last-year') {
    const ly = y - 1
    const lm = String(m).padStart(2, '0')
    const ld = String(d).padStart(2, '0')
    queryForm.compareDate = `${ly}-${lm}-${ld}`
  }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBalanceSheet({
      accountingDate: queryForm.accountingDate,
      compareDate: queryForm.compareDate || undefined,
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

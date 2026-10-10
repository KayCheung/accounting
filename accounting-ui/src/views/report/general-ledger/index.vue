<!-- 文件路径：src/views/report/general-ledger/index.vue -->
<template>
  <div class="general-ledger-page">
    <!-- 顶部检索与操作栏 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="会计期间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 250px;"
            @change="handleDateRangeChange"
          />
        </el-form-item>

        <el-form-item label="科目范围">
          <div class="range-inputs">
            <el-input
              v-model="queryForm.startSubjectCode"
              placeholder="起始编码"
              style="width: 110px;"
              clearable
            />
            <span class="range-sep">至</span>
            <el-input
              v-model="queryForm.endSubjectCode"
              placeholder="终止编码"
              style="width: 110px;"
              clearable
            />
          </div>
        </el-form-item>

        <el-form-item label="科目级次">
          <el-select v-model="queryForm.subjectLevel" placeholder="所有级次" clearable style="width: 120px;">
            <el-option label="所有级次" :value="undefined" />
            <el-option
              v-for="lvl in subjectLevels"
              :key="lvl"
              :label="`${lvl}级科目`"
              :value="lvl"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="无发生科目">
          <el-switch
            v-model="queryForm.showZeroBalance"
            active-text="显示"
            inactive-text="隐藏"
          />
        </el-form-item>

        <el-form-item label="科目搜索">
          <el-input
            v-model="queryForm.keyword"
            placeholder="科目编码或名称..."
            clearable
            style="width: 160px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
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

    <!-- 工具与平衡校验栏 -->
    <div v-if="reportData" class="toolbar-card">
      <div class="toolbar-left">
        <span>共查询到 <b class="highlight-count">{{ reportData.totalSubjectCount }}</b> 个科目</span>
        <span class="divider">|</span>
        <span>期间：{{ reportData.startDate }} ~ {{ reportData.endDate }}</span>
        <span class="divider">|</span>
        <span>账套：主账套（人民币 CNY）</span>
      </div>
      <div class="toolbar-right">
        <el-tag :type="reportData.isBalanced ? 'success' : 'danger'" effect="dark">
          {{ reportData.isBalanced ? '✓ 发生额借贷试算平衡' : '✕ 警告：借贷发生额不平衡' }}
        </el-tag>
      </div>
    </div>

    <!-- 总账三栏式汇总表格卡片 -->
    <div class="fin-card table-card">
      <el-table
        v-loading="loading"
        :data="reportData?.items || []"
        border
        stripe
        style="width: 100%"
        class="fin-table"
        empty-text="暂无总账数据"
      >
        <el-table-column prop="subjectCode" label="科目编码" width="130" sortable>
          <template #default="{ row }">
            <span class="mono-code">{{ row.subjectCode }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="subjectName" label="科目名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ paddingLeft: (row.subjectLevel - 1) * 16 + 'px' }">
              {{ row.subjectName }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="subjectLevel" label="级次" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.subjectLevel }}级</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="balanceDirectionDesc" label="方向" width="70" align="center">
          <template #default="{ row }">
            <span :class="row.balanceDirectionDesc === '借' ? 'dir-debit' : 'dir-credit'">
              {{ row.balanceDirectionDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="beginBalance" label="期初余额" width="150" align="right">
          <template #default="{ row }">
            <span class="mono">{{ formatMoney(row.beginBalance) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="本期发生额" align="center">
          <el-table-column prop="debitAmount" label="借方发生额" width="150" align="right">
            <template #default="{ row }">
              <span class="mono text-debit">{{ formatMoney(row.debitAmount) }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="creditAmount" label="贷方发生额" width="150" align="right">
            <template #default="{ row }">
              <span class="mono text-credit">{{ formatMoney(row.creditAmount) }}</span>
            </template>
          </el-table-column>
        </el-table-column>

        <el-table-column prop="endBalance" label="期末余额" width="150" align="right">
          <template #default="{ row }">
            <span class="mono is-bold">{{ formatMoney(row.endBalance) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部合计栏 -->
      <div v-if="reportData" class="summary-footer">
        <div class="summary-row">
          <div class="sum-label">合计 (汇总试算)：</div>
          <div class="sum-item">期初借方: <b class="mono">{{ formatMoney(reportData.totalBeginDebit) }}</b></div>
          <div class="sum-item">期初贷方: <b class="mono">{{ formatMoney(reportData.totalBeginCredit) }}</b></div>
          <div class="sum-item highlight">本期借方: <b class="mono text-debit">{{ formatMoney(reportData.totalPeriodDebit) }}</b></div>
          <div class="sum-item highlight">本期贷方: <b class="mono text-credit">{{ formatMoney(reportData.totalPeriodCredit) }}</b></div>
          <div class="sum-item">期末借方: <b class="mono">{{ formatMoney(reportData.totalEndDebit) }}</b></div>
          <div class="sum-item">期末贷方: <b class="mono">{{ formatMoney(reportData.totalEndCredit) }}</b></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  Search,
  RefreshRight,
  Download,
  Printer
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getGeneralLedger,
  getSubjectLevels,
  type GeneralLedgerResponse
} from '@/api/report'

const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1
const startStr = `${year}-01-01`
const endStr = `${year}-${String(month).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

const dateRange = ref<[string, string]>([startStr, endStr])
const subjectLevels = ref<number[]>([1, 2, 3])

const queryForm = reactive({
  startDate: startStr,
  endDate: endStr,
  startSubjectCode: '',
  endSubjectCode: '',
  subjectLevel: undefined as number | undefined,
  showZeroBalance: false,
  keyword: ''
})

const loading = ref(false)
const reportData = ref<GeneralLedgerResponse | null>(null)

function handleDateRangeChange(val: [string, string]) {
  if (val && val.length === 2) {
    queryForm.startDate = val[0]
    queryForm.endDate = val[1]
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getGeneralLedger({
      startDate: queryForm.startDate,
      endDate: queryForm.endDate,
      startSubjectCode: queryForm.startSubjectCode || undefined,
      endSubjectCode: queryForm.endSubjectCode || undefined,
      subjectLevel: queryForm.subjectLevel,
      showZeroBalance: queryForm.showZeroBalance,
      keyword: queryForm.keyword ? queryForm.keyword.trim() : undefined
    })
    reportData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载科目总账失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

function handleReset() {
  dateRange.value = [startStr, endStr]
  queryForm.startDate = startStr
  queryForm.endDate = endStr
  queryForm.startSubjectCode = ''
  queryForm.endSubjectCode = ''
  queryForm.subjectLevel = undefined
  queryForm.showZeroBalance = false
  queryForm.keyword = ''
  fetchData()
}

function handleExport() {
  ElMessage.info('正在导出科目总账 Excel 文件...')
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

onMounted(async () => {
  try {
    const levels = await getSubjectLevels()
    if (levels && levels.length > 0) {
      subjectLevels.value = levels
    }
  } catch {
    // 降级容错保留默认 [1, 2, 3]
  }
  fetchData()
})
</script>

<style scoped>
.general-ledger-page {
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

.range-inputs {
  display: flex;
  align-items: center;
  gap: 4px;

  .range-sep {
    color: #8c8c8c;
    font-size: 12px;
  }
}

.toolbar-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #595959;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .highlight-count {
      color: #1890ff;
      font-size: 15px;
    }

    .divider {
      color: #d9d9d9;
    }
  }
}

.mono-code {
  font-family: monospace;
  font-weight: 600;
  color: #1890ff;
}

.dir-debit {
  color: #cf1322;
  font-weight: 600;
}

.dir-credit {
  color: #389e0d;
  font-weight: 600;
}

.text-debit {
  color: #cf1322;
}

.text-credit {
  color: #389e0d;
}

.is-bold {
  font-weight: 700;
  color: #1f2229;
}

.summary-footer {
  margin-top: 16px;
  padding: 14px 16px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;

  .summary-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 20px;
    font-size: 13px;

    .sum-label {
      font-weight: 700;
      color: #262626;
    }

    .sum-item {
      color: #595959;

      &.highlight {
        font-weight: 600;
      }
    }
  }
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>

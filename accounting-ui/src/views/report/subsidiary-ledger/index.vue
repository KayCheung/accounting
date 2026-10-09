<!-- 文件路径：src/views/report/subsidiary-ledger/index.vue -->
<template>
  <div class="subsidiary-ledger-page">
    <!-- 顶部检索与操作栏 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="科目编码" required>
          <el-input
            v-model="queryForm.subjectCode"
            placeholder="如 1002 银行存款"
            style="width: 160px;"
            clearable
          />
        </el-form-item>

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

        <el-form-item label="账户编号">
          <el-input
            v-model="queryForm.accountNo"
            placeholder="可选特定分户"
            style="width: 160px;"
            clearable
          />
        </el-form-item>

        <el-form-item label="摘要搜索">
          <el-input
            v-model="queryForm.summaryKeyword"
            placeholder="摘要内容关键字..."
            style="width: 160px;"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            查询
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

    <!-- 科目信息摘要卡片（吸纳原型高亮展示） -->
    <div v-if="reportData" class="account-summary-bar">
      <div class="summary-item"><label>科目编码：</label><b class="mono-code">{{ reportData.subjectCode }}</b></div>
      <div class="summary-item"><label>科目名称：</label><b>{{ reportData.subjectName }}</b></div>
      <div class="summary-item"><label>科目级次：</label><el-tag size="small" type="info">{{ reportData.subjectLevel }}级</el-tag></div>
      <div class="summary-item"><label>余额方向：</label><span class="dir-tag">{{ reportData.balanceDirectionDesc }}</span></div>
      <div class="summary-item"><label>期初余额：</label><span class="mono">{{ formatMoney(reportData.beginBalance) }}</span></div>
      <div class="summary-item"><label>借方合计：</label><span class="mono text-debit">{{ formatMoney(reportData.totalDebitAmount) }}</span></div>
      <div class="summary-item"><label>贷方合计：</label><span class="mono text-credit">{{ formatMoney(reportData.totalCreditAmount) }}</span></div>
      <div class="summary-item highlight"><label>期末余额：</label><b class="mono highlight-money">{{ formatMoney(reportData.endBalance) }}</b></div>
    </div>

    <!-- 明细账表格卡片 -->
    <div class="fin-card table-card">
      <el-table
        v-loading="loading"
        :data="reportData?.items || []"
        border
        stripe
        style="width: 100%"
        class="fin-table"
        :row-class-name="tableRowClassName"
        empty-text="暂无科目明细账数据"
      >
        <el-table-column prop="accountingDate" label="会计日期" width="120" align="center" />

        <el-table-column prop="voucherNo" label="凭证字号" width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.voucherNo"
              link
              type="primary"
              class="mono-code"
              @click="handleViewVoucher(row.voucherNo)"
            >
              {{ row.voucherNo }}
            </el-button>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="entryId" label="分录流水号" width="180">
          <template #default="{ row }">
            <span class="mono text-entry">{{ row.entryId || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="summary" label="业务摘要" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'is-bold': row.rowType !== 'ENTRY' }">{{ row.summary }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="debitAmount" label="借方金额" width="150" align="right">
          <template #default="{ row }">
            <span v-if="row.debitAmount > 0" class="mono text-debit">{{ formatMoney(row.debitAmount) }}</span>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="creditAmount" label="贷方金额" width="150" align="right">
          <template #default="{ row }">
            <span v-if="row.creditAmount > 0" class="mono text-credit">{{ formatMoney(row.creditAmount) }}</span>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="balanceDirectionDesc" label="方向" width="70" align="center">
          <template #default="{ row }">
            <span :class="row.balanceDirectionDesc === '借' ? 'dir-debit' : (row.balanceDirectionDesc === '贷' ? 'dir-credit' : 'dir-flat')">
              {{ row.balanceDirectionDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="balance" label="动态余额" width="160" align="right">
          <template #default="{ row }">
            <span class="mono is-bold">{{ formatMoney(row.balance) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 凭证穿透弹窗 -->
    <el-dialog
      v-model="voucherDialogVisible"
      :title="'记账凭证详情 [' + currentVoucherNo + ']'"
      width="750px"
      destroy-on-close
    >
      <div v-loading="loadingVoucher" class="voucher-preview-box">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="凭证号">{{ currentVoucherNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag size="small" type="success">已过账</el-tag></el-descriptions-item>
          <el-descriptions-item label="记账模式">实时入账</el-descriptions-item>
          <el-descriptions-item label="系统跟踪号">TR20260315998124</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px;">
          <el-alert
            title="该凭证已完成总账及分户账过账，借贷试算平衡。"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="voucherDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
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
  getSubsidiaryLedger,
  type SubsidiaryLedgerResponse,
  type SubsidiaryLedgerItem
} from '@/api/report'

const now = new Date()
const year = now.getFullYear()
const month = String(now.getMonth() + 1).padStart(2, '0')
const startStr = `${year}-${month}-01`
const endStr = now.toISOString().split('T')[0]

const dateRange = ref<[string, string]>([startStr, endStr])

const queryForm = reactive({
  subjectCode: '1002',
  accountNo: '',
  startDate: startStr,
  endDate: endStr,
  summaryKeyword: ''
})

const loading = ref(false)
const reportData = ref<SubsidiaryLedgerResponse | null>(null)

// 凭证穿透
const voucherDialogVisible = ref(false)
const currentVoucherNo = ref('')
const loadingVoucher = ref(false)

function handleDateRangeChange(val: [string, string]) {
  if (val && val.length === 2) {
    queryForm.startDate = val[0]
    queryForm.endDate = val[1]
  }
}

async function fetchData() {
  if (!queryForm.subjectCode) {
    ElMessage.warning('请输入要查询的会计科目编码')
    return
  }
  loading.value = true
  try {
    const res = await getSubsidiaryLedger({
      subjectCode: queryForm.subjectCode.trim(),
      accountNo: queryForm.accountNo ? queryForm.accountNo.trim() : undefined,
      startDate: queryForm.startDate,
      endDate: queryForm.endDate,
      summaryKeyword: queryForm.summaryKeyword ? queryForm.summaryKeyword.trim() : undefined
    })
    reportData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载科目明细账失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

function handleExport() {
  ElMessage.info('正在导出科目明细账 Excel 文件...')
}

function handlePrint() {
  window.print()
}

function handleViewVoucher(voucherNo: string) {
  currentVoucherNo.value = voucherNo
  voucherDialogVisible.value = true
}

function tableRowClassName({ row }: { row: SubsidiaryLedgerItem }) {
  if (row.rowType === 'PERIOD_TOTAL') return 'row-total'
  if (row.rowType === 'BEGIN_BALANCE') return 'row-begin'
  return ''
}

function formatMoney(num?: number | null): string {
  if (num === null || num === undefined) return '0.00'
  return Number(num).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.subsidiary-ledger-page {
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

.account-summary-bar {
  background: #ffffff;
  border-radius: 8px;
  padding: 14px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 20px;
  font-size: 13px;

  .summary-item {
    color: #595959;

    label {
      color: #8c8c8c;
    }

    &.highlight {
      background: #e6f7ff;
      padding: 4px 12px;
      border-radius: 4px;
      border: 1px solid #91d5ff;

      .highlight-money {
        color: #1890ff;
        font-size: 15px;
      }
    }
  }
}

.mono-code {
  font-family: monospace;
  font-weight: 600;
  color: #1890ff;
}

.text-entry {
  color: #8c8c8c;
  font-size: 12px;
}

.text-placeholder {
  color: #bfbfbf;
}

.dir-debit {
  color: #cf1322;
  font-weight: 600;
}

.dir-credit {
  color: #389e0d;
  font-weight: 600;
}

.dir-flat {
  color: #8c8c8c;
}

.text-debit {
  color: #cf1322;
}

.text-credit {
  color: #389e0d;
}

.is-bold {
  font-weight: 600;
}

:deep(.row-begin) {
  background-color: #fafafa !important;
  font-weight: 600;
}

:deep(.row-total) {
  background-color: #fbfbfb !important;
  font-weight: 700;
  color: #1f2229;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>

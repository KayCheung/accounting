<!-- 文件路径：src/views/report/auxiliary-ledger/index.vue -->
<template>
  <div class="auxiliary-ledger-page">
    <div class="aux-layout">
      <!-- 左侧动态核算项目导航树/列表 -->
      <aside class="aux-sidebar fin-card">
        <div class="sidebar-head">
          <span class="sidebar-title">
            <el-icon class="head-icon"><CollectionTag /></el-icon>
            {{ currentAuxTypeName }}列表
          </span>
          <el-tag size="small" type="primary" effect="plain">{{ filteredAuxItems.length }} 项</el-tag>
        </div>

        <div class="sidebar-search">
          <el-input
            v-model="itemSearchKeyword"
            placeholder="搜索核算项名称/编码..."
            clearable
            :prefix-icon="Search"
            size="small"
          />
        </div>

        <el-scrollbar class="item-scroll-list">
          <!-- 全核算项汇总项 -->
          <div
            class="item-node root-node"
            :class="{ active: selectedAuxCode === 'ALL' }"
            @click="handleSelectAuxItem('ALL')"
          >
            <div class="node-content">
              <el-icon class="node-icon"><DataBoard /></el-icon>
              <span class="node-name">全维度汇总</span>
            </div>
            <span class="node-badge">{{ auxItems.length }} 项</span>
          </div>

          <!-- 具体核算项列表 -->
          <div
            v-for="item in filteredAuxItems"
            :key="item.auxCode"
            class="item-node"
            :class="{ active: selectedAuxCode === item.auxCode }"
            @click="handleSelectAuxItem(item.auxCode)"
          >
            <div class="node-content">
              <span class="status-dot" :class="{ active: selectedAuxCode === item.auxCode }"></span>
              <div class="node-text">
                <span class="node-name" :title="item.auxName">{{ item.auxName }}</span>
                <span class="node-code mono">{{ item.auxCode }}</span>
              </div>
            </div>
            <span class="node-amt mono">{{ formatMoney(item.totalAmount) }}</span>
          </div>

          <el-empty
            v-if="filteredAuxItems.length === 0"
            description="无匹配核算项"
            :image-size="60"
            style="padding: 20px 0;"
          />
        </el-scrollbar>
      </aside>

      <!-- 右侧报表分析主区域 -->
      <main class="aux-main">
        <!-- 顶部综合筛选区 -->
        <div class="fin-card filter-card">
          <el-form :model="queryForm" inline class="search-form">
            <el-form-item label="核算类别">
              <el-select
                v-model="queryForm.auxType"
                placeholder="选择类别"
                style="width: 140px;"
                @change="handleAuxTypeChange"
              >
                <el-option
                  v-for="type in auxTypeList"
                  :key="type.dictCode"
                  :label="type.dictName"
                  :value="type.dictCode"
                />
              </el-select>
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
                style="width: 240px;"
                @change="handleDateRangeChange"
              />
            </el-form-item>

            <el-form-item label="科目类别">
              <el-select
                v-model="queryForm.subjectCategory"
                placeholder="全部科目"
                clearable
                style="width: 130px;"
              >
                <el-option label="全部科目" :value="undefined" />
                <el-option label="资产类" :value="1" />
                <el-option label="负债类" :value="2" />
                <el-option label="权益类" :value="3" />
                <el-option label="成本类" :value="5" />
                <el-option label="损益类" :value="6" />
              </el-select>
            </el-form-item>

            <el-form-item label="关键字">
              <el-input
                v-model="queryForm.keyword"
                placeholder="科目、凭证号或摘要..."
                style="width: 170px;"
                clearable
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
                查询
              </el-button>
              <el-button :icon="Refresh" @click="handleReset">
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

        <!-- 财务 KPI 统计卡片 -->
        <div class="kpi-grid">
          <div class="kpi-card blue">
            <div class="kpi-label">本期核算发生总额</div>
            <div class="kpi-value mono text-primary">
              {{ formatMoney(summaryData?.totalAmount) }}
              <span class="kpi-unit">元</span>
            </div>
            <div class="kpi-sub">
              <span>当前核算项：</span>
              <b>{{ currentSelectedItemName }}</b>
            </div>
          </div>

          <div class="kpi-card green">
            <div class="kpi-label">借方发生额</div>
            <div class="kpi-value mono text-debit">
              {{ formatMoney(summaryData?.debitAmount) }}
              <span class="kpi-unit">元</span>
            </div>
            <div class="kpi-sub">
              <span>方向：借方累计</span>
            </div>
          </div>

          <div class="kpi-card orange">
            <div class="kpi-label">贷方发生额</div>
            <div class="kpi-value mono text-credit">
              {{ formatMoney(summaryData?.creditAmount) }}
              <span class="kpi-unit">元</span>
            </div>
            <div class="kpi-sub">
              <span>方向：贷方累计</span>
            </div>
          </div>

          <div class="kpi-card purple">
            <div class="kpi-label">涉及会计科目</div>
            <div class="kpi-value mono text-purple">
              {{ summaryData?.subjectCount || 0 }}
              <span class="kpi-unit">个</span>
            </div>
            <div class="kpi-sub">
              <span>覆盖账类及明细科目</span>
            </div>
          </div>

          <div class="kpi-card red">
            <div class="kpi-label">相关凭证记录</div>
            <div class="kpi-value mono text-dark">
              {{ summaryData?.voucherCount || 0 }}
              <span class="kpi-unit">笔</span>
            </div>
            <div class="kpi-sub">
              <span>已入账法定理算凭证</span>
            </div>
          </div>
        </div>

        <!-- Top 5 核算项分布占比进度条（当选全维度汇总时呈现） -->
        <div v-if="selectedAuxCode === 'ALL' && summaryData?.topItems && summaryData.topItems.length > 0" class="fin-card proportion-card">
          <div class="section-header">
            <span class="title">各{{ currentAuxTypeName }}发生额占比分布</span>
            <span class="hint">总计 {{ formatMoney(summaryData.totalAmount) }} 元</span>
          </div>
          <div class="proportion-bar-grid">
            <div
              v-for="item in summaryData.topItems"
              :key="item.auxCode"
              class="proportion-row"
            >
              <div class="prop-label" :title="item.auxName">{{ item.auxName }}</div>
              <div class="prop-progress">
                <el-progress
                  :percentage="Number(item.percentage) || 0"
                  :stroke-width="12"
                  :color="getProgressColor(item.percentage)"
                />
              </div>
              <div class="prop-amount mono">{{ formatMoney(item.amount) }} 元</div>
            </div>
          </div>
        </div>

        <!-- 核心工作台 Tab 切换 -->
        <div class="fin-card table-section">
          <el-tabs v-model="activeTab" class="aux-tabs" @tab-change="handleTabChange">
            <!-- Tab 1: 各核算项汇总对比（交叉矩阵透视表） -->
            <el-tab-pane label="各核算项汇总对比" name="matrix">
              <div class="tab-pane-content">
                <div class="table-toolbar">
                  <span class="tb-title">
                    {{ currentAuxTypeName }}交叉发生额对比表
                    <el-tag size="small" type="info" class="period-tag">{{ currentPeriodDesc }}</el-tag>
                  </span>
                  <span class="tb-hint">金额单位：元</span>
                </div>

                <el-table
                  v-loading="loadingMatrix"
                  :data="matrixData?.rows || []"
                  border
                  stripe
                  class="fin-table matrix-table"
                  empty-text="暂无交叉对比数据"
                  max-height="560"
                  show-summary
                  :summary-method="getMatrixSummary"
                >
                  <el-table-column prop="subjectCode" label="科目编码" width="110" fixed="left">
                    <template #default="{ row }">
                      <span class="mono-code">{{ row.subjectCode }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="subjectName" label="科目名称" min-width="150" fixed="left">
                    <template #default="{ row }">
                      <span class="subject-name-cell">{{ row.subjectName }}</span>
                      <el-tag v-if="row.subjectCategoryDesc" size="small" type="info" effect="plain" class="category-tag">
                        {{ row.subjectCategoryDesc }}
                      </el-tag>
                    </template>
                  </el-table-column>

                  <!-- 动态核算项目列 -->
                  <el-table-column
                    v-for="col in matrixData?.columns || []"
                    :key="col.auxCode"
                    :prop="'amounts.' + col.auxCode"
                    :label="col.auxName"
                    min-width="125"
                    align="right"
                  >
                    <template #default="{ row }">
                      <span
                        v-if="row.amounts && row.amounts[col.auxCode]"
                        class="mono text-amount"
                      >
                        {{ formatMoney(row.amounts[col.auxCode]) }}
                      </span>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <!-- 固定合计与占比列 -->
                  <el-table-column prop="totalAmount" label="科目合计" width="140" align="right" fixed="right">
                    <template #default="{ row }">
                      <b class="mono highlight-money">{{ formatMoney(row.totalAmount) }}</b>
                    </template>
                  </el-table-column>

                  <el-table-column prop="percentage" label="占比" width="90" align="right" fixed="right">
                    <template #default="{ row }">
                      <span class="mono pct-text">{{ row.percentage }}%</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- Tab 2: 科目分布明细 -->
            <el-tab-pane label="科目分布明细" name="subjects">
              <div class="tab-pane-content">
                <div class="table-toolbar">
                  <span class="tb-title">
                    {{ currentSelectedItemName }} - 科目发生额明细分布
                  </span>
                  <span class="tb-hint">金额单位：元</span>
                </div>

                <el-table
                  v-loading="loadingSubjects"
                  :data="subjectBreakdownList"
                  border
                  stripe
                  class="fin-table"
                  empty-text="暂无科目分布明细"
                  max-height="560"
                >
                  <el-table-column prop="subjectCode" label="科目编码" width="120">
                    <template #default="{ row }">
                      <span class="mono-code">{{ row.subjectCode }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="subjectName" label="会计科目名称" min-width="180">
                    <template #default="{ row }">
                      <span class="is-bold">{{ row.subjectName }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="subjectCategoryDesc" label="账类" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="info">{{ row.subjectCategoryDesc || '损益类' }}</el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column prop="debitAmount" label="借方发生额" width="150" align="right">
                    <template #default="{ row }">
                      <span v-if="row.debitAmount > 0" class="mono text-debit">{{ formatMoney(row.debitAmount) }}</span>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="creditAmount" label="贷方发生额" width="150" align="right">
                    <template #default="{ row }">
                      <span v-if="row.creditAmount > 0" class="mono text-credit">{{ formatMoney(row.creditAmount) }}</span>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="totalAmount" label="发生额合计" width="150" align="right">
                    <template #default="{ row }">
                      <b class="mono text-primary">{{ formatMoney(row.totalAmount) }}</b>
                    </template>
                  </el-table-column>

                  <el-table-column prop="entryCount" label="记录笔数" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" effect="plain">{{ row.entryCount }} 笔</el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column prop="percentage" label="占比结构" min-width="160">
                    <template #default="{ row }">
                      <div class="subject-pct-wrap">
                        <el-progress
                          :percentage="Number(row.percentage) || 0"
                          :stroke-width="8"
                          :show-text="false"
                          color="#1890ff"
                          style="flex: 1;"
                        />
                        <span class="mono pct-val">{{ row.percentage }}%</span>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- Tab 3: 相关凭证记录流水 -->
            <el-tab-pane label="辅助凭证记录" name="entries">
              <div class="tab-pane-content">
                <div class="table-toolbar">
                  <span class="tb-title">
                    已入账辅助核算凭证记录流水
                    <span class="hint">（共 {{ entryTotal }} 笔）</span>
                  </span>
                  <span class="tb-hint">点击凭证字号可穿透查看凭证详情</span>
                </div>

                <el-table
                  v-loading="loadingEntries"
                  :data="entryList"
                  border
                  stripe
                  class="fin-table"
                  empty-text="暂无辅助核算凭证记录"
                >
                  <el-table-column prop="accountingDate" label="会计日期" width="115" align="center" />

                  <el-table-column prop="voucherWord" label="凭证字号" width="170">
                    <template #default="{ row }">
                      <el-button
                        link
                        type="primary"
                        class="mono-code"
                        @click="handleViewVoucher(row.voucherNo)"
                      >
                        {{ row.voucherWord || row.voucherNo }}
                      </el-button>
                    </template>
                  </el-table-column>

                  <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />

                  <el-table-column prop="auxName" :label="'归属' + currentAuxTypeName" width="140">
                    <template #default="{ row }">
                      <el-tag size="small" type="primary" effect="plain" class="tag-dept">
                        {{ row.auxName }}
                      </el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column prop="subjectName" label="会计科目" min-width="170">
                    <template #default="{ row }">
                      <span class="mono-code">{{ row.subjectCode }}</span>
                      <span style="margin-left: 6px;">{{ row.subjectName }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="debitCreditDesc" label="方向" width="70" align="center">
                    <template #default="{ row }">
                      <span :class="row.debitCredit === 1 ? 'dir-debit' : 'dir-credit'">
                        {{ row.debitCreditDesc }}
                      </span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="debitAmount" label="借方金额" width="140" align="right">
                    <template #default="{ row }">
                      <span v-if="row.debitAmount > 0" class="mono text-debit">{{ formatMoney(row.debitAmount) }}</span>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="creditAmount" label="贷方金额" width="140" align="right">
                    <template #default="{ row }">
                      <span v-if="row.creditAmount > 0" class="mono text-credit">{{ formatMoney(row.creditAmount) }}</span>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="操作" width="90" align="center" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" size="small" @click="handleViewVoucher(row.voucherNo)">
                        查看
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>

                <!-- 分页栏 -->
                <div class="pagination-bar">
                  <el-pagination
                    v-model:current-page="pageNo"
                    v-model:page-size="pageSize"
                    :total="entryTotal"
                    :page-sizes="[10, 20, 50, 100]"
                    layout="total, sizes, prev, pager, next, jumper"
                    @size-change="fetchEntries"
                    @current-change="fetchEntries"
                  />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </main>
    </div>

    <!-- 凭证穿透全景抽屉 -->
    <el-drawer
      v-model="voucherDrawerVisible"
      :title="`记账凭证全景档案 · ${voucherDetail?.voucherNo || currentVoucherNo}`"
      size="760px"
      destroy-on-close
    >
      <div v-loading="loadingVoucherDetail" class="voucher-drawer-content">
        <template v-if="voucherDetail">
          <!-- 凭证主表概要 -->
          <el-descriptions :column="2" border size="small" class="detail-descriptions">
            <el-descriptions-item label="凭证字号">
              <b>{{ voucherDetail.voucherWord || voucherDetail.voucherNo }}</b>
            </el-descriptions-item>
            <el-descriptions-item label="会计日期">
              {{ voucherDetail.accountingDate }}
            </el-descriptions-item>
            <el-descriptions-item label="凭证类型">
              {{ voucherDetail.voucherTypeName || voucherDetail.voucherType }}
            </el-descriptions-item>
            <el-descriptions-item label="凭证状态">
              <el-tag size="small" type="success">已过账</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="入账模式">
              {{ voucherDetail.postingTypeDesc || '机制凭证' }}
            </el-descriptions-item>
            <el-descriptions-item label="制单/记账人">
              {{ voucherDetail.bookkeeperName || '系统自动入账' }}
            </el-descriptions-item>
            <el-descriptions-item label="凭证摘要" :span="2">
              {{ voucherDetail.summary || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="合计大写" :span="2">
              <span class="text-primary">{{ voucherDetail.totalAmountInWords || '-' }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 凭证借贷分录表 -->
          <div class="drawer-sub-section">
            <div class="sub-title">记账凭证借贷分录明细</div>
            <el-table :data="voucherDetail.entries || []" border size="small" stripe>
              <el-table-column prop="rowNum" label="行号" width="55" align="center" />
              <el-table-column prop="summary" label="摘要" min-width="120" show-overflow-tooltip />
              <el-table-column prop="subjectCode" label="科目" min-width="130">
                <template #default="{ row }">
                  <span class="mono-code">{{ row.subjectCode }}</span>
                  <div style="font-size: 11px; color: #8c8c8c;">{{ row.subjectName }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="debitCredit" label="方向" width="60" align="center">
                <template #default="{ row }">
                  <span :class="row.debitCredit === 1 ? 'dir-debit' : 'dir-credit'">
                    {{ row.debitCredit === 1 ? '借' : '贷' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="金额" width="120" align="right">
                <template #default="{ row }">
                  <b class="mono">{{ formatMoney(row.amount) }}</b>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 凭证辅助核算项分摊 -->
          <div v-if="voucherDetail.auxiliaries && voucherDetail.auxiliaries.length > 0" class="drawer-sub-section">
            <div class="sub-title">辅助核算维度分摊</div>
            <el-table :data="voucherDetail.auxiliaries" border size="small" stripe>
              <el-table-column prop="auxType" label="核算类别" width="110" />
              <el-table-column prop="auxCode" label="核算项编码" width="110">
                <template #default="{ row }">
                  <span class="mono-code">{{ row.auxCode }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="auxName" label="核算项名称" min-width="120" />
              <el-table-column prop="changeDirection" label="增减" width="70" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.changeDirection === 1 ? 'primary' : 'warning'">
                    {{ row.changeDirection === 1 ? '增加' : '减少' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="分摊金额" width="120" align="right">
                <template #default="{ row }">
                  <b class="mono">{{ formatMoney(row.amount) }}</b>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  Search,
  Refresh,
  Download,
  Printer,
  CollectionTag,
  DataBoard
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getAuxiliaryTypes,
  getAuxiliaryItems,
  getAuxiliarySummary,
  getAuxiliaryMatrix,
  getAuxiliarySubjectBreakdown,
  getAuxiliaryEntries,
  type AuxiliaryItemResponse,
  type AuxiliarySummaryResponse,
  type AuxiliaryMatrixResponse,
  type AuxiliarySubjectDetailResponse,
  type AuxiliaryEntryRecordResponse
} from '@/api/auxiliary-report'
import { getVoucherDetail, type VoucherFullDetail } from '@/api/voucher'
import type { DictResponse } from '@/api/dict'

// ==================== 状态定义 ====================

// 期间与日期默认值
const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1
const startStr = `${year}-${String(month).padStart(2, '0')}-01`
const lastDay = new Date(year, month, 0).getDate()
const endStr = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`

const dateRange = ref<[string, string]>([startStr, endStr])
const auxTypeList = ref<DictResponse[]>([])
const auxItems = ref<AuxiliaryItemResponse[]>([])
const selectedAuxCode = ref<string>('ALL')
const itemSearchKeyword = ref<string>('')

const queryForm = reactive({
  auxType: 'DEPARTMENT',
  subjectCategory: undefined as number | undefined,
  keyword: ''
})

const activeTab = ref<string>('matrix')

// 报表与统计状态
const loading = ref(false)
const loadingMatrix = ref(false)
const loadingSubjects = ref(false)
const loadingEntries = ref(false)

const summaryData = ref<AuxiliarySummaryResponse | null>(null)
const matrixData = ref<AuxiliaryMatrixResponse | null>(null)
const subjectBreakdownList = ref<AuxiliarySubjectDetailResponse[]>([])

// 凭证流水明细分页
const entryList = ref<AuxiliaryEntryRecordResponse[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const entryTotal = ref(0)

// 凭证穿透档案抽屉
const voucherDrawerVisible = ref(false)
const currentVoucherNo = ref('')
const loadingVoucherDetail = ref(false)
const voucherDetail = ref<VoucherFullDetail | null>(null)

// ==================== 计算属性 ====================

const currentAuxTypeName = computed(() => {
  const matched = auxTypeList.value.find((item) => item.dictCode === queryForm.auxType)
  return matched ? matched.dictName : '辅助核算'
})

const filteredAuxItems = computed(() => {
  if (!itemSearchKeyword.value) {
    return auxItems.value
  }
  const kw = itemSearchKeyword.value.toLowerCase().trim()
  return auxItems.value.filter(
    (item) =>
      item.auxName.toLowerCase().includes(kw) ||
      item.auxCode.toLowerCase().includes(kw)
  )
})

const currentSelectedItemName = computed(() => {
  if (selectedAuxCode.value === 'ALL') {
    return `全${currentAuxTypeName.value}汇总`
  }
  const found = auxItems.value.find((item) => item.auxCode === selectedAuxCode.value)
  return found ? found.auxName : selectedAuxCode.value
})

const currentPeriodDesc = computed(() => {
  if (dateRange.value && dateRange.value.length === 2) {
    return `${dateRange.value[0]} ~ ${dateRange.value[1]}`
  }
  return '本期'
})

// ==================== 业务方法 ====================

function handleDateRangeChange(val: [string, string]) {
  if (val && val.length === 2) {
    loadAllData()
  }
}

async function handleAuxTypeChange() {
  selectedAuxCode.value = 'ALL'
  await loadAuxItems()
  loadAllData()
}

function handleSelectAuxItem(code: string) {
  selectedAuxCode.value = code
  loadAllData()
}

function handleSearch() {
  loadAllData()
}

function handleReset() {
  dateRange.value = [startStr, endStr]
  queryForm.subjectCategory = undefined
  queryForm.keyword = ''
  selectedAuxCode.value = 'ALL'
  loadAllData()
}

function handleTabChange() {
  if (activeTab.value === 'matrix') {
    fetchMatrix()
  } else if (activeTab.value === 'subjects') {
    fetchSubjects()
  } else if (activeTab.value === 'entries') {
    fetchEntries()
  }
}

function buildQueryParams() {
  return {
    auxType: queryForm.auxType,
    auxCode: selectedAuxCode.value === 'ALL' ? undefined : selectedAuxCode.value,
    startDate: dateRange.value ? dateRange.value[0] : undefined,
    endDate: dateRange.value ? dateRange.value[1] : undefined,
    subjectCategory: queryForm.subjectCategory,
    keyword: queryForm.keyword ? queryForm.keyword.trim() : undefined
  }
}

async function loadAuxTypes() {
  try {
    const list = await getAuxiliaryTypes()
    auxTypeList.value = list || []
    if (list && list.length > 0 && !queryForm.auxType) {
      queryForm.auxType = list[0].dictCode
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载辅助核算类别失败')
  }
}

async function loadAuxItems() {
  try {
    const items = await getAuxiliaryItems({
      auxType: queryForm.auxType,
      startDate: dateRange.value ? dateRange.value[0] : undefined,
      endDate: dateRange.value ? dateRange.value[1] : undefined
    })
    auxItems.value = items || []
  } catch (err: any) {
    console.error('加载核算项目失败:', err)
  }
}

async function fetchSummary() {
  try {
    const res = await getAuxiliarySummary(buildQueryParams())
    summaryData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载统计指标失败')
  }
}

async function fetchMatrix() {
  loadingMatrix.value = true
  try {
    const res = await getAuxiliaryMatrix(buildQueryParams())
    matrixData.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '加载交叉汇总对比失败')
  } finally {
    loadingMatrix.value = false
  }
}

async function fetchSubjects() {
  loadingSubjects.value = true
  try {
    const res = await getAuxiliarySubjectBreakdown(buildQueryParams())
    subjectBreakdownList.value = res || []
  } catch (err: any) {
    ElMessage.error(err.message || '加载科目分布明细失败')
  } finally {
    loadingSubjects.value = false
  }
}

async function fetchEntries() {
  loadingEntries.value = true
  try {
    const res = await getAuxiliaryEntries({
      ...buildQueryParams(),
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    entryList.value = res.list || res.records || []
    entryTotal.value = res.total || 0
  } catch (err: any) {
    ElMessage.error(err.message || '加载凭证记录失败')
  } finally {
    loadingEntries.value = false
  }
}

function loadAllData() {
  fetchSummary()
  if (activeTab.value === 'matrix') {
    fetchMatrix()
  } else if (activeTab.value === 'subjects') {
    fetchSubjects()
  } else if (activeTab.value === 'entries') {
    fetchEntries()
  }
}

// 凭证穿透详情
async function handleViewVoucher(voucherNo: string) {
  if (!voucherNo) return
  currentVoucherNo.value = voucherNo
  voucherDrawerVisible.value = true
  loadingVoucherDetail.value = true
  try {
    const res = await getVoucherDetail(voucherNo)
    voucherDetail.value = res
  } catch (err: any) {
    ElMessage.error(err.message || '获取凭证档案失败')
  } finally {
    loadingVoucherDetail.value = false
  }
}

// 矩阵透视表合计行计算
interface SummaryMethodProps {
  columns: any[]
  data: any[]
}

function getMatrixSummary(param: SummaryMethodProps) {
  const { columns } = param
  const sums: string[] = []

  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '本期发生合计'
      return
    }
    if (index === 1) {
      sums[index] = ''
      return
    }
    // 动态列合计
    const prop = column.property
    if (prop && prop.startsWith('amounts.')) {
      const colAuxCode = prop.replace('amounts.', '')
      const colAmt = matrixData.value?.columnTotals?.[colAuxCode]
      sums[index] = colAmt ? formatMoney(colAmt) : '-'
      return
    }
    if (prop === 'totalAmount') {
      sums[index] = formatMoney(matrixData.value?.grandTotal)
      return
    }
    if (prop === 'percentage') {
      sums[index] = matrixData.value?.grandTotal ? '100.00%' : '-'
      return
    }
    sums[index] = ''
  })

  return sums
}

function getProgressColor(pct?: number): string {
  if (!pct) return '#1890ff'
  if (pct >= 50) return '#fa8c16'
  if (pct >= 25) return '#1890ff'
  return '#52c41a'
}

function handleExport() {
  ElMessage.info(`正在导出 ${currentAuxTypeName.value} 辅助核算账簿 Excel...`)
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
  loading.value = true
  try {
    await loadAuxTypes()
    await loadAuxItems()
    loadAllData()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.auxiliary-ledger-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.aux-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
  min-height: calc(100vh - 120px);
}

.fin-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

/* 左侧核算项目列表 */
.aux-sidebar {
  width: 250px;
  min-width: 250px;
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 0 4px;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2229;
  display: flex;
  align-items: center;
  gap: 6px;
}

.head-icon {
  color: #1890ff;
}

.sidebar-search {
  margin-bottom: 12px;
}

.item-scroll-list {
  flex: 1;
  padding-right: 4px;
}

.item-node {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  transition: all 0.2s;

  &:hover {
    background-color: #f0f7ff;
  }

  &.active {
    background-color: #e6f7ff;
    color: #1890ff;
    font-weight: 600;

    .node-code {
      color: #1890ff;
    }
  }

  &.root-node {
    margin-bottom: 8px;
    border: 1px dashed #d9d9d9;
    background: #fafafa;

    &.active {
      border-color: #1890ff;
      background: #e6f7ff;
    }
  }
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.node-icon {
  color: #1890ff;
  font-size: 14px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d9d9d9;
  flex-shrink: 0;

  &.active {
    background: #1890ff;
  }
}

.node-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.node-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-code {
  font-size: 11px;
  color: #8c8c8c;
}

.node-badge {
  font-size: 11px;
  color: #8c8c8c;
}

.node-amt {
  font-size: 11px;
  color: #595959;
}

/* 右侧主内容区 */
.aux-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.filter-card {
  padding-bottom: 0;
}

/* KPI 卡片 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.kpi-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border-left: 4px solid #d9d9d9;

  &.blue { border-left-color: #1890ff; }
  &.green { border-left-color: #52c41a; }
  &.orange { border-left-color: #fa8c16; }
  &.purple { border-left-color: #722ed1; }
  &.red { border-left-color: #f5222d; }
}

.kpi-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2229;
  line-height: 1.2;
}

.kpi-unit {
  font-size: 12px;
  font-weight: normal;
  color: #8c8c8c;
  margin-left: 2px;
}

.kpi-sub {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 6px;
}

/* 占比分布卡片 */
.proportion-card {
  padding: 14px 18px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .title {
    font-size: 13px;
    font-weight: 600;
    color: #1f2229;
  }

  .hint {
    font-size: 12px;
    color: #8c8c8c;
  }
}

.proportion-bar-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.proportion-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.prop-label {
  width: 100px;
  font-size: 12px;
  color: #595959;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.prop-progress {
  flex: 1;
}

.prop-amount {
  width: 120px;
  font-size: 12px;
  color: #595959;
  text-align: right;
}

/* 表格区域 */
.table-section {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.aux-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.tab-pane-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .tb-title {
    font-size: 13px;
    font-weight: 600;
    color: #1f2229;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .period-tag {
    font-weight: normal;
  }

  .tb-hint {
    font-size: 12px;
    color: #8c8c8c;
  }

  .hint {
    font-weight: normal;
    font-size: 12px;
    color: #8c8c8c;
  }
}

.fin-table {
  border-radius: 4px;
}

.mono-code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-weight: 600;
  color: #1890ff;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.text-amount {
  color: #262626;
  font-weight: 500;
}

.text-primary { color: #1890ff; }
.text-debit { color: #cf1322; }
.text-credit { color: #389e0d; }
.text-purple { color: #722ed1; }
.text-dark { color: #262626; }
.text-placeholder { color: #bfbfbf; }

.highlight-money {
  color: #1890ff;
  font-size: 13px;
}

.pct-text {
  color: #8c8c8c;
  font-size: 12px;
}

.subject-name-cell {
  font-size: 13px;
}

.category-tag {
  margin-left: 6px;
  font-size: 10px;
}

.subject-pct-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pct-val {
  font-size: 11px;
  color: #8c8c8c;
  width: 45px;
  text-align: right;
}

.dir-debit {
  color: #cf1322;
  font-weight: 600;
}

.dir-credit {
  color: #389e0d;
  font-weight: 600;
}

.tag-dept {
  background-color: #e6f7ff;
  color: #0050b3;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
}

/* 凭证详情抽屉 */
.voucher-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-descriptions {
  margin-bottom: 8px;
}

.drawer-sub-section {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .sub-title {
    font-size: 13px;
    font-weight: 600;
    color: #1f2229;
  }
}
</style>

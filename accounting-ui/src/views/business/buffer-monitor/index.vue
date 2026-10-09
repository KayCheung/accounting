<!-- 文件路径：src/views/business/buffer-monitor/index.vue -->
<template>
  <div class="buffer-monitor-page">
    <!-- ==================== 顶部检索与控制栏 ==================== -->
    <div class="fin-card control-card">
      <el-form :inline="true" class="monitor-control-form">
        <el-form-item label="会计日期">
          <el-date-picker
            v-model="currentAccountingDate"
            type="date"
            placeholder="选择会计日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 160px;"
            @change="handleDateChange"
          />
        </el-form-item>

        <el-form-item label="状态筛选">
          <el-select
            v-model="selectedStatus"
            placeholder="全部状态"
            clearable
            style="width: 130px;"
            @change="fetchMonitorData"
          >
            <el-option
              v-for="item in BUFFER_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loadingMonitor || loadingStats" @click="fetchAllData">
            查询监控
          </el-button>
          <el-button :icon="Refresh" :loading="loadingMonitor || loadingStats" @click="fetchAllData">
            刷新
          </el-button>
        </el-form-item>

        <div class="header-action-group">
          <el-button
            type="danger"
            :icon="VideoPlay"
            :loading="executingPosting"
            @click="openExecuteDialog()"
          >
            手动触发缓冲入账
          </el-button>
        </div>
      </el-form>
    </div>

    <!-- ==================== 卡片 1: 待入账模式存量看板 ==================== -->
    <div class="fin-card pending-stats-card" v-loading="loadingStats">
      <div class="card-header-bar">
        <div class="header-title-box">
          <el-icon class="header-icon"><Histogram /></el-icon>
          <span class="header-title">待入账模式存量看板</span>
          <span class="header-subtitle">按 3 种缓冲模式统计待处理明细规模</span>
        </div>
        <div class="header-meta-box">
          <span class="meta-item">
            涉及账户总数：<strong class="mono highlight-text">{{ pendingStats?.totalAccounts ?? 0 }} 户</strong>
          </span>
          <span class="meta-divider">|</span>
          <span class="meta-item">
            最早待入账时间：
            <strong class="mono" :class="{ 'warning-text': hasLongPendingTime }">
              {{ formatDateTime(pendingStats?.oldestPendingTime) }}
            </strong>
          </span>
        </div>
      </div>

      <div class="pending-mode-grid">
        <!-- 模式 1: 异步逐条 -->
        <div class="mode-kpi-cell mode-1">
          <div class="kpi-top">
            <span class="mode-name">模式 1 · 异步逐条 (ASYNC_SINGLE)</span>
            <el-tag size="small" type="primary" effect="plain">即时缓冲</el-tag>
          </div>
          <div class="kpi-body">
            <div class="count-block">
              <span class="sub-label">待入账笔数</span>
              <span class="count-value mono">{{ pendingStats?.mode1Count ?? 0 }} 笔</span>
            </div>
            <div class="amount-block">
              <span class="sub-label">待入账金额</span>
              <div class="amount-val">
                <AmountDisplay :value="pendingStats?.mode1Amount ?? 0" prefix="¥ " align="left" />
              </div>
            </div>
          </div>
          <div class="kpi-footer">
            <el-button
              link
              type="primary"
              size="small"
              :icon="TopRight"
              @click="openExecuteDialog(1)"
            >
              触发模式 1 入账
            </el-button>
          </div>
        </div>

        <!-- 模式 2: 日间批量 -->
        <div class="mode-kpi-cell mode-2">
          <div class="kpi-top">
            <span class="mode-name">模式 2 · 日间批量 (DAILY_BATCH)</span>
            <el-tag size="small" type="success" effect="plain">定时批量</el-tag>
          </div>
          <div class="kpi-body">
            <div class="count-block">
              <span class="sub-label">待入账笔数</span>
              <span class="count-value mono">{{ pendingStats?.mode2Count ?? 0 }} 笔</span>
            </div>
            <div class="amount-block">
              <span class="sub-label">待入账金额</span>
              <div class="amount-val">
                <AmountDisplay :value="pendingStats?.mode2Amount ?? 0" prefix="¥ " align="left" />
              </div>
            </div>
          </div>
          <div class="kpi-footer">
            <el-button
              link
              type="success"
              size="small"
              :icon="TopRight"
              @click="openExecuteDialog(2)"
            >
              触发模式 2 入账
            </el-button>
          </div>
        </div>

        <!-- 模式 3: 日终批量 -->
        <div class="mode-kpi-cell mode-3">
          <div class="kpi-top">
            <span class="mode-name">模式 3 · 日终批量 (EOD_BATCH)</span>
            <el-tag size="small" type="warning" effect="plain">日终汇总</el-tag>
          </div>
          <div class="kpi-body">
            <div class="count-block">
              <span class="sub-label">待入账笔数</span>
              <span class="count-value mono">{{ pendingStats?.mode3Count ?? 0 }} 笔</span>
            </div>
            <div class="amount-block">
              <span class="sub-label">待入账金额</span>
              <div class="amount-val">
                <AmountDisplay :value="pendingStats?.mode3Amount ?? 0" prefix="¥ " align="left" />
              </div>
            </div>
          </div>
          <div class="kpi-footer">
            <el-button
              link
              type="warning"
              size="small"
              :icon="TopRight"
              @click="openExecuteDialog(3)"
            >
              触发模式 3 入账
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 卡片 2: 状态分布与入账流转大盘 ==================== -->
    <div class="fin-card status-breakdown-card" v-loading="loadingMonitor">
      <div class="card-header-bar">
        <div class="header-title-box">
          <el-icon class="header-icon"><Odometer /></el-icon>
          <span class="header-title">入账状态分布与流转大盘</span>
          <span class="header-subtitle">
            当日缓冲明细总记录数：<strong class="mono bold-text">{{ monitorData?.totalRecords ?? 0 }} 笔</strong>
          </span>
        </div>
      </div>

      <div class="status-grid">
        <div
          v-for="st in statusDisplayList"
          :key="st.status"
          class="status-cell"
          :class="`status-theme-${st.status}`"
        >
          <div class="cell-header">
            <el-tag :type="getStatusTagType(st.status)" size="default">
              {{ st.statusDesc }}
            </el-tag>
            <span class="cell-percent mono">{{ calculatePercent(st.count) }}</span>
          </div>
          <div class="cell-main">
            <div class="main-count mono">{{ st.count }} <span class="unit">笔</span></div>
            <div class="main-amount">
              <span class="sub-text">发生额：</span>
              <AmountDisplay :value="st.amount" prefix="¥ " align="left" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 卡片 3 & 4: 并列布局（Running Balance 告警 + 失败 Top 账户） ==================== -->
    <el-row :gutter="16">
      <!-- 左侧：Running Balance 动账余额校验告警（核心风控） -->
      <el-col :xs="24" :lg="14">
        <div class="fin-card section-card balance-alert-card" v-loading="loadingMonitor">
          <div class="section-title-row">
            <div class="title-with-icon">
              <el-icon class="title-icon"><CircleCheck /></el-icon>
              <span class="title-text">Running Balance 动账余额校验告警</span>
            </div>
            <div class="action-wrap">
              <el-tag
                :type="hasRunningBalanceAlerts ? 'danger' : 'success'"
                size="small"
                effect="dark"
              >
                {{ hasRunningBalanceAlerts ? `存在 ${monitorData?.runningBalanceAlerts?.length} 笔偏差告警` : '账实相符 · 全部通过' }}
              </el-tag>
            </div>
          </div>

          <!-- 校验正常态 -->
          <div v-if="!hasRunningBalanceAlerts" class="balance-safe-state">
            <el-result
              icon="success"
              title="Running Balance 账实相符"
              sub-title="当日所有缓冲账户动账余额推导与实际余额完全一致，未发现任何试算偏差。"
            />
          </div>

          <!-- 校验异常态：告警表格 -->
          <div v-else class="balance-alert-content">
            <el-alert
              type="error"
              show-icon
              :closable="false"
              title="发现动账余额与实际余额存在偏差！"
              description="请重点排查以下账户明细流水，防范入账并发导致的版本更新覆盖或遗漏。"
              style="margin-bottom: 12px;"
            />

            <el-table
              :data="monitorData?.runningBalanceAlerts || []"
              border
              stripe
              size="small"
              class="fin-table"
              empty-text="暂无告警记录"
            >
              <el-table-column prop="accountNo" label="告警账户编号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="mono code-link" @click="copyText(row.accountNo, '账户编号')">
                    {{ row.accountNo }}
                  </span>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :icon="CopyDocument"
                    title="复制账号"
                    @click="copyText(row.accountNo, '账户编号')"
                  />
                </template>
              </el-table-column>

              <el-table-column prop="actualBalance" label="当前实际余额" min-width="140" align="right">
                <template #default="{ row }">
                  <AmountDisplay :value="row.actualBalance" align="right" />
                </template>
              </el-table-column>

              <el-table-column prop="calculatedBalance" label="推导计算余额" min-width="140" align="right">
                <template #default="{ row }">
                  <AmountDisplay :value="row.calculatedBalance" align="right" />
                </template>
              </el-table-column>

              <el-table-column prop="diff" label="核对差额 (Diff)" min-width="140" align="right">
                <template #default="{ row }">
                  <span class="text-danger font-bold">
                    <AmountDisplay :value="row.diff" align="right" />
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-col>

      <!-- 右侧：失败 Top 账户排行榜 -->
      <el-col :xs="24" :lg="10">
        <div class="fin-card section-card failed-top-card" v-loading="loadingMonitor">
          <div class="section-title-row">
            <div class="title-with-icon">
              <el-icon class="title-icon text-danger"><WarningFilled /></el-icon>
              <span class="title-text">入账失败 Top 账户排行</span>
            </div>
            <div class="action-wrap">
              <el-tag
                :type="(monitorData?.failedTopAccounts?.length || 0) > 0 ? 'warning' : 'info'"
                size="small"
              >
                {{ (monitorData?.failedTopAccounts?.length || 0) > 0 ? '需人工干预' : '无失败账户' }}
              </el-tag>
            </div>
          </div>

          <el-table
            :data="monitorData?.failedTopAccounts || []"
            border
            stripe
            size="small"
            class="fin-table"
            empty-text="无入账失败账户记录"
          >
            <el-table-column type="index" label="排位" width="50" align="center" />

            <el-table-column prop="accountNo" label="账户编号" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono code-link" @click="copyText(row.accountNo, '账户编号')">
                  {{ row.accountNo }}
                </span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="CopyDocument"
                  title="复制账号"
                  @click="copyText(row.accountNo, '账户编号')"
                />
              </template>
            </el-table-column>

            <el-table-column prop="failedCount" label="失败笔数" width="90" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.failedCount }} 笔</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="totalAmount" label="涉及金额" min-width="120" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.totalAmount" align="right" />
              </template>
            </el-table-column>

            <el-table-column label="快捷操作" width="90" align="center">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="openExecuteDialog(undefined, row.accountNo)"
                >
                  重试入账
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <!-- ==================== 对话框: 手动触发缓冲记账 ==================== -->
    <el-dialog
      v-model="executeDialogVisible"
      title="手动触发缓冲记账"
      width="480px"
      destroy-on-close
    >
      <el-form :model="executeForm" label-width="110px">
        <el-form-item label="会计日期" required>
          <span class="mono bold-text">{{ executeForm.accountingDate }}</span>
        </el-form-item>

        <el-form-item label="缓冲入账模式" required>
          <el-select v-model="executeForm.bufferMode" style="width: 100%;">
            <el-option
              v-for="item in BUFFER_MODE_OPTIONS"
              :key="item.value"
              :label="`${item.value} - ${item.label}`"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="指定账户编号">
          <el-input
            v-model="executeForm.accountNo"
            placeholder="留空则执行该模式下所有账户"
            clearable
          />
        </el-form-item>

        <el-form-item label="单批处理上限">
          <el-input-number
            v-model="executeForm.maxBatchSize"
            :min="1"
            :max="500"
            :step="10"
            style="width: 160px;"
          />
          <span class="form-tip">默认每批 50 笔</span>
        </el-form-item>

        <div class="dialog-notice">
          <el-icon><InfoFilled /></el-icon>
          <span>触发后系统将按选定模式扫描待入账明细，执行过账、更新余额及 Running Balance 校验，单笔失败不阻断整体处理。</span>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="executeDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="executingPosting" @click="handleConfirmExecutePosting">
            确认执行
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- ==================== 抽屉: 执行结果报告 ==================== -->
    <el-drawer
      v-model="resultDrawerVisible"
      title="缓冲记账执行结果报告"
      size="580px"
      destroy-on-close
    >
      <div v-if="executeResult" class="execute-report-container">
        <!-- 统计面板 -->
        <div class="report-overview-box">
          <div class="overview-header">
            <span class="title">执行耗时：<strong class="mono">{{ formatDuration(executeResult.durationMs) }}</strong></span>
            <el-tag :type="executeResult.failedCount === 0 ? 'success' : 'warning'" size="default">
              {{ executeResult.failedCount === 0 ? '全部成功' : '存在部分失败' }}
            </el-tag>
          </div>
          <div class="overview-stats">
            <div class="stat-item">
              <span class="lbl">处理总数</span>
              <span class="val mono">{{ executeResult.totalCount }}</span>
            </div>
            <div class="stat-item text-success">
              <span class="lbl">成功入账</span>
              <span class="val mono">{{ executeResult.successCount }}</span>
            </div>
            <div class="stat-item text-danger">
              <span class="lbl">失败笔数</span>
              <span class="val mono">{{ executeResult.failedCount }}</span>
            </div>
          </div>
        </div>

        <!-- 失败明细列表 -->
        <div class="report-section">
          <div class="section-title">失败明细清单 ({{ executeResult.failedList?.length || 0 }})</div>
          <el-table
            :data="executeResult.failedList || []"
            border
            size="small"
            empty-text="无入账失败明细"
          >
            <el-table-column prop="detailId" label="明细ID" width="90" align="center" />
            <el-table-column prop="accountNo" label="账户编号" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.accountNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="110" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.amount" align="right" />
              </template>
            </el-table-column>
            <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="text-danger">{{ row.failReason || '未知原因' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Search,
  Refresh,
  VideoPlay,
  CopyDocument,
  CircleCheck,
  WarningFilled,
  InfoFilled,
  Odometer,
  TopRight,
  Histogram
} from '@element-plus/icons-vue'
import { toast } from '@/utils/toast'
import { getEodStatus } from '@/api/eod'
import {
  getBufferPendingStats,
  getBufferMonitor,
  executeBufferPosting,
  BUFFER_MODE_OPTIONS,
  BUFFER_STATUS_OPTIONS,
  type BufferPendingStatsResponse,
  type BufferMonitorResponse,
  type BufferStatusCount,
  type BufferExecuteResponse
} from '@/api/buffer-monitor'

// ==================== 响应式状态 ====================

const currentAccountingDate = ref<string>('')
const selectedStatus = ref<number | undefined>(undefined)

const loadingStats = ref<boolean>(false)
const loadingMonitor = ref<boolean>(false)
const executingPosting = ref<boolean>(false)

const pendingStats = ref<BufferPendingStatsResponse | null>(null)
const monitorData = ref<BufferMonitorResponse | null>(null)
const executeResult = ref<BufferExecuteResponse | null>(null)

// 弹窗与抽屉
const executeDialogVisible = ref<boolean>(false)
const executeForm = ref<{
  accountingDate: string
  bufferMode: number
  accountNo: string
  maxBatchSize: number
}>({
  accountingDate: '',
  bufferMode: 1,
  accountNo: '',
  maxBatchSize: 50
})

const resultDrawerVisible = ref<boolean>(false)

// ==================== 计算属性 ====================

const hasRunningBalanceAlerts = computed(() => {
  return (monitorData.value?.runningBalanceAlerts?.length || 0) > 0
})

const hasLongPendingTime = computed(() => {
  if (!pendingStats.value?.oldestPendingTime) return false
  const oldest = new Date(pendingStats.value.oldestPendingTime).getTime()
  const now = Date.now()
  return now - oldest > 30 * 60 * 1000 // 超过 30 分钟提示积压
})

/**
 * 格式化状态分布列表，确保 4 种状态齐全
 */
const statusDisplayList = computed<BufferStatusCount[]>(() => {
  const map: Record<number, BufferStatusCount> = {
    1: { status: 1, statusDesc: '待入账', count: 0, amount: 0 },
    2: { status: 2, statusDesc: '处理中', count: 0, amount: 0 },
    3: { status: 3, statusDesc: '成功', count: 0, amount: 0 },
    4: { status: 4, statusDesc: '失败', count: 0, amount: 0 }
  }

  if (monitorData.value?.statusBreakdown) {
    monitorData.value.statusBreakdown.forEach((item) => {
      map[item.status] = item
    })
  }

  return Object.values(map)
})

// ==================== 辅助方法 ====================

function calculatePercent(count: number): string {
  const total = monitorData.value?.totalRecords || 0
  if (total <= 0 || count <= 0) return '0.0%'
  return `${((count / total) * 100).toFixed(1)}%`
}

function getStatusTagType(status: number): '' | 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 1: return 'info'
    case 2: return 'warning'
    case 3: return 'success'
    case 4: return 'danger'
    default: return 'info'
  }
}

function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '0 ms'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s (${ms} ms)`
}

function formatDateTime(val?: string | null): string {
  if (!val) return '--'
  return val.replace('T', ' ')
}

function copyText(text: string, label: string) {
  if (!text) return
  if (navigator && navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      toast.success(`${label}已复制`)
    }).catch(() => {
      fallbackCopy(text, label)
    })
  } else {
    fallbackCopy(text, label)
  }
}

function fallbackCopy(text: string, label: string) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    toast.success(`${label}已复制`)
  } catch {
    toast.error('复制失败，请手动复制')
  }
  document.body.removeChild(textarea)
}

// ==================== 数据拉取 ====================

async function fetchStats() {
  if (!currentAccountingDate.value) return
  loadingStats.value = true
  try {
    const res = await getBufferPendingStats(currentAccountingDate.value)
    pendingStats.value = res
  } catch {
    // 拦截器提示
  } finally {
    loadingStats.value = false
  }
}

async function fetchMonitorData() {
  loadingMonitor.value = true
  try {
    const res = await getBufferMonitor({
      accountingDate: currentAccountingDate.value || undefined,
      status: selectedStatus.value
    })
    monitorData.value = res
  } catch {
    // 拦截器提示
  } finally {
    loadingMonitor.value = false
  }
}

async function fetchAllData() {
  await Promise.all([fetchStats(), fetchMonitorData()])
}

function handleDateChange(newDate: string) {
  if (newDate) {
    fetchAllData()
  }
}

// ==================== 触发操作 ====================

function openExecuteDialog(mode?: number, accountNo?: string) {
  executeForm.value.accountingDate = currentAccountingDate.value
  executeForm.value.bufferMode = mode || 1
  executeForm.value.accountNo = accountNo || ''
  executeForm.value.maxBatchSize = 50
  executeDialogVisible.value = true
}

async function handleConfirmExecutePosting() {
  if (!executeForm.value.accountingDate) {
    toast.warning('会计日期不能为空')
    return
  }

  executingPosting.value = true
  try {
    const res = await executeBufferPosting({
      accountingDate: executeForm.value.accountingDate,
      bufferMode: executeForm.value.bufferMode,
      accountNo: executeForm.value.accountNo.trim() || undefined,
      maxBatchSize: executeForm.value.maxBatchSize
    })
    executeResult.value = res
    executeDialogVisible.value = false
    resultDrawerVisible.value = true

    if (res.failedCount === 0) {
      toast.success(`缓冲记账成功！共处理 ${res.totalCount} 笔`)
    } else {
      toast.warning(`缓冲记账完成：成功 ${res.successCount} 笔，失败 ${res.failedCount} 笔`)
    }

    await fetchAllData()
  } catch {
    // 拦截器提示
  } finally {
    executingPosting.value = false
  }
}

// ==================== 初始化 ====================

onMounted(async () => {
  try {
    // 优先读取系统当前全局会计日期
    const statusRes = await getEodStatus()
    if (statusRes?.accountingDate) {
      currentAccountingDate.value = statusRes.accountingDate
    } else {
      currentAccountingDate.value = new Date().toISOString().split('T')[0]
    }
  } catch {
    currentAccountingDate.value = new Date().toISOString().split('T')[0]
  }

  fetchAllData()
})
</script>

<style scoped lang="scss">
.buffer-monitor-page {
  padding-bottom: 24px;

  .control-card {
    padding: 14px 20px;
    margin-bottom: 16px;

    .monitor-control-form {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      margin-bottom: 0;

      .el-form-item {
        margin-bottom: 0;
        margin-right: 16px;
      }

      .header-action-group {
        margin-left: auto;
        display: flex;
        gap: 12px;
      }
    }
  }

  .card-header-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f2f3f5;

    .header-title-box {
      display: flex;
      align-items: center;
      gap: 8px;

      .header-icon {
        font-size: 18px;
        color: var(--el-color-primary, #409eff);
      }

      .header-title {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }

      .header-subtitle {
        font-size: 13px;
        color: #909399;
        margin-left: 10px;
      }
    }

    .header-meta-box {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: #606266;

      .meta-divider {
        color: #dcdfe6;
      }

      .highlight-text {
        color: var(--el-color-primary, #409eff);
      }

      .warning-text {
        color: var(--el-color-danger, #f56c6c);
      }
    }
  }

  /* 模式存量卡片 */
  .pending-stats-card {
    margin-bottom: 16px;

    .pending-mode-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;

      .mode-kpi-cell {
        background: #f8f9fb;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 16px 18px;
        transition: all 0.2s ease;

        &:hover {
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
        }

        &.mode-1 {
          border-left: 4px solid var(--el-color-primary, #409eff);
        }
        &.mode-2 {
          border-left: 4px solid var(--el-color-success, #67c23a);
        }
        &.mode-3 {
          border-left: 4px solid var(--el-color-warning, #e6a23c);
        }

        .kpi-top {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 14px;

          .mode-name {
            font-size: 13px;
            font-weight: 600;
            color: #303133;
          }
        }

        .kpi-body {
          display: flex;
          justify-content: space-between;
          margin-bottom: 12px;

          .count-block, .amount-block {
            display: flex;
            flex-direction: column;

            .sub-label {
              font-size: 12px;
              color: #909399;
              margin-bottom: 4px;
            }

            .count-value {
              font-size: 18px;
              font-weight: 700;
              color: #303133;
            }

            .amount-val {
              font-size: 16px;
              font-weight: 600;
            }
          }
        }

        .kpi-footer {
          display: flex;
          justify-content: flex-end;
          border-top: 1px dashed #e4e7ed;
          padding-top: 8px;
        }
      }
    }
  }

  /* 状态分布卡片 */
  .status-breakdown-card {
    margin-bottom: 16px;

    .status-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;

      .status-cell {
        background: #f8f9fb;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 14px 16px;

        .cell-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;

          .cell-percent {
            font-size: 13px;
            color: #909399;
          }
        }

        .cell-main {
          .main-count {
            font-size: 20px;
            font-weight: 700;
            color: #303133;
            margin-bottom: 6px;

            .unit {
              font-size: 12px;
              color: #909399;
              font-weight: normal;
            }
          }

          .main-amount {
            display: flex;
            align-items: center;
            font-size: 13px;

            .sub-text {
              color: #909399;
            }
          }
        }
      }
    }
  }

  /* 下方左右并列区域 */
  .section-card {
    min-height: 300px;
    margin-bottom: 16px;

    .section-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 10px;
      border-bottom: 1px solid #f2f3f5;

      .title-with-icon {
        display: flex;
        align-items: center;
        gap: 8px;

        .title-icon {
          font-size: 18px;
          color: var(--el-color-primary, #409eff);
        }

        .title-text {
          font-size: 15px;
          font-weight: 600;
          color: #303133;
        }
      }
    }
  }

  .balance-alert-card {
    .balance-safe-state {
      padding: 10px 0;
    }
  }

  .code-link {
    cursor: pointer;
    color: var(--el-color-primary, #409eff);
    font-weight: 500;
    margin-right: 4px;

    &:hover {
      text-decoration: underline;
    }
  }

  .bold-text {
    font-weight: 600;
    font-size: 14px;
  }

  .form-tip {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
  }

  .dialog-notice {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-top: 14px;
    padding: 10px 12px;
    background: #f4f4f5;
    border-radius: 4px;
    font-size: 12px;
    color: #606266;
    line-height: 1.5;

    .el-icon {
      color: #909399;
      font-size: 14px;
      margin-top: 2px;
    }
  }

  /* 抽屉样式 */
  .execute-report-container {
    .report-overview-box {
      background: #f8f9fb;
      border: 1px solid #e4e7ed;
      border-radius: 6px;
      padding: 14px 16px;
      margin-bottom: 20px;

      .overview-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 14px;

        .title {
          font-size: 14px;
          color: #606266;
        }
      }

      .overview-stats {
        display: flex;
        justify-content: space-around;

        .stat-item {
          display: flex;
          flex-direction: column;
          align-items: center;

          .lbl {
            font-size: 12px;
            color: #909399;
            margin-bottom: 4px;
          }

          .val {
            font-size: 20px;
            font-weight: 700;
          }
        }
      }
    }

    .report-section {
      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 10px;
      }
    }
  }

  .mono {
    font-family: var(--fin-font-mono, monospace);
  }

  .text-danger {
    color: var(--el-color-danger, #f56c6c);
  }

  .text-success {
    color: var(--el-color-success, #67c23a);
  }
}
</style>

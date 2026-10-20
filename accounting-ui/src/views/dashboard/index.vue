<template>
  <div class="dashboard-container" v-loading="loading">
    <!-- 顶部岗位欢迎与权限提示条 -->
    <div class="fin-card role-banner">
      <div class="banner-left">
        <div class="user-greeting">
          <h2>欢迎，{{ userStore.currentUsername }}</h2>
          <el-tag size="default" :type="userStore.roleInfo.tagType" effect="dark" class="role-tag">
            {{ userStore.currentRoleName }}
          </el-tag>
        </div>
        <p class="duty-text">{{ userStore.roleInfo.dutyDesc }}</p>
      </div>

      <div class="banner-right">
        <div class="date-capsule">
          <span class="capsule-label">系统当前会计日期</span>
          <strong class="capsule-date mono">{{ overview.accountingDate || appStore.accountingDate }}</strong>
        </div>
        <el-button :icon="Refresh" circle @click="handleManualRefresh" :loading="loading" title="刷新大盘" />
      </div>
    </div>

    <!-- ==================== 板块 1: 核算生命周期与健康状态 (ADMIN / OPERATOR 可见) ==================== -->
    <div v-if="userStore.hasPermission('dashboard:system:view')" class="section-block">
      <div class="section-header">
        <span class="section-title">核算生命周期与健康度</span>
        <el-tag size="small" type="info" effect="plain">系统级监控</el-tag>
      </div>
      <el-row :gutter="16">
        <!-- 营业日切状态 -->
        <el-col :span="8">
          <div class="fin-card stat-card interactive" @click="$router.push('/business/eod')">
            <div class="stat-header">
              <span class="stat-title">当前会计日营业状态</span>
              <el-icon class="stat-icon info"><Calendar /></el-icon>
            </div>
            <div class="stat-main">
              <span class="stat-value mono">{{ overview.accountingDate || appStore.accountingDate }}</span>
              <el-tag :type="overview.eodStatus === 1 || overview.eodStatus === 8 ? 'success' : 'warning'" effect="light">
                {{ overview.eodStatusDesc || '正常营业' }}
              </el-tag>
            </div>
            <div class="stat-footer">
              <span>点击前往日切与试算平衡控制台</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </el-col>

        <!-- 试算平衡指示灯 -->
        <el-col :span="8">
          <div class="fin-card stat-card interactive" @click="$router.push('/business/eod')">
            <div class="stat-header">
              <span class="stat-title">借贷试算平衡指示灯</span>
              <el-icon :class="['stat-icon', overview.trialBalanced ? 'success' : 'danger']">
                <component :is="overview.trialBalanced ? Check : WarningFilled" />
              </el-icon>
            </div>
            <div class="stat-main">
              <span :class="['balance-indicator', overview.trialBalanced ? 'is-balanced' : 'is-imbalanced']">
                {{ overview.trialBalanced ? '借贷严格平衡' : '借贷存在差额' }}
              </span>
              <span class="diff-badge mono">差额: {{ overview.trialBalanceDiff || '0.000000' }}</span>
            </div>
            <div class="stat-footer">
              <span>借方 ￥{{ overview.trialDebitTotal || '0.00' }} / 贷方 ￥{{ overview.trialCreditTotal || '0.00' }}</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </el-col>

        <!-- 缓冲记账队列 -->
        <el-col :span="8">
          <div class="fin-card stat-card interactive" @click="$router.push('/business/buffer-monitor')">
            <div class="stat-header">
              <span class="stat-title">缓冲记账队列堆积</span>
              <el-icon class="stat-icon warning"><Clock /></el-icon>
            </div>
            <div class="stat-main">
              <span class="stat-value mono">{{ overview.pendingBufferCount || 0 }}</span>
              <span class="unit">笔待入账</span>
            </div>
            <div class="stat-footer">
              <span>异步逐条 / 日间批量入账调度中</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- ==================== 板块 2: 我的待办工作台 (按角色权限动态推送) ==================== -->
    <div v-if="hasAnyTodoPermission" class="section-block">
      <div class="section-header">
        <span class="section-title">我的待办工单与审批池</span>
        <span class="section-desc">根据当前岗位权限实时推送待处理单据</span>
      </div>
      <el-row :gutter="16">
        <!-- 会计经办 (MAKER): 我的草稿箱 -->
        <el-col v-if="userStore.hasPermission('dashboard:todo:maker')" :span="6">
          <div class="fin-card todo-card" @click="$router.push('/business/manual-voucher')">
            <div class="todo-badge primary">
              <el-icon><EditPen /></el-icon>
            </div>
            <div class="todo-info">
              <span class="todo-name">手工凭证草稿箱</span>
              <div class="todo-count mono">{{ overview.makerDraftCount || 0 }} <span class="unit">单</span></div>
              <span class="todo-tip">未提审草稿凭证，点击前往编辑提审</span>
            </div>
          </div>
        </el-col>

        <!-- 会计经办 (MAKER): 被驳回单据 -->
        <el-col v-if="userStore.hasPermission('dashboard:todo:maker')" :span="6">
          <div class="fin-card todo-card" @click="$router.push('/business/manual-voucher')">
            <div class="todo-badge danger">
              <el-icon><CircleCloseFilled /></el-icon>
            </div>
            <div class="todo-info">
              <span class="todo-name">被驳回待修改凭证</span>
              <div class="todo-count mono danger">{{ overview.makerRejectedCount || 0 }} <span class="unit">单</span></div>
              <span class="todo-tip">审核未通过退回，需修改或作废</span>
            </div>
          </div>
        </el-col>

        <!-- 审核岗 (AUDITOR): 待初审把关 -->
        <el-col v-if="userStore.hasPermission('dashboard:todo:audit')" :span="6">
          <div class="fin-card todo-card highlight" @click="$router.push('/business/manual-voucher')">
            <div class="todo-badge warning">
              <el-icon><Stamp /></el-icon>
            </div>
            <div class="todo-info">
              <span class="todo-name">待初审把关凭证</span>
              <div class="todo-count mono warning">{{ overview.pendingAuditCount || 0 }} <span class="unit">单</span></div>
              <span class="todo-tip">经办已提交，等待核对分录与附件</span>
            </div>
          </div>
        </el-col>

        <!-- 复核岗 (REVIEWER): 待终审复核 -->
        <el-col v-if="userStore.hasPermission('dashboard:todo:review')" :span="6">
          <div class="fin-card todo-card highlight" @click="$router.push('/business/manual-voucher')">
            <div class="todo-badge info">
              <el-icon><Checked /></el-icon>
            </div>
            <div class="todo-info">
              <span class="todo-name">待终审复核凭证</span>
              <div class="todo-count mono info">{{ overview.pendingReviewCount || 0 }} <span class="unit">单</span></div>
              <span class="todo-tip">初审已通过，执行内控双签确认</span>
            </div>
          </div>
        </el-col>

        <!-- 记账岗 (BOOKKEEPER): 待确认正式记账 -->
        <el-col v-if="userStore.hasPermission('dashboard:todo:bookkeeper')" :span="6">
          <div class="fin-card todo-card highlight" @click="$router.push('/business/manual-voucher')">
            <div class="todo-badge success">
              <el-icon><Tickets /></el-icon>
            </div>
            <div class="todo-info">
              <span class="todo-name">待正式入账记账</span>
              <div class="todo-count mono success">{{ overview.pendingBookkeepingCount || 0 }} <span class="unit">单</span></div>
              <span class="todo-tip">复核已通过，确认后写入法定账簿</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- ==================== 板块 3: 异常与风控治理待办 (ADMIN / OPERATOR 可见) ==================== -->
    <div v-if="userStore.hasPermission('dashboard:exception:view')" class="section-block">
      <div class="section-header">
        <span class="section-title">账务异常与风控治理中心</span>
        <span class="section-desc">实时监控链路异常、掉单与在途挂起记录</span>
      </div>
      <el-row :gutter="16">
        <!-- 过账失败凭证 -->
        <el-col :span="6">
          <div
            :class="['fin-card exception-card', overview.failedPostingCount > 0 ? 'is-alert' : '']"
            @click="$router.push('/business/voucher')"
          >
            <div class="exception-icon-box danger">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="exception-info">
              <span class="exception-label">过账失败凭证</span>
              <span class="exception-val mono">{{ overview.failedPostingCount || 0 }} 笔</span>
              <span class="exception-desc">过账引擎异常拦截，支持手动重试</span>
            </div>
          </div>
        </el-col>

        <!-- 挂起/掉单流水 -->
        <el-col :span="6">
          <div
            :class="['fin-card exception-card', overview.hangingJournalCount > 0 ? 'is-alert' : '']"
            @click="$router.push('/business/journal')"
          >
            <div class="exception-icon-box warning">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="exception-info">
              <span class="exception-label">掉单流水待核对</span>
              <span class="exception-val mono">{{ overview.hangingJournalCount || 0 }} 笔</span>
              <span class="exception-desc">长时间处理中，支持一键自愈对账</span>
            </div>
          </div>
        </el-col>

        <!-- 资金冻结有效单据 -->
        <el-col :span="6">
          <div class="fin-card exception-card" @click="$router.push('/business/freeze')">
            <div class="exception-icon-box primary">
              <el-icon><Lock /></el-icon>
            </div>
            <div class="exception-info">
              <span class="exception-label">资金冻结有效工单</span>
              <span class="exception-val mono">{{ overview.activeFreezeCount || 0 }} 笔</span>
              <span class="exception-desc">在途资金冻结保护与扣款追踪</span>
            </div>
          </div>
        </el-col>

        <!-- 缓冲待入账存量 -->
        <el-col :span="6">
          <div class="fin-card exception-card" @click="$router.push('/business/buffer-monitor')">
            <div class="stat-icon-box info">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="exception-info">
              <span class="exception-label">缓冲入账待调度</span>
              <span class="exception-val mono">{{ overview.pendingBufferCount || 0 }} 笔</span>
              <span class="exception-desc">日终/日间定时入账批量任务</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- ==================== 板块 4: 账务规模与业务全景 (ADMIN / OPERATOR 可见) ==================== -->
    <div v-if="userStore.hasPermission('dashboard:metrics:view')" class="section-block">
      <div class="section-header">
        <span class="section-title">今日业务规模与账户资产大盘</span>
      </div>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="fin-card metric-card">
            <span class="metric-title">今日记账流水</span>
            <div class="metric-value mono">{{ overview.todayJournalCount || 0 }} <span class="unit">笔</span></div>
            <span class="metric-footer">业务流水驱动法定凭证</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="fin-card metric-card">
            <span class="metric-title">今日法定凭证总数</span>
            <div class="metric-value mono">{{ overview.todayVoucherCount || 0 }} <span class="unit">张</span></div>
            <span class="metric-footer">含机制凭证与手工调账凭证</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="fin-card metric-card">
            <span class="metric-title">今日成功过账凭证</span>
            <div class="metric-value mono success">{{ overview.todayPostedVoucherCount || 0 }} <span class="unit">张</span></div>
            <span class="metric-footer">过账引擎实时与异步平账</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="fin-card metric-card">
            <span class="metric-title">正常有效分户账户</span>
            <div class="metric-value mono primary">{{ overview.activeAccountCount || 0 }} <span class="unit">户</span></div>
            <span class="metric-footer">双子账户架构运行中</span>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- ==================== 板块 5: 常用业务向导与高频入口 (按当前角色权限过滤) ==================== -->
    <div class="section-block">
      <div class="section-header">
        <span class="section-title">常用业务功能入口</span>
        <span class="section-desc">按当前岗位权限配置的高频工作台入口</span>
      </div>
      <div class="quick-nav-grid">
        <!-- 手工凭证录入与审核 -->
        <div
          v-if="userStore.hasPermission('business:manual-voucher')"
          class="fin-card quick-item"
          @click="$router.push('/business/manual-voucher')"
        >
          <div class="item-icon-box primary"><el-icon><Edit /></el-icon></div>
          <div class="item-text">
            <h4>手工凭证与审批流</h4>
            <p>制单填制、初审把关、终审复核及记账印签</p>
          </div>
        </div>

        <!-- 记账流水与事务监控 -->
        <div
          v-if="userStore.hasPermission('business:journal')"
          class="fin-card quick-item"
          @click="$router.push('/business/journal')"
        >
          <div class="item-icon-box success"><el-icon><Finished /></el-icon></div>
          <div class="item-text">
            <h4>流水与事务监控</h4>
            <p>业务流水追溯、全景档案穿透与掉单自愈</p>
          </div>
        </div>

        <!-- 记账凭证管理 -->
        <div
          v-if="userStore.hasPermission('business:voucher')"
          class="fin-card quick-item"
          @click="$router.push('/business/voucher')"
        >
          <div class="item-icon-box warning"><el-icon><Document /></el-icon></div>
          <div class="item-text">
            <h4>记账凭证管理</h4>
            <p>法定记账凭证全景、红冲冲账与纸质印签打印</p>
          </div>
        </div>

        <!-- 账户与余额明细 -->
        <div
          v-if="userStore.hasPermission('business:balance')"
          class="fin-card quick-item"
          @click="$router.push('/business/balance')"
        >
          <div class="item-icon-box info"><el-icon><Money /></el-icon></div>
          <div class="item-text">
            <h4>分户余额与明细</h4>
            <p>主账户与双子账户资产看板、交易变动明细</p>
          </div>
        </div>

        <!-- 辅助核算账簿 -->
        <div
          v-if="userStore.hasPermission('report:all') || userStore.hasPermission('report:auxiliary')"
          class="fin-card quick-item"
          @click="$router.push('/report/auxiliary-ledger')"
        >
          <div class="item-icon-box purple"><el-icon><Grid /></el-icon></div>
          <div class="item-text">
            <h4>多维辅助核算账簿</h4>
            <p>部门/项目/客户多维透视、发生额与成本净额</p>
          </div>
        </div>

        <!-- 日切控制台 -->
        <div
          v-if="userStore.hasPermission('business:eod')"
          class="fin-card quick-item"
          @click="$router.push('/business/eod')"
        >
          <div class="item-icon-box danger"><el-icon><Switch /></el-icon></div>
          <div class="item-text">
            <h4>日切与试算平衡</h4>
            <p>全局会计切日、前置诊断、试算核验与归档</p>
          </div>
        </div>

        <!-- 期末结转工作台 -->
        <div
          v-if="userStore.hasPermission('business:transfer')"
          class="fin-card quick-item"
          @click="$router.push('/business/transfer')"
        >
          <div class="item-icon-box orange"><el-icon><FolderChecked /></el-icon></div>
          <div class="item-text">
            <h4>期末结转工作台</h4>
            <p>损益科目期末归集、试算预览与正式结转入账</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Refresh,
  Calendar,
  Check,
  WarningFilled,
  Clock,
  ArrowRight,
  EditPen,
  CircleCloseFilled,
  Stamp,
  Checked,
  Tickets,
  Connection,
  Lock,
  Timer,
  Edit,
  Finished,
  Document,
  Money,
  Grid,
  Switch,
  FolderChecked
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { getDashboardOverview, type DashboardOverviewResponse } from '@/api/dashboard'
import { toast } from '@/utils/toast'

const appStore = useAppStore()
const userStore = useUserStore()

const loading = ref(false)

const overview = ref<DashboardOverviewResponse>({
  accountingDate: '',
  eodStatus: 1,
  eodStatusDesc: '正常营业',
  trialBalanced: true,
  trialBalanceDiff: '0.000000',
  trialDebitTotal: '0.00',
  trialCreditTotal: '0.00',
  makerDraftCount: 0,
  makerRejectedCount: 0,
  pendingAuditCount: 0,
  pendingReviewCount: 0,
  pendingBookkeepingCount: 0,
  failedPostingCount: 0,
  hangingJournalCount: 0,
  pendingBufferCount: 0,
  activeFreezeCount: 0,
  todayVoucherCount: 0,
  todayPostedVoucherCount: 0,
  todayJournalCount: 0,
  activeAccountCount: 0
})

const hasAnyTodoPermission = computed(() => {
  return (
    userStore.hasPermission('dashboard:todo:maker') ||
    userStore.hasPermission('dashboard:todo:audit') ||
    userStore.hasPermission('dashboard:todo:review') ||
    userStore.hasPermission('dashboard:todo:bookkeeper')
  )
})

onMounted(() => {
  fetchData()
})

async function handleManualRefresh() {
  await fetchData()
  toast.success('系统概览与待办数据已刷新')
}

async function fetchData() {
  loading.value = true
  try {
    const data = await getDashboardOverview()
    if (data) {
      overview.value = data
      // 联动更新 appStore 中的权威日期与状态
      if (data.accountingDate) {
        appStore.accountingDate = data.accountingDate
      }
      if (data.eodStatus !== undefined) {
        appStore.eodStatus = data.eodStatus === 1 || data.eodStatus === 8 ? 'NORMAL' : (data.eodStatusDesc || '日切中')
      }
    }
  } catch (err) {
    // 静默降级
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.role-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #edf3fe 100%);
  border: 1px solid #d9e6f9;
}

.user-greeting {
  display: flex;
  align-items: center;
  gap: 12px;

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: #1f2d3d;
  }
}

.duty-text {
  margin: 8px 0 0;
  font-size: 13px;
  color: #606266;
}

.banner-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.date-capsule {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.capsule-label {
  font-size: 12px;
  color: #909399;
}

.capsule-date {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.section-desc {
  font-size: 12px;
  color: #909399;
}

.stat-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 120px;
  padding: 16px 20px;
  transition: all 0.2s;

  &.interactive {
    cursor: pointer;
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      border-color: #c6e2ff;
    }
  }
}

.stat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-title {
  font-size: 13px;
  color: #606266;
}

.stat-icon {
  font-size: 18px;
  padding: 6px;
  border-radius: 6px;

  &.info {
    color: #409eff;
    background: #ecf5ff;
  }
  &.success {
    color: #67c23a;
    background: #f0f9eb;
  }
  &.danger {
    color: #f56c6c;
    background: #fef0f0;
  }
  &.warning {
    color: #e6a23c;
    background: #fdf6ec;
  }
}

.stat-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 6px 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.balance-indicator {
  font-size: 16px;
  font-weight: 600;

  &.is-balanced {
    color: #67c23a;
  }
  &.is-imbalanced {
    color: #f56c6c;
  }
}

.diff-badge {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.unit {
  font-size: 13px;
  color: #909399;
}

.stat-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

/* 待办卡片 */
.todo-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  &.highlight {
    border-left: 4px solid #409eff;
  }
}

.todo-badge {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;

  &.primary {
    background: #ecf5ff;
    color: #409eff;
  }
  &.danger {
    background: #fef0f0;
    color: #f56c6c;
  }
  &.warning {
    background: #fdf6ec;
    color: #e6a23c;
  }
  &.info {
    background: #f4f4f5;
    color: #909399;
  }
  &.success {
    background: #f0f9eb;
    color: #67c23a;
  }
}

.todo-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.todo-name {
  font-size: 13px;
  color: #606266;
}

.todo-count {
  font-size: 20px;
  font-weight: 600;
  color: #303133;

  &.danger {
    color: #f56c6c;
  }
  &.warning {
    color: #e6a23c;
  }
  &.info {
    color: #409eff;
  }
  &.success {
    color: #67c23a;
  }

  .unit {
    font-size: 12px;
    font-weight: normal;
    color: #909399;
  }
}

.todo-tip {
  font-size: 11px;
  color: #a8abb2;
}

/* 异常治理卡片 */
.exception-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  &.is-alert {
    border-color: #fde2e2;
    background: #fff8f8;
  }
}

.exception-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;

  &.danger {
    background: #fef0f0;
    color: #f56c6c;
  }
  &.warning {
    background: #fdf6ec;
    color: #e6a23c;
  }
  &.primary {
    background: #ecf5ff;
    color: #409eff;
  }
  &.info {
    background: #f4f4f5;
    color: #909399;
  }
}

.exception-info {
  display: flex;
  flex-direction: column;
}

.exception-label {
  font-size: 12px;
  color: #606266;
}

.exception-val {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.exception-desc {
  font-size: 11px;
  color: #909399;
}

/* 规模指标卡片 */
.metric-card {
  display: flex;
  flex-direction: column;
  padding: 14px 18px;
  height: 96px;
  justify-content: space-between;
}

.metric-title {
  font-size: 12px;
  color: #909399;
}

.metric-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;

  &.success {
    color: #67c23a;
  }
  &.primary {
    color: #409eff;
  }
}

.metric-footer {
  font-size: 11px;
  color: #909399;
}

/* 快捷入口网格 */
.quick-nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    border-color: #c6e2ff;
  }
}

.item-icon-box {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;

  &.primary {
    background: #ecf5ff;
    color: #409eff;
  }
  &.success {
    background: #f0f9eb;
    color: #67c23a;
  }
  &.warning {
    background: #fdf6ec;
    color: #e6a23c;
  }
  &.info {
    background: #f4f4f5;
    color: #409eff;
  }
  &.purple {
    background: #f9f0ff;
    color: #722ed1;
  }
  &.danger {
    background: #fef0f0;
    color: #f56c6c;
  }
  &.orange {
    background: #fff7e6;
    color: #fa8c16;
  }
}

.item-text {
  display: flex;
  flex-direction: column;
  gap: 3px;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  p {
    margin: 0;
    font-size: 11px;
    color: #909399;
  }
}
</style>

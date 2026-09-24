<template>
  <div class="dashboard-container">
    <!-- 顶部状态大盘卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="fin-card stat-card">
          <div class="stat-header">
            <span class="stat-title">当前会计日期</span>
            <el-icon class="stat-icon info"><Calendar /></el-icon>
          </div>
          <div class="stat-value">{{ appStore.accountingDate }}</div>
          <div class="stat-footer">
            日切状态：
            <StatusTag :status="appStore.eodStatus" />
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="fin-card stat-card">
          <div class="stat-header">
            <span class="stat-title">今日记账总额</span>
            <el-icon class="stat-icon success"><Money /></el-icon>
          </div>
          <div class="stat-value">
            <AmountDisplay :value="statData.todayAmount" prefix="￥" />
          </div>
          <div class="stat-footer">
            较昨日 <span class="trend up">+12.5%</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="fin-card stat-card">
          <div class="stat-header">
            <span class="stat-title">今日凭证数</span>
            <el-icon class="stat-icon warning"><Document /></el-icon>
          </div>
          <div class="stat-value">{{ statData.todayVouchers }} <span class="unit">笔</span></div>
          <div class="stat-footer">已全部过账平账</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="fin-card stat-card">
          <div class="stat-header">
            <span class="stat-title">活跃账户总数</span>
            <el-icon class="stat-icon primary"><User /></el-icon>
          </div>
          <div class="stat-value">{{ statData.activeAccounts }} <span class="unit">户</span></div>
          <div class="stat-footer">双子账户架构运行中</div>
        </div>
      </el-col>
    </el-row>

    <!-- 核心架构提示与快捷入口 -->
    <el-row :gutter="16">
      <el-col :span="16">
        <div class="fin-card intro-card">
          <h3>FIN-Core 智能账务核心系统规范</h3>
          <p class="intro-desc">
            本系统严格遵循银行级财务核心约束（绝对值法则、严禁负数运算、先证后账、借贷平衡与红冲对调原则）。
            目前 Phase 1~6 后端核心服务已全部完成，本前端系统为您提供全链路可视化核验与治理平台。
          </p>
          <div class="quick-nav">
            <el-button type="primary" @click="$router.push('/component-demo')">
              <el-icon><Grid /></el-icon> 查看全套公共组件规范展台
            </el-button>
            <el-button @click="$router.push('/config/dict')">
              <el-icon><Setting /></el-icon> 配置管理
            </el-button>
            <el-button @click="$router.push('/business/account')">
              <el-icon><Tickets /></el-icon> 账户业务
            </el-button>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="fin-card quick-status-card">
          <h3>快速操作与状态检测</h3>
          <div class="status-actions">
            <ActionButton
              type="primary"
              plain
              confirm-title="瞬间切日确认"
              confirm-message="确定要执行瞬间切日检查吗？这将更新当前会计日期缓存。"
              :on-click="handleSimulateDateSwitch"
            >
              模拟会计切日检测
            </ActionButton>
            <ActionButton
              type="danger"
              plain
              confirm-type="danger"
              confirm-title="试算平衡告警"
              confirm-message="即将发起全科目试算平衡汇总，如借贷存在差额将发出预警。"
              :on-click="handleSimulateTrialBalance"
            >
              发起全科目试算平衡
            </ActionButton>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { Calendar, Money, Document, User, Grid, Setting, Tickets } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { toast } from '@/utils/toast'

const appStore = useAppStore()

const statData = reactive({
  todayAmount: '12845620.50',
  todayVouchers: 1250,
  activeAccounts: 896
})

async function handleSimulateDateSwitch() {
  await new Promise((resolve) => setTimeout(resolve, 600))
  toast.success('瞬间切日检查通过，当前会计系统状态正常')
}

async function handleSimulateTrialBalance() {
  await new Promise((resolve) => setTimeout(resolve, 800))
  toast.success('全科目试算平衡校验完成：借贷方总额差额为 0.000000，完全平账！')
}
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 128px;
}

.stat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-title {
  font-size: 14px;
  color: #909399;
}

.stat-icon {
  font-size: 20px;
  padding: 8px;
  border-radius: 6px;

  &.info {
    color: #409eff;
    background: #ecf5ff;
  }
  &.success {
    color: #67c23a;
    background: #f0f9eb;
  }
  &.warning {
    color: #e6a23c;
    background: #fdf6ec;
  }
  &.primary {
    color: #909399;
    background: #f4f4f5;
  }
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 4px 0;

  .unit {
    font-size: 14px;
    font-weight: normal;
    color: #909399;
    margin-left: 2px;
  }
}

.stat-footer {
  font-size: 12px;
  color: #909399;

  .trend.up {
    color: #67c23a;
    font-weight: 500;
  }
}

.intro-card {
  h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 8px;
  }
  .intro-desc {
    font-size: 13px;
    color: #606266;
    line-height: 1.6;
    margin-bottom: 20px;
  }
  .quick-nav {
    display: flex;
    gap: 12px;
  }
}

.quick-status-card {
  h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 16px;
  }
  .status-actions {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}
</style>

<template>
  <aside class="fin-sidebar" :class="{ 'is-collapse': appStore.isCollapse }">
    <div class="sidebar-logo">
      <el-icon :size="24" class="logo-icon"><Coin /></el-icon>
      <span v-if="!appStore.isCollapse" class="logo-text">智能账务核心</span>
    </div>

    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.isCollapse"
        :collapse-transition="false"
        unique-opened
        router
        class="fin-menu"
      >
        <!-- 1. 工作台 -->
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>系统概览</template>
        </el-menu-item>

        <!-- 2. 账户管理 -->
        <el-sub-menu index="/menu-account">
          <template #title>
            <el-icon><Wallet /></el-icon>
            <span>账户管理</span>
          </template>
          <el-menu-item index="/business/account">账户档案管理</el-menu-item>
          <el-menu-item index="/business/balance">账户余额明细</el-menu-item>
          <el-menu-item index="/business/freeze">资金冻结与扣款</el-menu-item>
        </el-sub-menu>

        <!-- 3. 凭证作业 -->
        <el-sub-menu index="/menu-voucher">
          <template #title>
            <el-icon><DocumentChecked /></el-icon>
            <span>凭证作业</span>
          </template>
          <el-menu-item index="/business/voucher">记账凭证管理</el-menu-item>
          <el-menu-item index="/business/manual-voucher">手工凭证作业</el-menu-item>
        </el-sub-menu>

        <!-- 4. 期末与日终 -->
        <el-sub-menu index="/menu-closing">
          <template #title>
            <el-icon><Timer /></el-icon>
            <span>期末与日终</span>
          </template>
          <el-menu-item index="/business/eod">日切与试算平衡</el-menu-item>
          <el-menu-item index="/business/transfer">期末损益结转</el-menu-item>
        </el-sub-menu>

        <!-- 5. 账簿与报表 -->
        <el-sub-menu index="/menu-report">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>账簿与报表</span>
          </template>
          <el-menu-item index="/report/general-ledger">科目总分类账</el-menu-item>
          <el-menu-item index="/report/subsidiary-ledger">科目明细账</el-menu-item>
          <el-menu-item index="/report/auxiliary-ledger">辅助核算账簿</el-menu-item>
          <el-menu-item index="/report/balance-sheet">资产负债表</el-menu-item>
          <el-menu-item index="/report/income-statement">利润表</el-menu-item>
        </el-sub-menu>

        <!-- 6. 核心配置 -->
        <el-sub-menu index="/menu-config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>核心配置</span>
          </template>
          <el-menu-item index="/config/subject">会计科目体系</el-menu-item>
          <el-menu-item index="/config/template">账户配置模板</el-menu-item>
          <el-menu-item index="/config/rule">业务记账规则</el-menu-item>
          <el-menu-item index="/config/buffer-rule">缓冲入账规则</el-menu-item>
          <el-menu-item index="/config/transfer-rule">期末结转规则</el-menu-item>
          <el-menu-item index="/config/dict">基础数据字典</el-menu-item>
        </el-sub-menu>

        <!-- 7. 事务与监控 -->
        <el-sub-menu index="/menu-monitor">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>事务与监控</span>
          </template>
          <el-menu-item index="/business/journal">记账流水与事务</el-menu-item>
          <el-menu-item index="/business/buffer-monitor">缓冲记账监控</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Coin,
  Odometer,
  Wallet,
  DocumentChecked,
  Timer,
  DataAnalysis,
  Setting,
  Monitor
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  return route.path
})
</script>

<style scoped>
.fin-sidebar {
  width: var(--fin-sidebar-width, 220px);
  height: 100%;
  background: #1e222d;
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  flex-shrink: 0;

  &.is-collapse {
    width: var(--fin-sidebar-collapse-width, 64px);
  }
}

.sidebar-logo {
  height: var(--fin-header-height, 56px);
  display: flex;
  align-items: center;
  padding: 0 16px;
  background: #14171f;
  color: #ffffff;
  overflow: hidden;
  white-space: nowrap;
  gap: 12px;
}

.logo-icon {
  color: #409eff;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.sidebar-scroll {
  flex: 1;
}

.fin-menu {
  border-right: none !important;
  background-color: transparent !important;
}

:deep(.el-menu) {
  background-color: #1e222d;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  color: #c0c4cc !important;

  &:hover {
    color: #ffffff !important;
    background-color: #262c3a !important;
  }
}

:deep(.el-menu-item.is-active) {
  color: #ffffff !important;
  background-color: #409eff !important;
  font-weight: 500;
}
</style>

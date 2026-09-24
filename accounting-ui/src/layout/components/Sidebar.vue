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
        <!-- 仪表盘 -->
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>系统概览</template>
        </el-menu-item>

        <!-- 公共组件规范展台 -->
        <el-menu-item index="/component-demo">
          <el-icon><Grid /></el-icon>
          <template #title>公共组件规范</template>
        </el-menu-item>

        <!-- 配置管理 (Step 22) -->
        <el-sub-menu index="/config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>配置管理</span>
          </template>
          <el-menu-item index="/config/dict">数据字典</el-menu-item>
          <el-menu-item index="/config/subject">科目树管理</el-menu-item>
          <el-menu-item index="/config/template">开户模板</el-menu-item>
          <el-menu-item index="/config/rule">记账规则</el-menu-item>
          <el-menu-item index="/config/buffer-rule">缓冲入账规则</el-menu-item>
        </el-sub-menu>

        <!-- 业务管理 (Step 23) -->
        <el-sub-menu index="/business">
          <template #title>
            <el-icon><Tickets /></el-icon>
            <span>账务业务</span>
          </template>
          <el-menu-item index="/business/account">账户开户与状态</el-menu-item>
          <el-menu-item index="/business/balance">余额与明细查询</el-menu-item>
          <el-menu-item index="/business/freeze">资金冻结与扣款</el-menu-item>
          <el-menu-item index="/business/voucher">记账凭证管理</el-menu-item>
          <el-menu-item index="/business/eod">日切与试算平衡</el-menu-item>
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
  Grid,
  Setting,
  Tickets
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

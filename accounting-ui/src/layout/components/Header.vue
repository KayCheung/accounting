<template>
  <header class="fin-header">
    <div class="header-left">
      <el-button
        link
        :icon="appStore.isCollapse ? Expand : Fold"
        class="collapse-btn"
        @click="appStore.toggleSidebar"
      />
      <Breadcrumb />
    </div>

    <div class="header-right">
      <!-- 会计日期与日切状态标识 -->
      <div class="accounting-badge">
        <el-tag type="success" effect="light" class="date-tag">
          <el-icon><Calendar /></el-icon>
          会计日期：{{ appStore.accountingDate }}
        </el-tag>
        <el-tag :type="appStore.eodStatus === 'NORMAL' ? 'info' : 'warning'" effect="plain">
          {{ appStore.eodStatus === 'NORMAL' ? '正常营业' : '日切中' }}
        </el-tag>
      </div>

      <!-- 用户信息与快捷操作 -->
      <el-dropdown trigger="click">
        <div class="user-profile">
          <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
          <span class="username">账务管理员</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="refreshStatus">
              <el-icon><Refresh /></el-icon>刷新日切状态
            </el-dropdown-item>
            <el-dropdown-item divided>
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import {
  Fold,
  Expand,
  Calendar,
  Refresh,
  SwitchButton
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { toast } from '@/utils/toast'
import Breadcrumb from './Breadcrumb.vue'

const appStore = useAppStore()

onMounted(() => {
  appStore.fetchSystemStatus()
})

async function refreshStatus() {
  await appStore.fetchSystemStatus()
  toast.success('已刷新最新会计日期与状态')
}
</script>

<style scoped>
.fin-header {
  height: var(--fin-header-height, 56px);
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  color: #606266;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.accounting-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #303133;
}
</style>

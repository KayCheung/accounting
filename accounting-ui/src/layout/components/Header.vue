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
          会计日期：{{ appStore.accountingDate || '获取中...' }}
        </el-tag>
        <el-tag :type="appStore.eodStatus === 'NORMAL' ? 'info' : 'warning'" effect="plain">
          {{ appStore.eodStatus === 'NORMAL' ? '正常营业' : '日切中' }}
        </el-tag>
      </div>

      <!-- 用户信息与角色切换 -->
      <el-dropdown trigger="click">
        <div class="user-profile">
          <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
          <div class="user-meta">
            <span class="username">{{ userStore.currentUsername }}</span>
            <el-tag size="small" :type="userStore.roleInfo.tagType" effect="dark" class="role-badge">
              {{ userStore.currentRoleName }}
            </el-tag>
          </div>
          <el-icon class="arrow-down"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <div class="role-switch-header">
              <el-icon><User /></el-icon>
              <span>切换当前操作岗位 (RBAC)</span>
            </div>
            <el-dropdown-item
              v-for="role in roleOptions"
              :key="role.code"
              :class="{ 'is-active-role': userStore.currentRole === role.code }"
              @click="handleSwitchRole(role.code, role.name)"
            >
              <div class="role-item">
                <el-tag size="small" :type="role.tagType" effect="plain">{{ role.name }}</el-tag>
                <span class="role-desc">{{ role.dutyDesc.slice(0, 15) }}...</span>
                <el-icon v-if="userStore.currentRole === role.code" class="check-icon"><Check /></el-icon>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided @click="refreshStatus">
              <el-icon><Refresh /></el-icon>刷新系统与会计日期
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onMounted, computed } from 'vue'
import {
  Fold,
  Expand,
  Calendar,
  Refresh,
  User,
  ArrowDown,
  Check
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore, ROLE_CONFIGS, type RoleCode } from '@/stores/user'
import { toast } from '@/utils/toast'
import Breadcrumb from './Breadcrumb.vue'

const appStore = useAppStore()
const userStore = useUserStore()

const roleOptions = computed(() => Object.values(ROLE_CONFIGS))

onMounted(() => {
  appStore.fetchSystemStatus()
})

async function refreshStatus() {
  await appStore.fetchSystemStatus()
  toast.success('已刷新最新会计日期与营业状态')
}

function handleSwitchRole(code: RoleCode, name: string) {
  userStore.switchRole(code)
  toast.success(`已切换为操作岗位：${name}`)
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
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.username {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.role-badge {
  font-size: 11px;
  height: 20px;
  line-height: 18px;
  padding: 0 6px;
}

.arrow-down {
  font-size: 12px;
  color: #909399;
}

.role-switch-header {
  padding: 8px 16px;
  font-size: 12px;
  color: #909399;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 260px;
  padding: 2px 0;
}

.role-desc {
  font-size: 12px;
  color: #909399;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-icon {
  color: #409eff;
  font-weight: bold;
}

.is-active-role {
  background-color: #ecf5ff;
}
</style>

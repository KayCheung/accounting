<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="440px"
    :close-on-click-modal="false"
    class="fin-confirm-dialog"
  >
    <div class="confirm-body">
      <div class="confirm-icon-wrapper" :class="type">
        <el-icon :size="28">
          <WarningFilled v-if="type === 'warning'" />
          <CircleCloseFilled v-else-if="type === 'danger'" />
          <InfoFilled v-else />
        </el-icon>
      </div>
      <div class="confirm-content">
        <div class="confirm-message">{{ message }}</div>
        <div v-if="description" class="confirm-desc">{{ description }}</div>
      </div>
    </div>

    <template #footer>
      <div class="confirm-footer">
        <el-button :disabled="loading" @click="handleCancel">
          {{ cancelText }}
        </el-button>
        <el-button
          :type="type === 'danger' ? 'danger' : 'primary'"
          :loading="loading"
          @click="handleConfirm"
        >
          {{ confirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'

interface Props {
  modelValue: boolean
  title?: string
  message: string
  description?: string
  type?: 'warning' | 'danger' | 'info'
  confirmText?: string
  cancelText?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '操作确认',
  description: '',
  type: 'warning',
  confirmText: '确定',
  cancelText: '取消',
  loading: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
  dialogVisible.value = false
}
</script>

<style scoped>
.confirm-body {
  display: flex;
  align-items: flex-start;
  padding: 12px 4px 8px;
  gap: 16px;
}

.confirm-icon-wrapper {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &.warning {
    color: var(--el-color-warning, #e6a23c);
  }
  &.danger {
    color: var(--el-color-danger, #f56c6c);
  }
  &.info {
    color: var(--el-color-primary, #409eff);
  }
}

.confirm-content {
  flex: 1;
}

.confirm-message {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  line-height: 1.5;
}

.confirm-desc {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
  line-height: 1.4;
}

.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

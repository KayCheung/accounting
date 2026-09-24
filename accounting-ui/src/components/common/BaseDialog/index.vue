<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :fullscreen="isFullscreen"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    :append-to-body="appendToBody"
    class="fin-base-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ title }}</span>
        <div class="dialog-tools">
          <el-button
            link
            :icon="isFullscreen ? CopyDocument : FullScreen"
            class="tool-btn"
            @click="toggleFullscreen"
          />
        </div>
      </div>
    </template>

    <div v-loading="loading" class="dialog-body">
      <slot></slot>
    </div>

    <template #footer>
      <slot name="footer">
        <div class="dialog-footer">
          <el-button :disabled="confirmLoading" @click="handleCancel">
            {{ cancelText }}
          </el-button>
          <el-button
            type="primary"
            :loading="confirmLoading"
            @click="handleConfirm"
          >
            {{ confirmText }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { FullScreen, CopyDocument } from '@element-plus/icons-vue'

interface Props {
  modelValue: boolean
  title?: string
  width?: string
  loading?: boolean
  confirmLoading?: boolean
  confirmText?: string
  cancelText?: string
  closeOnClickModal?: boolean
  destroyOnClose?: boolean
  appendToBody?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '系统提示',
  width: '600px',
  loading: false,
  confirmLoading: false,
  confirmText: '确定',
  cancelText: '取消',
  closeOnClickModal: false,
  destroyOnClose: true,
  appendToBody: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'close'): void
}>()

const isFullscreen = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
  visible.value = false
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 28px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.dialog-tools {
  display: flex;
  align-items: center;
}

.tool-btn {
  color: #909399;
  font-size: 14px;
}

.dialog-body {
  max-height: 70vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 16px;
  box-sizing: border-box;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

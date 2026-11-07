<template>
  <el-button
    :type="finalType"
    :size="size"
    :plain="plain"
    :round="round"
    :disabled="disabled"
    :loading="isLoading"
    :icon="icon"
    class="fin-action-button"
    @click="handleClick"
  >
    <slot></slot>
  </el-button>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useConfirm } from '@/hooks/useConfirm'

interface Props {
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default' | ''
  size?: 'large' | 'default' | 'small'
  plain?: boolean
  round?: boolean
  disabled?: boolean
  loading?: boolean
  icon?: any
  danger?: boolean
  confirmMessage?: string
  confirmTitle?: string
  confirmType?: 'warning' | 'danger' | 'info'
  onClick?: () => Promise<any> | any
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  size: 'default',
  plain: false,
  round: false,
  disabled: false,
  loading: false,
  danger: false,
  confirmMessage: '',
  confirmTitle: '操作确认',
  confirmType: 'warning'
})

const emit = defineEmits<{
  (e: 'click'): void
}>()

const internalLoading = ref(false)

const isLoading = computed(() => props.loading || internalLoading.value)

const finalType = computed(() => {
  if (props.danger) return 'danger'
  return props.type || 'default'
})

async function handleClick() {
  if (isLoading.value || props.disabled) return

  // 二次确认拦截
  if (props.confirmMessage) {
    const ok = await useConfirm(props.confirmMessage, {
      title: props.confirmTitle,
      type: props.confirmType === 'danger' ? 'error' : props.confirmType
    })
    if (!ok) return
  }

  if (props.onClick) {
    try {
      internalLoading.value = true
      await props.onClick()
    } finally {
      internalLoading.value = false
    }
  }

  emit('click')
}
</script>

<style scoped>
.fin-action-button {
  font-weight: 500;
}
</style>

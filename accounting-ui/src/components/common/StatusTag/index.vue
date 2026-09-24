<template>
  <el-tag :type="tagType" :size="size" :effect="effect" class="fin-status-tag">
    {{ displayLabel }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'

interface Props {
  status: number | string | null | undefined
  label?: string
  dictType?: string
  size?: 'large' | 'default' | 'small'
  effect?: 'light' | 'dark' | 'plain'
  typeMap?: Record<string | number, 'success' | 'warning' | 'info' | 'danger' | ''>
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  dictType: '',
  size: 'default',
  effect: 'light'
})

const dictStore = useDictStore()

// 默认状态颜色映射
const defaultTypeMap: Record<string | number, 'success' | 'warning' | 'info' | 'danger' | ''> = {
  1: 'info',     // 待处理 / 草稿 / 待启用
  2: 'warning',  // 处理中 / 过账中 / 审核中
  3: 'success',  // 成功 / 已过账 / 正常 / 启用
  4: 'danger',   // 失败 / 停用 / 冻结 / 异常
  5: 'info',     // 已冲销 / 已结转
  0: 'danger',   // 停用 / 禁用
  NORMAL: 'success',
  FROZEN: 'danger',
  CANCELLED: 'info',
  REALTIME: 'success',
  ASYNC: 'warning',
  BUFFER: 'info'
}

const tagType = computed(() => {
  if (props.status === null || props.status === undefined) return 'info'
  if (props.typeMap && props.typeMap[props.status] !== undefined) {
    return props.typeMap[props.status]
  }
  return defaultTypeMap[props.status] || ''
})

const displayLabel = computed(() => {
  if (props.label) return props.label
  if (props.dictType && props.status !== null && props.status !== undefined) {
    return dictStore.getDictLabel(props.dictType, props.status)
  }
  return String(props.status ?? '-')
})
</script>

<style scoped>
.fin-status-tag {
  font-weight: 500;
}
</style>

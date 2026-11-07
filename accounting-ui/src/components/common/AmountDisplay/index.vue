<template>
  <span
    class="fin-amount"
    :class="{
      'is-negative': isNegative,
      'is-positive': !isNegative && numValue > 0,
      'is-zero': numValue === 0
    }"
    :style="{ textAlign: align }"
  >
    <span v-if="prefix" class="fin-amount-prefix">{{ prefix }}</span>
    {{ formattedText }}
    <span v-if="suffix" class="fin-amount-suffix">{{ suffix }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatAmount, isNegativeAmount } from '@/utils/amount'

interface Props {
  value: string | number | null | undefined
  decimals?: number
  prefix?: string
  suffix?: string
  align?: 'left' | 'center' | 'right'
}

const props = withDefaults(defineProps<Props>(), {
  decimals: 2,
  prefix: '',
  suffix: '',
  align: 'right'
})

const isNegative = computed(() => isNegativeAmount(props.value))

const numValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return 0
  const n = typeof props.value === 'string' ? parseFloat(props.value) : props.value
  return isNaN(n) ? 0 : n
})

const formattedText = computed(() => {
  return formatAmount(props.value, props.decimals)
})
</script>

<style scoped>
.fin-amount {
  display: inline-block;
  font-family: var(--fin-font-mono, monospace);
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
}

.is-negative {
  color: var(--el-color-danger, #f56c6c) !important;
  font-weight: 500;
}

.is-zero {
  color: #909399;
}

.fin-amount-prefix {
  margin-right: 2px;
  font-size: 0.9em;
}

.fin-amount-suffix {
  margin-left: 2px;
  font-size: 0.9em;
}
</style>

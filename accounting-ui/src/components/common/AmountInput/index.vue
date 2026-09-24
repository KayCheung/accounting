<template>
  <el-input
    v-model="displayValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :size="size"
    class="fin-amount-input"
    @input="handleInput"
    @focus="handleFocus"
    @blur="handleBlur"
  >
    <template v-if="prefix" #prefix>
      <span class="prefix-text">{{ prefix }}</span>
    </template>
    <template v-if="suffix" #suffix>
      <span class="suffix-text">{{ suffix }}</span>
    </template>
  </el-input>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { formatAmount } from '@/utils/amount'

interface Props {
  modelValue?: string | number | null
  decimals?: number
  min?: number
  max?: number
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  size?: 'large' | 'default' | 'small'
  prefix?: string
  suffix?: string
  autoFormatOnBlur?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  decimals: 2,
  min: 0,
  max: 999999999999.99,
  placeholder: '请输入金额',
  disabled: false,
  clearable: true,
  size: 'default',
  prefix: '￥',
  suffix: '',
  autoFormatOnBlur: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'change', val: string): void
}>()

const isFocused = ref(false)
const displayValue = ref('')

// 同步外部 modelValue
watch(
  () => props.modelValue,
  (val) => {
    if (!isFocused.value) {
      if (val !== null && val !== undefined && val !== '') {
        displayValue.value = props.autoFormatOnBlur ? formatAmount(val, props.decimals) : String(val)
      } else {
        displayValue.value = ''
      }
    }
  },
  { immediate: true }
)

function handleInput(raw: string) {
  // 过滤非数字和小数点
  let val = raw.replace(/[^\d.]/g, '')

  // 严禁负数（当 min >= 0 时）
  if (props.min >= 0) {
    val = val.replace(/-/g, '')
  }

  // 限制只能有一个小数点
  const parts = val.split('.')
  if (parts.length > 2) {
    val = parts[0] + '.' + parts.slice(1).join('')
  }

  // 限制小数位长度
  if (parts.length === 2 && parts[1].length > props.decimals) {
    val = parts[0] + '.' + parts[1].substring(0, props.decimals)
  }

  displayValue.value = val
  emit('update:modelValue', val)
}

function handleFocus() {
  isFocused.value = true
  // 聚焦时还原为无千分位的纯数字字符串
  if (props.modelValue !== null && props.modelValue !== undefined && props.modelValue !== '') {
    displayValue.value = String(props.modelValue).replace(/,/g, '')
  }
}

function handleBlur() {
  isFocused.value = false
  let val = displayValue.value.trim()
  if (!val) {
    emit('update:modelValue', '')
    emit('change', '')
    return
  }

  let num = parseFloat(val)
  if (isNaN(num)) {
    displayValue.value = ''
    emit('update:modelValue', '')
    emit('change', '')
    return
  }

  // 边界约束
  if (props.min !== undefined && num < props.min) {
    num = props.min
  }
  if (props.max !== undefined && num > props.max) {
    num = props.max
  }

  const rawFixed = num.toFixed(props.decimals)
  emit('update:modelValue', rawFixed)
  emit('change', rawFixed)

  if (props.autoFormatOnBlur) {
    displayValue.value = formatAmount(rawFixed, props.decimals)
  } else {
    displayValue.value = rawFixed
  }
}
</script>

<style scoped>
.fin-amount-input :deep(.el-input__inner) {
  font-family: var(--fin-font-mono, monospace);
  text-align: right;
}

.prefix-text {
  color: #909399;
  font-size: 13px;
}

.suffix-text {
  color: #909399;
  font-size: 13px;
}
</style>

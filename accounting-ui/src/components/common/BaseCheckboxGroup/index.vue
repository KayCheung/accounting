<template>
  <div class="fin-checkbox-group">
    <div v-if="showCheckAll" class="check-all-wrapper">
      <el-checkbox
        v-model="checkAll"
        :indeterminate="isIndeterminate"
        :disabled="disabled"
        @change="handleCheckAllChange"
      >
        全选
      </el-checkbox>
    </div>
    <el-checkbox-group
      v-model="checkedValues"
      :disabled="disabled"
      :size="size"
      @change="handleGroupChange"
    >
      <el-checkbox
        v-for="item in options"
        :key="item.value"
        :label="item.value"
        :disabled="item.disabled || disabled"
      >
        {{ item.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SelectOption } from '@/api/types'

interface Props {
  modelValue?: any[]
  options: SelectOption[]
  showCheckAll?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  options: () => [],
  showCheckAll: false,
  disabled: false,
  size: 'default'
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
  (e: 'change', val: any[]): void
}>()

const checkedValues = computed({
  get: () => props.modelValue || [],
  set: (val) => emit('update:modelValue', val)
})

const allValues = computed(() => props.options.map((opt) => opt.value))

const checkAll = computed({
  get: () => {
    return allValues.value.length > 0 && checkedValues.value.length === allValues.value.length
  },
  set: (val) => {
    handleCheckAllChange(val)
  }
})

const isIndeterminate = computed(() => {
  const len = checkedValues.value.length
  return len > 0 && len < allValues.value.length
})

function handleCheckAllChange(val: boolean) {
  const next = val ? [...allValues.value] : []
  emit('update:modelValue', next)
  emit('change', next)
}

function handleGroupChange(val: any[]) {
  emit('change', val)
}
</script>

<style scoped>
.fin-checkbox-group {
  display: inline-block;
}

.check-all-wrapper {
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed #ebeef5;
}
</style>

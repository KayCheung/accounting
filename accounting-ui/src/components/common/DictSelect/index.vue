<template>
  <el-select
    v-model="selectedValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    :multiple="multiple"
    :size="size"
    :loading="loading"
    class="fin-dict-select"
    @change="handleChange"
  >
    <el-option
      v-for="item in finalOptions"
      :key="item.value"
      :label="item.label"
      :value="item.value"
      :disabled="item.disabled"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useDictStore } from '@/stores/dict'
import type { SelectOption } from '@/api/types'

interface Props {
  modelValue?: any
  dictType?: string
  options?: SelectOption[]
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  filterable?: boolean
  multiple?: boolean
  size?: 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  dictType: '',
  options: () => [],
  placeholder: '请选择',
  disabled: false,
  clearable: true,
  filterable: true,
  multiple: false,
  size: 'default'
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: any): void
  (e: 'change', val: any, option?: SelectOption): void
}>()

const dictStore = useDictStore()
const loading = ref(false)
const remoteOptions = ref<SelectOption[]>([])

const selectedValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const finalOptions = computed(() => {
  if (props.options && props.options.length > 0) {
    return props.options
  }
  return remoteOptions.value
})

async function fetchDict() {
  if (!props.dictType) return
  loading.value = true
  try {
    remoteOptions.value = await dictStore.getOptions(props.dictType)
  } catch {
    remoteOptions.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.dictType,
  (newVal) => {
    if (newVal) fetchDict()
  }
)

onMounted(() => {
  fetchDict()
})

function handleChange(val: any) {
  const selectedOpt = finalOptions.value.find((opt) => opt.value === val)
  emit('change', val, selectedOpt)
}
</script>

<style scoped>
.fin-dict-select {
  width: 100%;
}
</style>

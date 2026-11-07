<template>
  <div v-if="total > 0" class="fin-pagination-wrapper">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      background
      class="fin-pagination"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  total: number
  pageNo: number
  pageSize: number
  pageSizes?: number[]
  layout?: string
}

const props = withDefaults(defineProps<Props>(), {
  pageSizes: () => [10, 20, 50, 100],
  layout: 'total, sizes, prev, pager, next, jumper'
})

const emit = defineEmits<{
  (e: 'update:pageNo', val: number): void
  (e: 'update:pageSize', val: number): void
  (e: 'change', page: number, size: number): void
}>()

const currentPage = computed({
  get: () => props.pageNo,
  set: (val) => emit('update:pageNo', val)
})

const currentPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:pageSize', val)
})

function handleSizeChange(val: number) {
  emit('change', 1, val)
}

function handleCurrentChange(val: number) {
  emit('change', val, props.pageSize)
}
</script>

<style scoped>
.fin-pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 8px;
}
</style>

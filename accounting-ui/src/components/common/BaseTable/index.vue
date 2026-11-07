<template>
  <div class="fin-base-table-container">
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="data"
      :stripe="stripe"
      :border="border"
      :size="size"
      :row-key="rowKey"
      :lazy="lazy"
      :load="load"
      :tree-props="treeProps"
      class="fin-table"
      style="width: 100%"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <template #empty>
        <el-empty :description="emptyText" :image-size="100" />
      </template>

      <!-- 默认表格列插槽 -->
      <slot></slot>
    </el-table>

    <!-- 分页器集成 -->
    <BasePagination
      v-if="showPagination && total !== undefined && pageNo !== undefined && pageSize !== undefined"
      :total="total"
      :page-no="pageNo"
      :page-size="pageSize"
      @update:page-no="(val) => $emit('update:pageNo', val)"
      @update:pageSize="(val) => $emit('update:pageSize', val)"
      @change="(p, s) => $emit('pageChange', p, s)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BasePagination from '@/components/common/BasePagination/index.vue'

interface Props {
  data: any[]
  loading?: boolean
  stripe?: boolean
  border?: boolean
  size?: 'large' | 'default' | 'small'
  rowKey?: string | ((row: any) => string)
  emptyText?: string
  total?: number
  pageNo?: number
  pageSize?: number
  showPagination?: boolean
  lazy?: boolean
  load?: (row: any, treeNode: unknown, resolve: (data: any[]) => void) => void
  treeProps?: { hasChildren?: string; children?: string }
}

withDefaults(defineProps<Props>(), {
  loading: false,
  stripe: true,
  border: true,
  size: 'default',
  emptyText: '暂无账务数据',
  showPagination: true,
  lazy: false,
  treeProps: () => ({ hasChildren: 'hasChildren', children: 'children' })
})

const emit = defineEmits<{
  (e: 'selectionChange', selection: any[]): void
  (e: 'sortChange', data: { column: any; prop: string; order: string }): void
  (e: 'update:pageNo', val: number): void
  (e: 'update:pageSize', val: number): void
  (e: 'pageChange', page: number, size: number): void
}>()

const tableRef = ref<any>()

function handleSelectionChange(selection: any[]) {
  emit('selectionChange', selection)
}

function handleSortChange(data: any) {
  emit('sortChange', data)
}

// 暴露底层 el-table 实例方法
defineExpose({
  tableRef,
  clearSelection: () => tableRef.value?.clearSelection(),
  toggleRowSelection: (row: any, selected?: boolean) => tableRef.value?.toggleRowSelection(row, selected)
})
</script>

<style scoped>
.fin-base-table-container {
  width: 100%;
}
</style>

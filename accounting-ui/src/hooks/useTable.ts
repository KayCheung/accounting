import { ref } from 'vue'
import type { PageResponse } from '@/api/types'

export interface UseTableOptions<T, P extends Record<string, any>> {
  fetchApi: (params: P & { pageNo: number; pageSize: number }) => Promise<PageResponse<T>>
  defaultParams?: Partial<P>
  defaultPageSize?: number
  immediate?: boolean
}

/**
 * 分页表格通用数据流 Hook
 */
export function useTable<T = any, P extends Record<string, any> = Record<string, any>>(
  options: UseTableOptions<T, P>
) {
  const { fetchApi, defaultParams = {}, defaultPageSize = 10, immediate = true } = options

  const loading = ref(false)
  const dataList = ref<T[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(defaultPageSize)

  const searchParams = ref<P>({ ...defaultParams } as P)

  /**
   * 加载数据
   */
  async function loadData() {
    loading.value = true
    try {
      const res = await fetchApi({
        ...searchParams.value,
        pageNo: pageNo.value,
        pageSize: pageSize.value
      })
      const items = res.list || res.records || []
      dataList.value = items
      total.value = res.total || 0
      if (res.current !== undefined) {
        pageNo.value = Number(res.current)
      }
    } catch {
      dataList.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 搜索（重置为第 1 页）
   */
  function handleSearch() {
    pageNo.value = 1
    return loadData()
  }

  /**
   * 重置搜索条件
   */
  function handleReset() {
    searchParams.value = { ...defaultParams } as P
    pageNo.value = 1
    return loadData()
  }

  /**
   * 页码切换
   */
  function handlePageChange(val: number) {
    pageNo.value = val
    return loadData()
  }

  /**
   * 每页条数切换
   */
  function handleSizeChange(val: number) {
    pageSize.value = val
    pageNo.value = 1
    return loadData()
  }

  if (immediate) {
    loadData()
  }

  return {
    loading,
    dataList,
    total,
    pageNo,
    pageSize,
    searchParams,
    loadData,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange
  }
}

export default useTable

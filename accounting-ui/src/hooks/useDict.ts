import { ref, onMounted } from 'vue'
import { useDictStore } from '@/stores/dict'
import type { SelectOption } from '@/api/types'

/**
 * 字典项使用 Hook
 *
 * @param dictType 字典类型
 */
export function useDict(dictType: string) {
  const dictStore = useDictStore()
  const options = ref<SelectOption[]>([])
  const loading = ref(false)

  async function fetchOptions() {
    loading.value = true
    try {
      options.value = await dictStore.getOptions(dictType)
    } catch {
      options.value = []
    } finally {
      loading.value = false
    }
  }

  function getLabel(dictCode: string | number): string {
    return dictStore.getDictLabel(dictType, dictCode)
  }

  onMounted(() => {
    fetchOptions()
  })

  return {
    options,
    loading,
    getLabel,
    refresh: fetchOptions
  }
}

export default useDict

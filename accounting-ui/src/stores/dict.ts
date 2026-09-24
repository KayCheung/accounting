import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDictByType, type DictItem } from '@/api/dict'
import type { SelectOption } from '@/api/types'

export const useDictStore = defineStore('dict', () => {
  // 字典缓存：key 为 dictType
  const dictCache = ref<Record<string, DictItem[]>>({})
  // 正在请求中的 Promise 缓存，防止并发重复请求
  const pendingRequests = new Map<string, Promise<DictItem[]>>()

  /**
   * 加载指定类型的字典项
   */
  async function loadDict(dictType: string): Promise<DictItem[]> {
    if (dictCache.value[dictType]) {
      return dictCache.value[dictType]
    }
    if (pendingRequests.has(dictType)) {
      return pendingRequests.get(dictType)!
    }

    const promise = getDictByType(dictType)
      .then((items) => {
        dictCache.value[dictType] = items || []
        pendingRequests.delete(dictType)
        return dictCache.value[dictType]
      })
      .catch((err) => {
        pendingRequests.delete(dictType)
        throw err
      })

    pendingRequests.set(dictType, promise)
    return promise
  }

  /**
   * 获取下拉 options 格式
   */
  async function getOptions(dictType: string): Promise<SelectOption[]> {
    const list = await loadDict(dictType)
    return list.map((item) => ({
      label: item.dictName,
      value: item.dictCode
    }))
  }

  /**
   * 根据 dictType 和 code 获取字典名称
   */
  function getDictLabel(dictType: string, dictCode: string | number): string {
    const list = dictCache.value[dictType]
    if (!list) return String(dictCode ?? '')
    const target = list.find((item) => item.dictCode === String(dictCode))
    return target ? target.dictName : String(dictCode ?? '')
  }

  /**
   * 清空本地字典缓存
   */
  function clearCache(dictType?: string) {
    if (dictType) {
      delete dictCache.value[dictType]
    } else {
      dictCache.value = {}
    }
  }

  return {
    dictCache,
    loadDict,
    getOptions,
    getDictLabel,
    clearCache
  }
})

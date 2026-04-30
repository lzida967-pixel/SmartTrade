import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'

/**
 * 自选股 store
 * 用 Set 在内存里维护，方便 O(1) 判断是否已收藏。
 */
export const useWatchlistStore = defineStore('watchlist', () => {
  const codes = ref(new Set())
  const loaded = ref(false)
  const loading = ref(false)

  const has = (code) => codes.value.has(code)
  const size = computed(() => codes.value.size)
  const list = computed(() => [...codes.value])

  /** 拉一次后端的列表，缓存到 store。force=true 时绕过缓存 */
  const refresh = async (force = false) => {
    if (loaded.value && !force) return
    if (loading.value) return
    loading.value = true
    try {
      const res = await request.get('/watchlist/codes')
      if (res.code === 200) {
        codes.value = new Set(res.data || [])
        loaded.value = true
      }
    } catch (_) {
      /* ignore */
    } finally {
      loading.value = false
    }
  }

  const add = async (code) => {
    if (!code || codes.value.has(code)) return false
    try {
      const res = await request.post(`/watchlist/${code}`)
      if (res.code === 200) {
        codes.value.add(code)
        // 触发响应式更新（Set 直接 mutate 不能被 Vue 追踪到长度变化的所有场景）
        codes.value = new Set(codes.value)
        return true
      }
    } catch (_) { /* ignore */ }
    return false
  }

  const remove = async (code) => {
    if (!code || !codes.value.has(code)) return false
    try {
      const res = await request.delete(`/watchlist/${code}`)
      if (res.code === 200) {
        codes.value.delete(code)
        codes.value = new Set(codes.value)
        return true
      }
    } catch (_) { /* ignore */ }
    return false
  }

  /** 切换收藏状态：返回切换后的状态（true=已收藏） */
  const toggle = async (code) => {
    if (codes.value.has(code)) {
      await remove(code)
      return false
    } else {
      await add(code)
      return true
    }
  }

  const reset = () => {
    codes.value = new Set()
    loaded.value = false
  }

  return { codes, loaded, loading, size, list, has, refresh, add, remove, toggle, reset }
})

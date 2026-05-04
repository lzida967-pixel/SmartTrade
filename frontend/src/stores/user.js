import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户认证状态。
 *
 * Token 持久化策略：
 *   - persist=true  → localStorage（跨浏览器会话保留，关掉浏览器再开仍登录）
 *   - persist=false → sessionStorage（仅当前标签页有效，关掉标签即失效）
 *
 * 初始化时优先读 sessionStorage —— 这样「不勾选保存会话」的临时登录优先生效。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem('token') || localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const setToken = (newToken, persist = true) => {
    token.value = newToken
    if (persist) {
      localStorage.setItem('token', newToken)
      // 如果之前曾有会话级 token，写入 localStorage 后也清掉，避免混淆
      sessionStorage.removeItem('token')
    } else {
      sessionStorage.setItem('token', newToken)
      // 不勾选保存会话时，清掉持久化 token，避免下次自动登录
      localStorage.removeItem('token')
    }
  }

  const setUserInfo = (info) => {
    userInfo.value = info
  }

  const clearAuth = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
  }

  return { token, userInfo, setToken, setUserInfo, clearAuth }
})
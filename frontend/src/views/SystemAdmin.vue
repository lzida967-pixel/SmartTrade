<template>
  <div class="min-h-screen bg-[#0a0a0f] text-gray-200 flex">
    <!-- Decorative background -->
    <div class="fixed top-0 right-0 w-[500px] h-[500px] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>
    <div class="fixed bottom-0 left-0 w-[500px] h-[500px] bg-blue-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

    <!-- 左侧菜单 -->
    <aside class="w-64 bg-[#0c0c14]/80 backdrop-blur-md border-r border-white/5 flex flex-col relative z-10">
      <div class="h-16 flex items-center gap-2 px-5 border-b border-white/5">
        <el-icon class="text-purple-400 text-2xl"><Monitor /></el-icon>
        <div class="leading-tight">
          <div class="text-base font-bold bg-clip-text text-transparent bg-gradient-to-r from-purple-400 to-blue-400">
            Admin Console
          </div>
          <div class="text-[10px] text-gray-600 font-mono tracking-widest">SmartTrade · Master</div>
        </div>
      </div>

      <nav class="flex-1 py-4">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-3 mx-3 my-1 px-4 py-2.5 rounded-lg text-sm text-gray-400 hover:bg-white/5 hover:text-gray-100 transition-colors"
          active-class="!bg-blue-500/10 !text-blue-300 !border !border-blue-500/30"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="border-t border-white/5 p-3 space-y-1">
        <a class="flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm text-gray-400 hover:bg-white/5 hover:text-blue-300 cursor-pointer transition-colors"
           @click="goMain">
          <el-icon><Back /></el-icon><span>返回主站</span>
        </a>
        <a class="flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm text-gray-400 hover:bg-red-500/10 hover:text-red-300 cursor-pointer transition-colors"
           @click="logout">
          <el-icon><SwitchButton /></el-icon><span>退出登录</span>
        </a>
      </div>
    </aside>

    <!-- 主内容 -->
    <main class="flex-1 relative z-10 overflow-auto">
      <header class="sticky top-0 z-20 h-14 bg-[#0a0a0f]/80 backdrop-blur-md border-b border-white/5 flex items-center justify-between px-6">
        <div class="text-sm text-gray-400 font-mono tracking-widest flex items-center gap-2">
          <span class="w-2 h-2 rounded-full bg-green-500 shadow-[0_0_8px_#22c55e] animate-pulse"></span>
          CORE_ENGINE / NODE: MASTER-01
        </div>
        <div class="text-xs text-gray-500 font-mono">
          {{ userStore.userInfo?.nickname || userStore.userInfo?.username }} · {{ userStore.userInfo?.role || 'USER' }}
        </div>
      </header>

      <div class="p-6">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import {
  Monitor, User, Coin, List, Back, SwitchButton
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/admin/users', label: '用户管理', icon: User },
  { path: '/admin/stocks', label: '股票池管理', icon: Coin },
  { path: '/admin/orders', label: '订单巡检', icon: List }
]

const goMain = () => {
  // 同窗口返回主站
  router.push('/')
}

const logout = () => {
  userStore.clearAuth()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style>
/* 通用暗黑表格样式（admin 子页面共用） */
.admin-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(24, 24, 27, 0.4);
  --el-table-header-text-color: #a1a1aa;
  --el-table-text-color: #d4d4d8;
  --el-table-row-hover-bg-color: rgba(59, 130, 246, 0.08);
  --el-table-border-color: rgba(255, 255, 255, 0.04);
  background-color: transparent !important;
}
.admin-table th.el-table__cell {
  background-color: var(--el-table-header-bg-color) !important;
  border-bottom: 1px solid var(--el-table-border-color) !important;
  font-size: 12px;
  letter-spacing: 1px;
  font-weight: 500;
}
.admin-table td.el-table__cell {
  border-bottom: 1px solid var(--el-table-border-color) !important;
  background-color: transparent !important;
}
.admin-table .el-table__body tr:hover > td.el-table__cell {
  background-color: var(--el-table-row-hover-bg-color) !important;
}
.admin-table::before {
  display: none !important;
}

/* 卡片样式 */
.admin-card {
  background-color: rgba(24, 24, 27, 0.5);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  overflow: hidden;
}
</style>

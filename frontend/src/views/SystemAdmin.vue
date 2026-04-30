<template>
  <div class="admin-root min-h-screen bg-[#07080d] text-gray-200 flex">
    <!-- Decorative background -->
    <div class="fixed top-[-200px] right-[-200px] w-[600px] h-[600px] bg-indigo-600/10 blur-[140px] rounded-full pointer-events-none z-0"></div>
    <div class="fixed bottom-[-200px] left-[-200px] w-[600px] h-[600px] bg-blue-600/10 blur-[140px] rounded-full pointer-events-none z-0"></div>

    <!-- 左侧菜单 -->
    <aside class="admin-aside w-60 shrink-0 flex flex-col relative z-10 h-screen sticky top-0">
      <!-- Logo -->
      <div class="h-16 flex items-center gap-3 px-5 border-b border-white/5">
        <div class="w-9 h-9 rounded-lg bg-gradient-to-br from-indigo-500 to-blue-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
          <el-icon class="text-white text-lg"><Monitor /></el-icon>
        </div>
        <div class="leading-tight">
          <div class="text-[15px] font-semibold text-gray-100">Admin Console</div>
          <div class="text-[10px] text-gray-500 font-mono tracking-[0.2em] mt-0.5">SMARTTRADE</div>
        </div>
      </div>

      <!-- 主导航 -->
      <nav class="flex-1 overflow-y-auto py-4 px-3 space-y-6">
        <div>
          <div class="px-3 mb-2 text-[10px] font-semibold tracking-[0.2em] text-gray-600">MANAGEMENT</div>
          <router-link
            v-for="item in menus"
            :key="item.path"
            :to="item.path"
            class="admin-menu-item"
            active-class="is-active"
          >
            <el-icon class="admin-menu-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <span class="admin-menu-arrow">›</span>
          </router-link>
        </div>
      </nav>

      <!-- 底部账号区 -->
      <div class="border-t border-white/5 p-3">
        <div class="flex items-center gap-3 px-3 py-2 rounded-lg bg-white/[0.02] mb-2">
          <div class="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-sm font-bold shrink-0">
            {{ avatarChar }}
          </div>
          <div class="min-w-0">
            <div class="text-sm text-gray-200 truncate">
              {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员' }}
            </div>
            <div class="text-[10px] text-indigo-300 font-mono tracking-wider">
              {{ userStore.userInfo?.role || 'ADMIN' }}
            </div>
          </div>
        </div>
        <a class="admin-menu-item !py-2" @click="goMain">
          <el-icon class="admin-menu-icon"><Back /></el-icon><span>返回主站</span>
        </a>
        <a class="admin-menu-item !py-2 hover:!text-red-300 hover:!bg-red-500/10" @click="logout">
          <el-icon class="admin-menu-icon"><SwitchButton /></el-icon><span>退出登录</span>
        </a>
      </div>
    </aside>

    <!-- 主内容 -->
    <main class="flex-1 relative z-10 overflow-auto">
      <header class="admin-header sticky top-0 z-20 h-14 flex items-center justify-between px-6">
        <div class="flex items-center gap-3 text-sm">
          <span class="text-gray-500">管理后台</span>
          <span class="text-gray-700">/</span>
          <span class="text-gray-200 font-medium">{{ currentMenu?.label || '' }}</span>
        </div>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_#34d399] animate-pulse"></span>
            <span class="text-[11px] text-emerald-300 font-mono tracking-wider">SYSTEM ONLINE</span>
          </div>
          <div class="text-xs text-gray-500 font-mono">{{ nowText }}</div>
        </div>
      </header>

      <div class="p-6">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import {
  Monitor, Odometer, User, Coin, List, Back, SwitchButton, Document
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const menus = [
  { path: '/admin/dashboard', label: '仪表盘', icon: Odometer },
  { path: '/admin/users', label: '用户管理', icon: User },
  { path: '/admin/stocks', label: '股票池管理', icon: Coin },
  { path: '/admin/orders', label: '订单巡检', icon: List },
  { path: '/admin/audit', label: '审计日志', icon: Document }
]

const currentMenu = computed(() => menus.find(m => route.path.startsWith(m.path)))

const avatarChar = computed(() => {
  const n = userStore.userInfo?.nickname || userStore.userInfo?.username || 'A'
  return String(n).charAt(0).toUpperCase()
})

const nowText = ref('')
let timer = null
const tick = () => {
  const d = new Date()
  const pad = (x) => String(x).padStart(2, '0')
  nowText.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
onMounted(() => { tick(); timer = setInterval(tick, 1000) })
onUnmounted(() => { if (timer) clearInterval(timer) })

const goMain = () => {
  router.push('/')
}

const logout = () => {
  userStore.clearAuth()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style>
/* ============== 侧栏 / 顶栏 ============== */
.admin-root {
  font-feature-settings: 'tnum';
}
.admin-aside {
  background: linear-gradient(180deg, #0b0d15 0%, #07080d 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.04);
}
.admin-header {
  background: rgba(7, 8, 13, 0.7);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.admin-menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 13.5px;
  color: #9ca3af;
  cursor: pointer;
  transition: background-color .15s, color .15s, transform .15s;
  margin-bottom: 2px;
  user-select: none;
}
.admin-menu-item:hover {
  color: #e5e7eb;
  background-color: rgba(255, 255, 255, 0.04);
}
.admin-menu-item.is-active {
  color: #c7d2fe;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.18) 0%, rgba(59, 130, 246, 0.05) 100%);
  box-shadow: inset 2px 0 0 #818cf8;
}
.admin-menu-icon {
  font-size: 16px;
  color: inherit;
  width: 18px;
  flex-shrink: 0;
}
.admin-menu-arrow {
  margin-left: auto;
  color: #4b5563;
  font-size: 14px;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity .15s, transform .15s;
}
.admin-menu-item:hover .admin-menu-arrow,
.admin-menu-item.is-active .admin-menu-arrow {
  opacity: 1;
  transform: translateX(0);
  color: #818cf8;
}

/* ============== 卡片 ============== */
.admin-card {
  background-color: rgba(17, 19, 27, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 4px 24px -8px rgba(0, 0, 0, 0.4);
}
.admin-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  background: rgba(255, 255, 255, 0.015);
}
.admin-card-title-text {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #e5e7eb;
}
.admin-card-title-text::before {
  content: '';
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, #818cf8, #60a5fa);
}

/* ============== 页面标题 ============== */
.admin-page-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.admin-page-title h2 {
  font-size: 22px;
  font-weight: 700;
  color: #f3f4f6;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}
.admin-page-title h2 .pt-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(99,102,241,.25), rgba(59,130,246,.15));
  border: 1px solid rgba(129, 140, 248, 0.25);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
  font-size: 18px;
}
.admin-page-title .pt-sub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

/* ============== 统计卡片 ============== */
.stat-card {
  position: relative;
  background: linear-gradient(135deg, rgba(255,255,255,0.025), rgba(255,255,255,0.005));
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  overflow: hidden;
  transition: transform .2s, border-color .2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  border-color: rgba(129, 140, 248, 0.25);
}
.stat-card .stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.stat-card .stat-label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
  letter-spacing: 0.5px;
}
.stat-card .stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #f3f4f6;
  font-variant-numeric: tabular-nums;
  font-family: 'JetBrains Mono', ui-monospace, Menlo, monospace;
  line-height: 1.1;
}

/* ============== 工具栏 ============== */
.admin-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(17, 19, 27, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  margin-bottom: 14px;
}
.admin-toolbar .el-input,
.admin-toolbar .el-select {
  --el-fill-color-blank: rgba(255, 255, 255, 0.03);
}
.admin-toolbar .el-input__wrapper,
.admin-toolbar .el-select .el-input__wrapper {
  background-color: rgba(255, 255, 255, 0.03) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06) inset !important;
  transition: box-shadow .15s;
}
.admin-toolbar .el-input__wrapper:hover,
.admin-toolbar .el-select:hover .el-input__wrapper {
  box-shadow: 0 0 0 1px rgba(129, 140, 248, 0.35) inset !important;
}
.admin-toolbar .el-input__wrapper.is-focus,
.admin-toolbar .el-select .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #6366f1 inset !important;
}
.admin-toolbar .el-input__inner,
.admin-toolbar .el-select__placeholder,
.admin-toolbar .el-select__selected-item {
  color: #e5e7eb !important;
}

/* ============== 表格 ============== */
.admin-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.025);
  --el-table-header-text-color: #9ca3af;
  --el-table-text-color: #d1d5db;
  --el-table-row-hover-bg-color: rgba(99, 102, 241, 0.06);
  --el-table-border-color: rgba(255, 255, 255, 0.04);
  background-color: transparent !important;
  font-size: 13px;
}
.admin-table th.el-table__cell {
  background-color: var(--el-table-header-bg-color) !important;
  border-bottom: 1px solid rgba(255,255,255,0.06) !important;
  font-size: 11.5px;
  letter-spacing: 1.2px;
  font-weight: 600;
  text-transform: uppercase;
  padding: 10px 0;
}
.admin-table td.el-table__cell {
  border-bottom: 1px solid var(--el-table-border-color) !important;
  background-color: transparent !important;
  padding: 10px 0;
}
.admin-table .el-table__body tr:hover > td.el-table__cell {
  background-color: var(--el-table-row-hover-bg-color) !important;
}
.admin-table::before { display: none !important; }
.admin-table .cell { line-height: 1.45; }

/* tabular nums for 顺眼 */
.admin-table .num,
.admin-table .font-mono {
  font-variant-numeric: tabular-nums;
}

/* ============== 状态点 tag ============== */
.dot-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  line-height: 1.6;
  font-weight: 500;
  border: 1px solid transparent;
}
.dot-tag::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
  flex-shrink: 0;
}
.dot-tag.success { color: #34d399; background: rgba(16, 185, 129, 0.1); border-color: rgba(16, 185, 129, 0.25); }
.dot-tag.warn    { color: #fbbf24; background: rgba(245, 158, 11, 0.1); border-color: rgba(245, 158, 11, 0.25); }
.dot-tag.danger  { color: #f87171; background: rgba(239, 68, 68, 0.1);  border-color: rgba(239, 68, 68, 0.25); }
.dot-tag.info    { color: #93c5fd; background: rgba(59, 130, 246, 0.1); border-color: rgba(59, 130, 246, 0.25); }
.dot-tag.muted   { color: #9ca3af; background: rgba(156, 163, 175, 0.08); border-color: rgba(156, 163, 175, 0.18); }
.dot-tag.primary { color: #a5b4fc; background: rgba(99, 102, 241, 0.12); border-color: rgba(99, 102, 241, 0.3); }

/* ============== 分页 ============== */
.admin-card .el-pagination {
  --el-pagination-button-bg-color: transparent;
  --el-pagination-hover-color: #818cf8;
  color: #9ca3af;
}
.admin-card .el-pagination .btn-prev,
.admin-card .el-pagination .btn-next,
.admin-card .el-pagination .el-pager li {
  background: rgba(255, 255, 255, 0.025) !important;
  color: #d1d5db !important;
  border: 1px solid rgba(255, 255, 255, 0.06);
  margin: 0 2px;
}
.admin-card .el-pagination .el-pager li.is-active {
  background: linear-gradient(135deg, #6366f1, #3b82f6) !important;
  color: #fff !important;
  border-color: transparent;
}

/* ============== Dialog ============== */
.el-overlay-dialog .el-dialog {
  background: #0f111a !important;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.el-overlay-dialog .el-dialog__title { color: #f3f4f6; }
.el-overlay-dialog .el-dialog__body { color: #d1d5db; }
</style>

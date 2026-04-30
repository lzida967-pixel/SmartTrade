<template>
  <el-config-provider>
    <!-- 对于登录/注册页面，隐藏所有框架菜单，直接展现主体 -->
    <template v-if="$route.meta.hideLayout">
      <router-view />
    </template>

    <!-- 已登录的业务系统骨架架构 -->
    <div v-else class="min-h-screen bg-[#141414] text-white">
      <el-container class="h-screen outline-none">
        <el-aside width="240px" class="border-r border-gray-800">
          <div class="h-16 flex items-center justify-center font-bold text-xl border-b border-gray-800 text-blue-500 tracking-wider">
            <el-icon class="mr-2"><TrendCharts /></el-icon> SmartTrade
          </div>
          <el-menu :default-active="$route.path" router class="border-none !bg-transparent" active-text-color="#409eff">
            <el-menu-item index="/">
              <el-icon><DataBoard /></el-icon>
              <span>交易看盘</span>
            </el-menu-item>
            <el-menu-item index="/market">
              <el-icon><TrendCharts /></el-icon>
              <span>行情中心</span>
            </el-menu-item>
            <el-menu-item index="/orders">
              <el-icon><List /></el-icon>
              <span>委托单记录</span>
            </el-menu-item>
            <el-menu-item index="/positions">
              <el-icon><Wallet /></el-icon>
              <span>我的持仓</span>
            </el-menu-item>
            <el-menu-item index="/ai">
              <el-icon><ChatDotRound /></el-icon>
              <span>AI 助手</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        
        <el-container>
<el-header class="h-16 border-b border-gray-800 flex justify-between items-center px-6 relative z-[100]">
            <div class="font-medium flex items-center gap-3">
              <span class="text-gray-300">A股虚拟撮合交易系统</span>
              <div class="flex items-center gap-2 text-xs bg-gray-800/50 px-2 py-1 rounded">
                <span class="relative flex h-2 w-2">
                  <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                  <span class="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                </span>
                <span class="text-green-500/80 tracking-widest font-mono">CORE_ENGINE: ONLINE</span>
              </div>
            </div>

            <div class="flex items-center gap-6 h-full">              <!-- 系统控制台入口 -->
              <a 
                v-if="userStore.userInfo?.role === 'ADMIN'" 
                href="/admin"
                target="_blank"
                class="flex items-center gap-2 bg-gradient-to-r from-purple-600/20 to-blue-600/20 hover:from-purple-500/40 hover:to-blue-500/40 border border-purple-500/30 px-4 py-1.5 rounded-full transition-all duration-300 text-sm font-bold text-purple-300 shadow-[0_0_10px_rgba(168,85,247,0.15)] hover:shadow-[0_0_15px_rgba(168,85,247,0.3)] cursor-pointer no-underline"
              >
                <el-icon class="text-base"><Monitor /></el-icon>
                系统控制台
              </a>
              <!-- B站风格 头像 Hover 展开区 -->
              <div class="relative group h-full flex items-center px-4">
                <!-- 触发区: 默认状态（水平排列类似之前） -->
                <div class="flex items-center gap-3 cursor-pointer py-1.5 px-2 rounded-lg transition-colors group-hover:bg-gray-800/50">
                  <div class="relative w-8 h-8 z-50">
                    <!-- 悬浮时放大的头像本体 -->
                    <div class="absolute inset-0 rounded-full custom-bili-avatar overflow-hidden border border-gray-700 bg-gray-800 flex items-center justify-center">
                      <img 
                        v-if="userStore.userInfo?.avatar"
                        :src="userStore.userInfo.avatar" 
                        class="w-full h-full object-cover"
                      />
                      <el-icon v-else class="text-xl text-gray-500"><User /></el-icon>
                    </div>
                  </div>
                  <!-- 原来的用户名，悬浮时稍微变淡 -->
                  <span class="text-gray-300 font-medium transition-opacity group-hover:opacity-50">
                    {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '测试账户' }}
                  </span>
                  <el-icon class="text-gray-500 transition-transform group-hover:rotate-180"><ArrowDown /></el-icon>
                </div>

                <!-- 下拉面板 - 调整宽度为 200px 并在绝对定位中向左拉回以中心对齐右侧触发区 -->
                <div class="absolute top-[64px] right-0 translate-x-[15px] w-[200px] bg-[#18181b] border border-[#27272a] rounded-xl shadow-[0_10px_40px_rgba(0,0,0,0.8)] opacity-0 invisible group-hover:opacity-100 group-hover:visible z-40 pb-2 pointer-events-none group-hover:pointer-events-auto custom-bili-panel overflow-hidden">
                  
                  <!-- 卡片头部背景条 -->
                  <div class="absolute top-0 left-0 right-0 h-14 bg-gradient-to-br from-blue-900/40 to-purple-900/30 overflow-hidden rounded-t-xl z-0 border-b border-gray-800/50">
                     <div class="absolute inset-0 bg-cover bg-center blur-md opacity-40" :style="{ backgroundImage: `url(${userStore.userInfo?.avatar || ''})` }"></div>
                  </div>

                  <!-- 撑开高度，给降下来的大头像留出空间 -->
                  <div class="h-[36px]"></div>

                  <!-- 内容信息区 (仅名称) -->
                  <div class="relative z-10 px-4 text-center mt-2 mb-4">
                    <h3 class="text-base font-bold text-gray-100 tracking-wide">
                      {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '测试账户' }}
                    </h3>
                  </div>
                  
                  <!-- 菜单区 -->
                  <div class="px-2 space-y-1 relative z-10">
                    <a @click="handleCommand('profile')" class="flex items-center justify-between px-4 py-3 rounded-lg text-sm text-gray-300 hover:text-blue-400 hover:bg-blue-500/10 transition-colors cursor-pointer font-medium group/item">
                      <span class="flex items-center gap-3">
                         <el-icon class="text-lg"><User /></el-icon> 账户综合设置
                      </span>
                      <el-icon class="opacity-0 group-hover/item:opacity-100 transition-all translate-x-1 group-hover/item:translate-x-0"><ArrowRight /></el-icon>
                    </a>
                    <a @click="handleCommand('api')" class="flex items-center justify-between px-4 py-3 rounded-lg text-sm text-gray-300 hover:text-blue-400 hover:bg-blue-500/10 transition-colors cursor-pointer font-medium group/item">
                      <span class="flex items-center gap-3">
                         <el-icon class="text-lg"><Key /></el-icon> 交易 API 管理
                      </span>
                      <el-icon class="opacity-0 group-hover/item:opacity-100 transition-all translate-x-1 group-hover/item:translate-x-0"><ArrowRight /></el-icon>
                    </a>
                  </div>

                  <div class="h-px bg-gray-800 my-1 mx-3 relative z-10"></div>

                  <div class="px-2 relative z-10">
                    <a @click="handleCommand('logout')" class="flex items-center justify-between px-4 py-3 rounded-lg text-sm text-gray-400 hover:text-red-400 hover:bg-red-500/10 transition-colors cursor-pointer font-medium group/logout">
                      <span class="flex items-center gap-3">
                        <el-icon class="text-lg"><SwitchButton /></el-icon> 安全退出终端
                      </span>
                      <el-icon class="opacity-0 group-hover/logout:opacity-100 transition-all translate-x-1 group-hover/logout:translate-x-0"><ArrowRight /></el-icon>
                    </a>
                  </div>
                </div>
              </div>
            </div>
          </el-header>
          
          <el-main class="p-0 relative">
            <router-view />

            <!-- 用户设置弹窗 (覆盖在应用之上) -->
            <ProfileDialog v-model="profileVisible" />
          </el-main>
        </el-container>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from './stores/user'
import { ElMessage } from 'element-plus'
import request from './utils/request'
import ProfileDialog from './components/ProfileDialog.vue'
import { 
  TrendCharts, DataBoard, List, Wallet, 
  User, Key, SwitchButton, ArrowRight,
  ChatDotRound, Trophy, Monitor
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const profileVisible = ref(false)

const fetchUserInfo = async () => {
  if (userStore.token && !userStore.userInfo) {
    try {
      const res = await request.get('/user/info')
      if (res.code === 200) {
        userStore.setUserInfo(res.data)
      }
    } catch (e) {
      console.error(e)
    }
  }
}

// 首次挂载时尝试获取用户信息
onMounted(() => {
  fetchUserInfo()
})

// 监听路由变化，一旦不处于隐藏布局的页面（证明进入了系统内部）且没有加载过用户信息，就触发加载
watch(() => route.path, (newPath) => {
  if (newPath !== '/login') {
    fetchUserInfo()
  }
})

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.clearAuth()
    ElMessage.success('已安全断开服务端连接，登出成功')
    router.push('/login')
  } else if (command === 'profile') {
    // 打开设置面板
    profileVisible.value = true
  } else if (command === 'api') {
    ElMessage.info('API密钥管理功能开发中...')
  }
}
</script>

<style>
/* 覆盖 Element Plus 侧边栏自带的边框 */
.el-menu {
  border-right: none;
}

/* B站风格的面板和头像特制动效 */
.custom-bili-avatar {
  transform-origin: center top;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), border-color 0.3s ease, box-shadow 0.3s ease;
}
/* 鼠标悬浮触发区后，真正头像(div.custom-bili-avatar)的行为 */
.group:hover .custom-bili-avatar {
  /* 高度原来 32px，放大2.2倍是 70px 左右。往下移动大约16像素让其完美跨越面板 */
  transform: translateY(16px) scale(2.2);
  border-color: #27272a;
  z-index: 100;
}

/* 下拉面板带微弱上提的过度效果 */
.custom-bili-panel {
  transform: translateY(12px);
  transition: opacity 0.3s ease, visibility 0.3s ease, transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.group:hover .custom-bili-panel {
  transform: translateY(0);
}
</style>

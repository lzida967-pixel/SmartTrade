<template>
  <div class="financial-terminal-bg min-h-screen flex items-center justify-center p-4 selection:bg-blue-500/30 relative overflow-hidden">
    
    <!-- ====== 动态科技网格层 ====== -->
    <div class="absolute inset-0 bg-grid-pattern pointer-events-none opacity-[0.25] animate-grid"></div>
    
    <!-- ====== 漂浮发光球体 (增加质感和暗黑科技风) ====== -->
    <div class="absolute top-[-10%] left-[-10%] w-[35vw] h-[35vw] bg-blue-700/20 rounded-full mix-blend-screen filter blur-[150px] animate-blob pointer-events-none"></div>
    <div class="absolute top-[20%] right-[-10%] w-[30vw] h-[30vw] bg-cyan-600/10 rounded-full mix-blend-screen filter blur-[120px] animate-blob animation-delay-2000 pointer-events-none"></div>
    <div class="absolute bottom-[-15%] left-[20%] w-[45vw] h-[45vw] bg-indigo-800/20 rounded-full mix-blend-screen filter blur-[150px] animate-blob animation-delay-4000 pointer-events-none"></div>

    <!-- ====== 全屏极光/光轴扫描线 ====== -->
    <div class="absolute inset-0 pointer-events-none scan-line"></div>

    <!-- 背景动画元素：漂浮的K线数据流粒子 (保留已有) -->
    <div class="absolute inset-0 overflow-hidden pointer-events-none opacity-40 z-0">
      <div v-for="i in 20" :key="i" class="kline-particle" :style="getParticleStyle(i)"></div>
    </div>

    <!-- 增加渐入切换动画 -->
    <transition name="terminal-boot" mode="out-in" appear>
      <div 
        v-if="!isSuccess"
        class="login-panel relative w-full max-w-[420px] bg-[#0c0c0e]/80 backdrop-blur-2xl border border-[#27272a]/50 rounded-3xl shadow-[0_0_80px_rgba(0,0,0,0.9)] p-10 z-10 hover:border-blue-500/30 transition-colors duration-500"
      >
        <!-- 头部 LOGO (入场动画延迟:0.1s) -->
        <div class="mb-10 text-center space-y-3 animate-slide-up" style="animation-delay: 0.1s; animation-fill-mode: both;">
          <div class="mx-auto w-16 h-16 bg-gradient-to-tr from-blue-600 to-indigo-500 rounded-2xl flex items-center justify-center mb-5 shadow-[0_0_30px_rgba(37,99,235,0.4)] transition-transform duration-700 hover:rotate-180 hover:shadow-[0_0_50px_rgba(37,99,235,0.6)] cursor-pointer">
            <DataLine class="text-3xl text-white w-8 h-8" />
          </div>
          <h1 class="text-3xl font-extrabold tracking-widest text-transparent bg-clip-text bg-gradient-to-r from-gray-100 to-gray-400">
            量化交易终端
          </h1>
          <p class="text-[12px] text-blue-400/80 tracking-[0.3em] font-semibold uppercase">SmartTrade System</p>
        </div>

        <!-- 登录表单 -->
        <div class="space-y-6 flex-col">
          <div class="animate-slide-up w-full block" style="animation-delay: 0.2s; animation-fill-mode: both;">
            <!-- Vuesax Alpha 输入框 -->
            <vs-input 
              v-model="form.username" 
              placeholder="请输入交易账号" 
              class="fin-input"
            >
              <template #icon>
                <User class="w-5 h-5" />
              </template>
            </vs-input>
          </div>

          <div class="animate-slide-up w-full block" style="animation-delay: 0.3s; animation-fill-mode: both;">
            <vs-input 
              v-model="form.password" 
              type="password" 
              placeholder="请输入安全密码" 
              class="fin-input"
              @keyup.enter="handleAction"
            >
              <template #icon>
                <Lock class="w-5 h-5" />
              </template>
            </vs-input>
          </div>

          <transition name="fade-slide">
            <div v-show="!isLogin" class="animate-slide-up space-y-2 w-full block" style="animation-delay: 0.4s; animation-fill-mode: both;">
              <vs-input 
                v-model="form.nickname" 
                placeholder="为您的账户起个代号 (选填)" 
                class="fin-input"
              >
                <template #icon>
                  <EditPen class="w-5 h-5" />
                </template>
              </vs-input>
            </div>
          </transition>

          <!-- 额外引入 Vuesax 组件：记住密码 checkbox -->
          <div v-show="isLogin" class="flex justify-between items-center animate-slide-up" style="animation-delay: 0.4s; animation-fill-mode: both;">
            <!-- Vuesax 的暗黑风格选项框 -->
            <vs-checkbox v-model="rememberMe" style="--vs-color: 59,130,246;">
               <span class="text-xs text-gray-400 tracking-wider">保存会话</span>
            </vs-checkbox>
            <span class="text-xs text-gray-500 hover:text-white cursor-pointer transition-colors duration-300">忘记凭证?</span>
          </div>

          <div class="pt-4 animate-slide-up" style="animation-delay: 0.5s; animation-fill-mode: both;">
            <!-- 使用 Vuesax 特效交互按钮: 拥有波纹和动画图标支持 -->
            <vs-button 
              block 
              color="#2563eb"
              size="large"
              :loading="loading"
              @click="handleAction"
              class="terminal-btn font-bold tracking-widest relative overflow-hidden"
              animation-type="scale"
            >
              {{ isLogin ? '安全接入终端' : '开通虚拟资金账户' }}
              <!-- 按钮内部的右侧滑入图标 -->
              <template #animate>
                <ArrowRight class="w-5 h-5" />
              </template>
            </vs-button>
          </div>
          
          <div class="flex justify-center items-center gap-3 pt-6 border-t border-white/5 mt-4 animate-slide-up" style="animation-delay: 0.6s; animation-fill-mode: both;">
            <span class="text-xs text-gray-500">{{ isLogin ? '首次使用本终端?' : '已拥有量化账户?' }}</span>
            <button 
              @click.prevent="toggleMode" 
              class="text-xs text-blue-500 hover:text-cyan-400 font-bold transition-transform transform hover:scale-105 cursor-pointer disabled:opacity-50"
              :disabled="loading"
            >
              {{ isLogin ? '立即构建新档' : '返回系统登录' }}
            </button>
          </div>
        </div>
      </div>
      
      <!-- 登录成功后的接驳动画：极客数据同步效果 -->
      <div v-else class="z-10 flex flex-col items-center justify-center space-y-8 absolute top-[40%]">
        <div class="relative flex items-center justify-center w-28 h-28 mx-auto">
          <!-- 外层光晕环 -->
          <div class="absolute inset-0 border-[3px] border-blue-500/30 rounded-full animate-ping"></div>
          <!-- 旋转扫面环 -->
          <div class="absolute inset-2 border-[4px] border-t-blue-500 border-r-cyan-400 border-b-transparent border-l-transparent rounded-full animate-spin"></div>
          <!-- 核心成功标识 -->
          <div class="absolute inset-4 bg-blue-600/20 rounded-full flex items-center justify-center backdrop-blur-md">
            <Select class="text-cyan-400 w-10 h-10" />
          </div>
        </div>

        <div class="space-y-3 text-center">
          <div class="text-2xl font-black tracking-[0.2em] text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-cyan-200">
            身份验证通过
          </div>
          <div class="text-xs text-blue-400/80 tracking-[0.4em] font-medium type-writer-text uppercase">
            正在桥接全市场行情数据 ...
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { User, Lock, EditPen, DataLine, Select, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const isLogin = ref(true)
const loading = ref(false)
const isSuccess = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

const toggleMode = () => {
  isLogin.value = !isLogin.value
  form.password = ''
  form.nickname = ''
}

const handleAction = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('系统警报：凭证输入不完整')
    return
  }
  
  loading.value = true
  try {
    if (isLogin.value) {
      // 执行登录
      const res = await request.post('/user/login', {
        username: form.username,
        password: form.password
      })
      userStore.setToken(res.data)
      loginSuccessPipeline()
    } else {
      // 执行注册
      await request.post('/user/register', {
        username: form.username,
        password: form.password,
        nickname: form.nickname
      })
      ElMessage.success('账户初始化完毕！已划拨 1,000,000 元虚拟本金')
      isLogin.value = true
      form.password = ''
    }
  } catch (error) {
    // 错误信息在 axios interceptor 已经拦截处理
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 登录成功后，播放一段酷炫动画再进系统
const loginSuccessPipeline = () => {
  isSuccess.value = true
  setTimeout(() => {
    router.push('/')
  }, 2200)
}

// 随机生成背景中漂浮的K线数据流CSS样式
const getParticleStyle = (index) => {
  const isRed = Math.random() > 0.5
  const color = isRed ? '#ef4444' : '#22c55e' // A股红涨绿跌基调
  const left = Math.random() * 100
  const height = Math.random() * 80 + 20
  const duration = Math.random() * 10 + 10
  const delay = Math.random() * 5
  const opacity = Math.random() * 0.5 + 0.1

  return {
    position: 'absolute',
    bottom: '-100px',
    left: `${left}%`,
    width: '4px',
    height: `${height}px`,
    backgroundColor: color,
    opacity: opacity,
    boxShadow: `0 0 10px ${color}`,
    borderRadius: '2px',
    animation: `floatUp ${duration}s linear ${delay}s infinite`
  }
}
</script>

<style scoped>
/* 终极黑金融终端背景 */
.financial-terminal-bg {
  background-color: #030303;
  background-image: 
    radial-gradient(ellipse at top, rgba(30, 58, 138, 0.15) 0%, transparent 50%),
    radial-gradient(ellipse at bottom, rgba(59, 130, 246, 0.05) 0%, transparent 50%);
}

/* ================= 动态背景与动画定义 ================= */

/* 1. 向下移动的无缝网格科技线 */
.bg-grid-pattern {
  background-image: 
    linear-gradient(to right, rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 50px 50px;
}
.animate-grid {
  animation: grid-move 20s linear infinite;
}
@keyframes grid-move {
  0% { transform: translateY(-50px); }
  100% { transform: translateY(0); }
}

/* 2. 悬浮缓慢移动的发光球 */
.animate-blob {
  animation: blob 15s infinite alternate ease-in-out;
}
.animation-delay-2000 { animation-delay: 2s; }
.animation-delay-4000 { animation-delay: 4s; }
@keyframes blob {
  0% { transform: translate(0px, 0px) scale(1); }
  33% { transform: translate(30px, -50px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
  100% { transform: translate(0px, 0px) scale(1); }
}

/* 3. 屏幕扫描线段，极光扫过效果 */
.scan-line {
  background: linear-gradient(to bottom, transparent, rgba(59, 130, 246, 0.1) 50%, transparent);
  height: 200px;
  animation: scan 8s ease-in-out infinite;
  opacity: 0.6;
}
@keyframes scan {
  0% { transform: translateY(-100vh); }
  100% { transform: translateY(100vh); }
}

/* ======================================================== */

/* K线粒子的悬浮上升动画 */
@keyframes floatUp {
  0% { transform: translateY(0); opacity: 0; }
  10% { opacity: var(--bg-opacity, 0.5); }
  90% { opacity: var(--bg-opacity, 0.5); }
  100% { transform: translateY(-120vh); opacity: 0; }
}

/* 表单每一项的阶梯式入场动画效果 */
.animate-slide-up {
  opacity: 0;
  transform: translateY(20px);
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes slideUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 整个面板第一次装载出来的开机动画 */
.terminal-boot-enter-active {
  animation: bootUp 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}
.terminal-boot-leave-active {
  animation: bootDown 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes bootUp {
  0% { opacity: 0; transform: scale(0.95) translateY(30px); filter: blur(10px); }
  100% { opacity: 1; transform: scale(1) translateY(0); filter: blur(0); }
}

@keyframes bootDown {
  0% { opacity: 1; transform: scale(1); filter: blur(0); }
  100% { opacity: 0; transform: scale(1.1); filter: blur(10px); }
}

/* 深度定制 Vuesax 输入框的悬浮、高亮、玻璃态颜色效果 */
:deep(.fin-input) {
  width: 100% !important;
  display: block;
  --vs-input-width: 100% !important;
  --vs-input-max-width: 100% !important;
}

:deep(.fin-input .vs-input-parent) {
  width: 100% !important;
}

:deep(.fin-input .vs-input-content) {
  width: 100% !important;
}

:deep(.fin-input .vs-input),
:deep(.fin-input input) {
  width: 100% !important;
  background-color: rgba(24, 24, 27, 0.6) !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.05) inset !important;
  border-radius: 12px;
  height: 52px;
  padding: 0 16px 0 45px; /* 让出 icon 的空间 */
  backdrop-filter: blur(8px);
  transition: all 0.3s ease;
  color: #f4f4f5 !important;
  font-size: 15px;
  letter-spacing: 1px;
}

:deep(.fin-input:hover .vs-input) {
  box-shadow: 0 0 0 1px rgba(59,130,246,0.5) inset !important;
  background-color: rgba(24, 24, 27, 0.8) !important;
}

:deep(.fin-input .vs-input:focus) {
  background-color: rgba(0, 0, 0, 0.5) !important;
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 15px rgba(59, 130, 246, 0.2) !important;
}

:deep(.fin-input .vs-input__icon) {
  color: #52525b !important;
  background: transparent !important;
}

:deep(.fin-input .vs-input:focus ~ .vs-input__icon) {
  color: #3b82f6 !important;
}

/* Vue 渐变动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Vuesax 按钮覆写与定制 */
:deep(.terminal-btn.vs-button) {
  border-radius: 12px;
  height: 52px;
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border: 1px solid rgba(59, 130, 246, 0.5);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
:deep(.terminal-btn.vs-button:hover) {
  box-shadow: 0 8px 25px -5px rgba(37,99,235, 0.6);
}

/* 控制加载中打字机特效文字 */
.type-writer-text {
  overflow: hidden;
  white-space: nowrap;
  border-right: 2px solid #60a5fa;
  display: inline-block;
  animation: typing 2.5s steps(40, end), blink-caret 0.75s step-end infinite;
}

@keyframes typing {
  from { width: 0; }
  to { width: 100%; }
}
@keyframes blink-caret {
  from, to { border-color: transparent; }
  50% { border-color: #60a5fa; }
}
</style>
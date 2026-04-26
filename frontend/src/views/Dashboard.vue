<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans selection:bg-blue-500/30">
    <!-- 主要内容区 -->
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">
      
      <!-- 资产概览：使用 Tailwind Grid 替代 Element el-row / el-col -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        
        <!-- 资产卡片 1 -->
        <div class="stat-card group">
          <div class="text-gray-500 text-xs font-semibold tracking-widest mb-2 flex items-center gap-2 group-hover:text-gray-400 transition-colors">
            <Wallet class="w-4 h-4" /> 账户总资产估值 (CNY)
          </div>
          <div class="text-4xl font-black text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-orange-400 font-mono mt-3 filter drop-shadow-[0_0_8px_rgba(239,68,68,0.3)]">
            1,000,000.00
          </div>
          <div class="mt-4 text-xs text-red-500/80 flex items-center gap-1 font-mono">
            <TopRight class="w-3 h-3" /> +0.00% (初始注入)
          </div>
        </div>

        <!-- 资产卡片 2 -->
        <div class="stat-card group">
          <div class="text-gray-500 text-xs font-semibold tracking-widest mb-2 flex items-center gap-2 group-hover:text-gray-400 transition-colors">
            <Money class="w-4 h-4" /> 核心可用资金
          </div>
          <div class="text-3xl font-bold text-gray-200 font-mono mt-4">
            1,000,000.00
          </div>
        </div>

        <!-- 资产卡片 3 -->
        <div class="stat-card group">
          <div class="text-gray-500 text-xs font-semibold tracking-widest mb-2 flex items-center gap-2 group-hover:text-gray-400 transition-colors">
            <DataAnalysis class="w-4 h-4" /> 今日交易净盈亏
          </div>
          <div class="text-3xl font-bold text-gray-600 font-mono mt-4">
            0.00
          </div>
          <div class="mt-5 text-xs text-gray-600 flex items-center gap-1">
            -- 暂无持仓波动
          </div>
        </div>
      </div>

      <!-- 下部分：图表和交易面板 -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        <!-- 行情大盘展示区 -->
        <div class="lg:col-span-2 chart-card h-[450px] flex flex-col relative overflow-hidden">
          <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center z-10">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <TrendCharts class="w-4 h-4" /> 实时自选行情 (AI-Prediction版)
            </span>
            <span class="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-1 rounded text-mono">1000ms 刷新率</span>
          </div>
          <!-- 暂无数据背景占位 -->
          <div class="absolute inset-0 bg-grid-pattern opacity-10 pointer-events-none"></div>
          <div class="flex-1 flex flex-col items-center justify-center space-y-4 z-10">
             <div class="w-16 h-16 border-2 border-dashed border-blue-500/30 rounded-full mx-auto animate-[spin_4s_linear_infinite] flex items-center justify-center">
                 <Loading class="text-blue-500 text-xl animate-spin" />
             </div>
             <div class="text-blue-400/50 tracking-[0.2em] text-xs">等待行情终端信号接入...</div>
          </div>
        </div>
        
        <!-- 极速交易指令面板 -->
        <div class="chart-card h-[450px] flex flex-col">
           <div class="border-b border-white/5 pb-3 mb-6">
              <span class="text-xs font-semibold tracking-widest text-gray-300 flex items-center gap-2">
                <Aim class="w-4 h-4" /> 极速交易指令面板
              </span>
           </div>
           
           <div class="space-y-6 flex-1">
             <!-- Vuesax 搜索输入框 -->
             <div class="w-full fin-dark-input">
               <vs-input block placeholder="输入标的代码 (如: sh600519)" v-model="tradeCode">
                  <template #icon>
                    <Search class="w-4 h-4" />
                  </template>
               </vs-input>
             </div>

             <div class="w-full fin-dark-input">
               <vs-input block placeholder="委托价格 (CNY)" v-model="tradePrice" type="number">
               </vs-input>
             </div>

             <div class="w-full fin-dark-input">
               <vs-input block placeholder="交易数量 (股)" v-model="tradeVolume" type="number">
               </vs-input>
             </div>
             
             <!-- Vuesax 按钮区 -->
             <div class="flex gap-4 pt-4">
               <vs-button block color="#ef4444" class="font-bold flex-1 tracking-widest terminal-btn-buy">买入做多</vs-button>
               <vs-button block color="#22c55e" class="font-bold flex-1 tracking-widest terminal-btn-sell">卖出做空</vs-button>
             </div>
           </div>
        </div>

      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { 
  DataLine, Wallet, Money, DataAnalysis, 
  TopRight, TrendCharts, Loading, Aim, Search
} from '@element-plus/icons-vue'

const tradeCode = ref('')
const tradePrice = ref('')
const tradeVolume = ref('')

</script>

<style scoped>
/* 终极黑金融卡片统一样式 */
.stat-card {
  position: relative;
  background-color: rgba(24, 24, 27, 0.5);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.5rem;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.stat-card:hover {
  border-color: rgba(59, 130, 246, 0.3);
  box-shadow: 0 0 30px rgba(37,99,235,0.05);
  transform: translateY(-0.25rem);
}

.chart-card {
  position: relative;
  background-color: rgba(12, 12, 14, 0.8);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.5rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.chart-card:hover {
  border-color: #3f3f46;
}

/* 背景网格微动效 */
.bg-grid-pattern {
  background-image: 
    linear-gradient(to right, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 30px 30px;
}

/* Vuesax 深色表单魔改覆盖 */
:deep(.fin-dark-input .vs-input) {
  width: 100% !important;
  background-color: rgba(24, 24, 27, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.05) inset !important;
  color: #f4f4f5 !important;
  height: 48px;
  border-radius: 10px;
}
:deep(.fin-dark-input .vs-input-parent),
:deep(.fin-dark-input .vs-input-content) {
  width: 100% !important;
}
:deep(.fin-dark-input .vs-input:focus) {
  background-color: rgba(0,0,0,0.4) !important;
  box-shadow: 0 0 0 1px #3b82f6 inset !important;
}

/* 定制化做多做空按钮 */
:deep(.terminal-btn-buy.vs-button) {
  background: linear-gradient(135deg, #b91c1c, #ef4444);
  height: 48px;
  border-radius: 8px;
}
:deep(.terminal-btn-sell.vs-button) {
  background: linear-gradient(135deg, #15803d, #22c55e);
  height: 48px;
  border-radius: 8px;
}
</style>

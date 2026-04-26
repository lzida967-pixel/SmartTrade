<template>
  <div class="min-h-screen bg-[#0a0a0f] text-gray-200 overflow-hidden relative pb-10">
    
    <!-- Decorative background elements -->
    <div class="absolute top-0 right-0 w-[500px] h-[500px] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>
    <div class="absolute bottom-0 left-0 w-[500px] h-[500px] bg-blue-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

    <!-- 主体容器，设置最大宽度并居中，增加屏幕四周的安全距离 -->
    <div class="w-full max-w-[1600px] mx-auto h-screen flex flex-col relative z-10 pt-10 px-6 sm:px-10 lg:px-12">
      
      <!-- Header Section -->
      <div class="flex justify-between items-center mb-8">
        <div>
          <h1 class="text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-purple-400 to-blue-400 tracking-tight">
            System Admin Console
          </h1>
          <p class="text-gray-500 mt-1 flex items-center gap-2 text-sm font-mono tracking-widest">
            <span class="w-2 h-2 rounded-full bg-green-500 shadow-[0_0_8px_#22c55e] animate-pulse"></span>
            CORE_ENGINE // NODE: MASTER-01
          </p>
        </div>
        
        <div class="flex gap-4">
          <el-button 
            type="primary" 
            color="#2563eb"
            class="!border-none !hidden md:!flex shadow-[0_0_15px_rgba(59,130,246,0.3)] hover:shadow-[0_0_25px_rgba(59,130,246,0.5)] transition-all font-bold tracking-widest"
          >
            <el-icon class="mr-2"><Refresh /></el-icon> 实时行情同步
          </el-button>
          
          <el-button 
            type="primary" 
            color="#7e22ce"
            :loading="initializing" 
            @click="handleInitStocks"
            class="!border-none shadow-[0_0_15px_rgba(126,34,206,0.3)] hover:shadow-[0_0_25px_rgba(126,34,206,0.5)] transition-all font-bold tracking-widest"
          >
            <el-icon class="mr-2"><Cpu /></el-icon> 注入基础证券核心数据
          </el-button>
        </div>
      </div>

      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <div class="bg-gray-900/60 backdrop-blur-md border border-gray-800 p-5 rounded-2xl flex items-center justify-between group hover:border-gray-700 transition-colors">
          <div>
            <p class="text-sm font-medium text-gray-500 mb-1">系统证券总库</p>
            <h3 class="text-3xl font-bold text-gray-100 font-mono">{{ stocks.length }}</h3>
          </div>
          <div class="w-12 h-12 bg-blue-500/10 rounded-xl flex items-center justify-center text-blue-400 group-hover:scale-110 transition-transform">
            <el-icon class="text-2xl"><Document /></el-icon>
          </div>
        </div>
        
        <div class="bg-gray-900/60 backdrop-blur-md border border-gray-800 p-5 rounded-2xl flex items-center justify-between group hover:border-gray-700 transition-colors">
          <div>
            <p class="text-sm font-medium text-gray-500 mb-1">运行中 (NORMAL)</p>
            <h3 class="text-3xl font-bold text-green-400 font-mono">{{ activeStocks }}</h3>
          </div>
          <div class="w-12 h-12 bg-green-500/10 rounded-xl flex items-center justify-center text-green-400 group-hover:scale-110 transition-transform">
            <el-icon class="text-2xl"><CircleCheck /></el-icon>
          </div>
        </div>

        <div class="bg-gray-900/60 backdrop-blur-md border border-gray-800 p-5 rounded-2xl flex items-center justify-between group hover:border-gray-700 transition-colors">
          <div>
            <p class="text-sm font-medium text-gray-500 mb-1">停牌中 (SUSPEND)</p>
            <h3 class="text-3xl font-bold text-yellow-400 font-mono">{{ suspendStocks }}</h3>
          </div>
          <div class="w-12 h-12 bg-yellow-500/10 rounded-xl flex items-center justify-center text-yellow-400 group-hover:scale-110 transition-transform">
            <el-icon class="text-2xl"><Warning /></el-icon>
          </div>
        </div>

        <div class="bg-gray-900/60 backdrop-blur-md border border-gray-800 p-5 rounded-2xl flex items-center justify-between group hover:border-gray-700 transition-colors">
          <div>
            <p class="text-sm font-medium text-gray-500 mb-1">撮合主节点状态</p>
            <h3 class="text-xl font-bold text-purple-400 tracking-wider mt-1">HEALTHY</h3>
          </div>
          <div class="w-12 h-12 bg-purple-500/10 rounded-xl flex items-center justify-center text-purple-400 group-hover:scale-110 transition-transform">
            <el-icon class="text-2xl"><Odometer /></el-icon>
          </div>
        </div>
      </div>

      <!-- Main Data Table Container -->
      <div class="flex-1 bg-gray-900/60 backdrop-blur-md border border-gray-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col relative">
        <div class="px-6 py-4 border-b border-gray-800 flex justify-between items-center bg-gray-900/50">
          <h3 class="font-bold tracking-wide text-gray-300 flex items-center gap-2">
            <el-icon><Menu /></el-icon> 核心数据库 / 证券基础资料录入 (sys_stock_info)
          </h3>
          <el-input 
            v-model="searchQuery" 
            placeholder="搜索代码或名称..." 
            class="!w-64 max-dark-input"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="flex-1 p-4 overflow-hidden relative">
          <el-table
            :data="filteredStocks"
            height="100%"
            class="custom-master-table"
            v-loading="loading"
            element-loading-background="rgba(10, 10, 15, 0.8)"
            element-loading-text="SYNCING CORE DATA..."
          >
            <el-table-column prop="stockCode" label="证券代码" width="140">
              <template #default="{ row }">
                <span class="font-mono text-blue-400 font-bold bg-blue-500/10 px-2 py-1 rounded">{{ row.stockCode }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="stockName" label="证券简称" min-width="150">
              <template #default="{ row }">
                <span class="text-gray-100 font-medium">{{ row.stockName }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="market" label="上市板块" width="130">
              <template #default="{ row }">
                <span class="px-2.5 py-1 rounded text-xs font-bold tracking-wider"
                  :class="row.market.toUpperCase() === 'SH' ? 'bg-red-500/20 text-red-400 border border-red-500/30' : 'bg-blue-500/20 text-blue-400 border border-blue-500/30'">
                  {{ row.market.toUpperCase() === 'SH' ? '上交所' : '深交所' }}
                </span>
              </template>
            </el-table-column>

            <el-table-column prop="plateType" label="行业/类型" min-width="130">
              <template #default="{ row }">
                <span class="text-gray-400">{{ row.plateType || '--' }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="status" label="撮合状态" width="150" align="center">
              <template #default="{ row }">
                <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-gray-800/50 border border-gray-700" v-if="row.status === 1">
                  <div class="w-1.5 h-1.5 rounded-full bg-green-500 shadow-[0_0_5px_#22c55e]"></div>
                  <span class="text-green-400 text-xs font-bold">NORMAL</span>
                </div>
                <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-gray-800/50 border border-gray-700" v-else>
                  <div class="w-1.5 h-1.5 rounded-full bg-yellow-500 shadow-[0_0_5px_#eab308]"></div>
                  <span class="text-yellow-400 text-xs font-bold">SUSPEND</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="120" fixed="right">
              <template #default>
                <el-button link type="primary" class="!text-blue-500 hover:!text-blue-400">编辑参数</el-button>
              </template>
            </el-table-column>
            
            <template #empty>
              <div class="py-20 flex flex-col items-center justify-center text-gray-500">
                <el-icon class="text-6xl mb-4 opacity-50"><Box /></el-icon>
                <p>数据库此表中暂无证券资料，请点击顶部按钮注入</p>
              </div>
            </template>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Monitor, Cpu, Refresh, Document, CircleCheck, Warning, Odometer, Menu, Search, Box } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const loading = ref(false)
const initializing = ref(false)
const stocks = ref([])
const searchQuery = ref('')

const activeStocks = computed(() => stocks.value.filter(s => s.status === 1).length)
const suspendStocks = computed(() => stocks.value.filter(s => s.status !== 1).length)

const filteredStocks = computed(() => {
  if (!searchQuery.value) return stocks.value
  const q = searchQuery.value.toLowerCase()
  return stocks.value.filter(s => 
    s.stockCode.toLowerCase().includes(q) || 
    s.stockName.toLowerCase().includes(q)
  )
})

const loadStocks = async () => {
  loading.value = true
  try {
    const res = await request.get('/stock/list')
    if (res.code === 200) {
      stocks.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleInitStocks = async () => {
  initializing.value = true
  try {
    const res = await request.post('/stock/init')
    if (res.code === 200) {
      ElMessage.success(res.data || '基础数据注入成功')
      await loadStocks()
    }
  } catch (e) {
    ElMessage.error('注入失败')
  } finally {
    initializing.value = false
  }
}

onMounted(() => {
  loadStocks()
})
</script>

<style>
/* 输入框暗黑定制 */
.max-dark-input .el-input__wrapper {
  background-color: #18181b !important;
  box-shadow: 0 0 0 1px #3f3f46 inset !important;
}
.max-dark-input .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #6366f1 inset !important;
}
.max-dark-input .el-input__inner {
  color: #d4d4d8 !important;
}

/* 高级数据表格统一样式 */
.custom-master-table {
  background-color: transparent !important;
  --el-table-border-color: #27272a;
  --el-table-header-bg-color: rgba(24, 24, 27, 0.4);
  --el-table-header-text-color: #a1a1aa;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(39, 39, 42, 0.5);
}
.custom-master-table th.el-table__cell {
  background-color: var(--el-table-header-bg-color) !important;
  border-bottom: 1px solid var(--el-table-border-color) !important;
  font-weight: 500;
  backdrop-filter: blur(10px);
}
.custom-master-table td.el-table__cell {
  border-bottom: 1px solid var(--el-table-border-color) !important;
  background-color: transparent !important;
}
.custom-master-table .el-table__body tr:hover > td.el-table__cell {
  background-color: var(--el-table-row-hover-bg-color) !important;
}
.custom-master-table::before {
  display: none !important;
}
</style>
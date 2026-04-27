<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans selection:bg-blue-500/30">
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">

      <!-- 欢迎语 -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div class="text-xs text-gray-500 tracking-widest font-mono">资产 · 概览</div>
          <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
            <el-icon class="text-blue-400"><DataAnalysis /></el-icon>
            {{ greeting }}，{{ asset.nickname || asset.username || '交易员' }}
          </div>
        </div>
        <el-button type="primary" plain :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <!-- 顶部资产卡片 -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div class="stat-card">
          <div class="stat-label flex items-center gap-2">
            <Wallet class="w-3.5 h-3.5" /> 账户总资产
          </div>
          <div class="stat-val text-3xl text-transparent bg-clip-text bg-gradient-to-r from-orange-400 to-red-400 mt-3">
            ¥ {{ formatNum(asset.totalAssets) }}
          </div>
          <div class="stat-sub" :class="dailyClass">
            今日 {{ asset.dailyProfit >= 0 ? '+' : '' }}{{ formatNum(asset.dailyProfit) }}
            <span class="ml-1">({{ formatPercent(dailyRate) }})</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-label flex items-center gap-2">
            <Money class="w-3.5 h-3.5" /> 可用资金
          </div>
          <div class="stat-val mt-3">¥ {{ formatNum(asset.availableFunds) }}</div>
          <div class="stat-sub text-gray-500">冻结 ¥ {{ formatNum(asset.frozenFunds) }}</div>
        </div>

        <div class="stat-card">
          <div class="stat-label flex items-center gap-2">
            <DataLine class="w-3.5 h-3.5" /> 持仓市值
          </div>
          <div class="stat-val mt-3">¥ {{ formatNum(asset.marketValue) }}</div>
          <div class="stat-sub text-gray-500">{{ positions.length }} 只持仓</div>
        </div>

        <div class="stat-card">
          <div class="stat-label flex items-center gap-2">
            <TrendCharts class="w-3.5 h-3.5" /> 持仓浮动盈亏
          </div>
          <div class="stat-val mt-3" :class="profitClass(asset.floatingProfit)">
            {{ asset.floatingProfit >= 0 ? '+' : '' }}¥ {{ formatNum(asset.floatingProfit) }}
          </div>
          <div class="stat-sub" :class="profitClass(asset.floatingProfit)">
            浮动 {{ formatPercent(floatingRate) }}
          </div>
        </div>
      </div>

      <!-- 资产收益曲线 -->
      <div class="chart-card relative overflow-hidden">
        <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center flex-wrap gap-3">
          <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
            <DataLine class="w-4 h-4" /> 资产收益曲线
          </span>
          <el-radio-group v-model="curveDays" size="small" @change="loadCurve">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
            <el-radio-button :value="90">近 90 天</el-radio-button>
          </el-radio-group>
        </div>
        <div class="relative w-full" style="height:340px">
          <div ref="curveRef" class="absolute inset-0"></div>
          <div v-if="!curve.length && !loadingCurve"
               class="absolute inset-0 flex items-center justify-center text-gray-600 text-xs tracking-widest">
            暂无快照数据 · 系统每个交易日 16:35 自动拍快照，启动时也会立即拍一次
          </div>
        </div>
      </div>

      <!-- 持仓分布 + 最近交易 -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

        <!-- 持仓分布饼图 -->
        <div class="chart-card">
          <div class="border-b border-white/5 pb-3 mb-4">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <PieChart class="w-4 h-4" /> 持仓分布
            </span>
          </div>
          <div class="relative w-full" style="height:300px">
            <div ref="pieRef" class="absolute inset-0"></div>
            <div v-if="!positions.length"
                 class="absolute inset-0 flex items-center justify-center text-gray-600 text-xs tracking-widest">
              暂无持仓
            </div>
          </div>
        </div>

        <!-- 最近成交 -->
        <div class="chart-card lg:col-span-2">
          <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <List class="w-4 h-4" /> 最近成交
            </span>
            <el-button link type="primary" size="small" @click="$router.push('/orders')">
              查看全部委托 →
            </el-button>
          </div>
          <el-table :data="deals" empty-text="暂无成交记录" class="market-table" :max-height="300">
            <el-table-column prop="dealTime" label="成交时间" min-width="160">
              <template #default="{ row }">
                <span class="text-xs text-gray-400 font-mono">{{ formatTime(row.dealTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="股票" min-width="140">
              <template #default="{ row }">
                <div class="font-mono text-sm">{{ row.stockCode }}</div>
                <div class="text-[11px] text-gray-500">{{ stockName(row.stockCode) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="方向" width="80">
              <template #default="{ row }">
                <el-tag :type="orderDirection(row) === 'BUY' ? 'danger' : 'success'"
                        size="small" disable-transitions>
                  {{ orderDirection(row) === 'BUY' ? '买入' : '卖出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="成交价" width="100" align="right">
              <template #default="{ row }">
                <span class="font-mono">{{ formatNum(row.dealPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="80" align="right">
              <template #default="{ row }">{{ row.dealQuantity }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">
                <span class="font-mono">¥ {{ formatNum(row.dealAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  Wallet, Money, DataAnalysis, DataLine, TrendCharts,
  Refresh, PieChart, List
} from '@element-plus/icons-vue'
import request from '../utils/request'

// ============== 状态 ==============
const loading = ref(false)
const loadingCurve = ref(false)
const asset = ref({
  username: '', nickname: '',
  totalAssets: 0, availableFunds: 0, frozenFunds: 0,
  marketValue: 0, floatingProfit: 0,
  dailyProfit: 0, dailyProfitBase: 0
})
const positions = ref([])
const deals = ref([])
const stockMap = ref({})
const curve = ref([])
const curveDays = ref(30)

const curveRef = ref(null)
const pieRef = ref(null)
let curveChart = null
let pieChart = null

// ============== 派生 ==============
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const dailyRate = computed(() => {
  const base = Number(asset.value.dailyProfitBase) || 0
  if (base <= 0) return 0
  return (Number(asset.value.dailyProfit) || 0) / base * 100
})

const floatingRate = computed(() => {
  // 浮动 / (市值 - 浮动) ≈ 持仓成本基数
  const fp = Number(asset.value.floatingProfit) || 0
  const mv = Number(asset.value.marketValue) || 0
  const cost = mv - fp
  if (cost <= 0) return 0
  return fp / cost * 100
})

const dailyClass = computed(() => profitClass(asset.value.dailyProfit))

// ============== 工具 ==============
const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const formatPercent = (v) => {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return '0.00%'
  return (n >= 0 ? '+' : '') + n.toFixed(2) + '%'
}
const formatTime = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '--'
const profitClass = (v) => {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'text-gray-300'
  return n > 0 ? 'text-red-400' : 'text-green-400'
}
const stockName = (code) => stockMap.value[code]?.stockName || ''
const orderDirection = (deal) => {
  // TradeDeal 没有 direction 字段，但是绝大多数情况下"买入"会让总资产变大
  // 这里用一种更稳的方式：根据 orderNo 反查不太合适，不如通过 dealAmount 配合最近订单匹配
  // 简化：从最近订单 cache 里找对应 orderNo
  const o = recentOrdersByNo.value[deal.orderNo]
  return o ? o.direction : 'BUY'
}
const recentOrdersByNo = ref({})

// ============== 数据加载 ==============
const loadStockMap = async () => {
  try {
    const res = await request.get('/stock/list')
    if (res.code === 200) {
      const m = {}
      for (const s of res.data || []) m[s.stockCode] = s
      stockMap.value = m
    }
  } catch { /* ignore */ }
}

const loadAsset = async () => {
  try {
    const res = await request.get('/user/asset')
    if (res.code === 200) {
      asset.value.username = res.data.username
      asset.value.nickname = res.data.nickname
      asset.value.totalAssets = Number(res.data.totalAssets || 0)
      asset.value.availableFunds = Number(res.data.availableFunds || 0)
      asset.value.frozenFunds = Number(res.data.frozenFunds || 0)
      asset.value.marketValue = Number(res.data.marketValue || 0)
      asset.value.floatingProfit = Number(res.data.floatingProfit || 0)
      positions.value = res.data.positions || []
    }
  } catch { /* ignore */ }
}

const loadCurve = async () => {
  loadingCurve.value = true
  try {
    const res = await request.get('/user/asset/curve', { params: { days: curveDays.value } })
    if (res.code === 200) {
      curve.value = res.data || []
      // 当日收益的"基数" = 昨日总资产
      const len = curve.value.length
      if (len >= 2) {
        asset.value.dailyProfitBase = Number(curve.value[len - 2].totalAssets) || 0
        asset.value.dailyProfit = Number(asset.value.totalAssets) - asset.value.dailyProfitBase
      } else if (len === 1) {
        asset.value.dailyProfitBase = Number(curve.value[0].totalAssets) || 0
        asset.value.dailyProfit = 0
      } else {
        asset.value.dailyProfit = 0
      }
    }
    await nextTick()
    renderCurve()
  } catch { /* ignore */ } finally {
    loadingCurve.value = false
  }
}

const loadDeals = async () => {
  try {
    const [dealsRes, ordersRes] = await Promise.all([
      request.get('/trade/deals', { params: { page: 1, size: 10 } }),
      request.get('/trade/orders', { params: { page: 1, size: 50 } })
    ])
    if (dealsRes.code === 200) deals.value = dealsRes.data?.records || []
    if (ordersRes.code === 200) {
      const m = {}
      for (const o of ordersRes.data?.records || []) m[o.orderNo] = o
      recentOrdersByNo.value = m
    }
  } catch { /* ignore */ }
}

const reload = async () => {
  loading.value = true
  try {
    await Promise.all([loadStockMap(), loadAsset(), loadCurve(), loadDeals()])
    await nextTick()
    renderPie()
  } finally {
    loading.value = false
  }
}

// ============== ECharts 渲染 ==============
const renderCurve = () => {
  if (!curveRef.value) return
  if (!curveChart) curveChart = echarts.init(curveRef.value, 'dark')
  const dates = curve.value.map(p => p.snapshotDate)
  const values = curve.value.map(p => Number(p.totalAssets))
  curveChart.setOption({
    backgroundColor: 'transparent',
    grid: { top: 30, left: 60, right: 30, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,24,35,0.95)',
      borderColor: 'rgba(96,165,250,0.4)',
      textStyle: { color: '#e5e7eb' },
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>总资产 <b style="color:#f97316">¥ ${Number(p.value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</b>`
      }
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: {
        color: '#9ca3af', fontSize: 11,
        formatter: (v) => '¥' + (v / 10000).toFixed(1) + 'w'
      }
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: false,
      lineStyle: { color: '#60a5fa', width: 2 },
      itemStyle: { color: '#60a5fa', borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(96,165,250,0.4)' },
          { offset: 1, color: 'rgba(96,165,250,0)' }
        ])
      }
    }]
  }, true)
}

const renderPie = () => {
  if (!pieRef.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value, 'dark')
  const data = positions.value
    .filter(p => Number(p.marketValue) > 0)
    .map(p => ({
      name: stockName(p.stockCode) || p.stockCode,
      value: Number(p.marketValue)
    }))
  // 如果没持仓，显示一个"可用资金"占位
  if (data.length === 0 && asset.value.availableFunds > 0) {
    data.push({ name: '可用资金', value: asset.value.availableFunds })
  }
  pieChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(20,24,35,0.95)',
      borderColor: 'rgba(96,165,250,0.4)',
      textStyle: { color: '#e5e7eb' },
      formatter: (p) => `${p.name}<br/>¥ ${Number(p.value).toLocaleString('zh-CN', {minimumFractionDigits:2, maximumFractionDigits:2})} (${p.percent}%)`
    },
    legend: {
      orient: 'vertical', right: 0, top: 'middle',
      textStyle: { color: '#9ca3af', fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderColor: 'rgba(0,0,0,0.5)',
        borderWidth: 2
      },
      label: { color: '#d1d5db', fontSize: 11 },
      data
    }]
  }, true)
}

// 响应窗口大小变化
const onResize = () => {
  curveChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  reload()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  curveChart?.dispose(); curveChart = null
  pieChart?.dispose(); pieChart = null
})
</script>

<style scoped>
.stat-card {
  position: relative;
  background-color: rgba(24, 24, 27, 0.5);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.25rem 1.5rem;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.stat-card:hover {
  border-color: rgba(59, 130, 246, 0.3);
  box-shadow: 0 0 30px rgba(37, 99, 235, 0.05);
  transform: translateY(-2px);
}
.stat-label {
  font-size: 11px;
  color: #9ca3af;
  letter-spacing: 1.5px;
}
.stat-val {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 22px;
  color: #e5e7eb;
  font-weight: 700;
}
.stat-sub {
  font-size: 11px;
  margin-top: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.chart-card {
  position: relative;
  background-color: rgba(12, 12, 14, 0.8);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.25rem 1.5rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.chart-card:hover {
  border-color: #3f3f46;
}

:deep(.market-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(59,130,246,0.08);
  --el-table-border-color: rgba(255,255,255,0.04);
  --el-table-text-color: #d1d5db;
  --el-table-header-text-color: #9ca3af;
}
:deep(.market-table th.el-table__cell) {
  background: rgba(24,24,27,0.4) !important;
  border-bottom: 1px solid rgba(255,255,255,0.05) !important;
  font-size: 12px;
  letter-spacing: 1px;
}
</style>

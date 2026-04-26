<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans selection:bg-blue-500/30">
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">

      <!-- 顶部页签 -->
      <div class="flex items-center justify-between">
        <div>
          <div class="text-xs text-gray-500 tracking-widest font-mono">MARKET / REALTIME</div>
          <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
            <el-icon class="text-blue-400"><TrendCharts /></el-icon>
            行情中心
          </div>
        </div>
        <div class="flex items-center gap-3">
          <el-button type="primary" plain :loading="loadingQuotes" @click="loadQuotes">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新行情
          </el-button>
          <div class="text-xs text-gray-500 font-mono">
            数据源：东方财富 · 自动刷新 30s
          </div>
        </div>
      </div>

      <!-- 行情表 + K 线 -->
      <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">

        <!-- 左侧行情列表 -->
        <div class="xl:col-span-2 chart-card overflow-hidden">
          <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <DataLine class="w-4 h-4" /> 自选股票池实时行情
            </span>
            <span class="text-[10px] text-gray-500 font-mono">
              共 {{ quotes.length }} 支
            </span>
          </div>

          <div class="relative">
            <div v-if="loadingQuotes" class="absolute inset-0 z-10 flex items-center justify-center bg-black/40 backdrop-blur-sm rounded-lg">
              <div class="text-cyan-400 text-xs tracking-widest font-mono flex items-center gap-2">
                <el-icon class="animate-spin"><Loading /></el-icon> 行情加载中...
              </div>
            </div>
          <el-table
            :data="quotes"
            highlight-current-row
            :row-class-name="rowClass"
            @row-click="handleSelect"
            stripe
            max-height="580"
            style="width: 100%; background: transparent;"
            class="market-table"
          >
            <el-table-column prop="stockCode" label="代码" width="100" />
            <el-table-column prop="stockName" label="名称" width="120" />
            <el-table-column label="最新价" width="110" align="right">
              <template #default="{ row }">
                <span :class="priceColor(row)">{{ formatNum(row.latestPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="涨跌幅" width="110" align="right">
              <template #default="{ row }">
                <span :class="priceColor(row)">{{ formatPercent(row.changePercent) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="涨跌额" width="110" align="right">
              <template #default="{ row }">
                <span :class="priceColor(row)">{{ formatNum(row.changeAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="今开" width="100" align="right">
              <template #default="{ row }">{{ formatNum(row.openPrice) }}</template>
            </el-table-column>
            <el-table-column label="昨收" width="100" align="right">
              <template #default="{ row }">{{ formatNum(row.preClosePrice) }}</template>
            </el-table-column>
            <el-table-column label="最高/最低" min-width="140" align="right">
              <template #default="{ row }">
                <span class="text-red-400">{{ formatNum(row.highPrice) }}</span>
                <span class="text-gray-500 mx-1">/</span>
                <span class="text-green-400">{{ formatNum(row.lowPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="换手" width="100" align="right">
              <template #default="{ row }">{{ formatPercent(row.turnoverRate) }}</template>
            </el-table-column>
            <el-table-column label="行业" min-width="120">
              <template #default="{ row }">
                <span class="text-gray-400 text-xs">{{ row.industryName || row.plateType || '--' }}</span>
              </template>
            </el-table-column>
          </el-table>
          </div>
        </div>

        <!-- 右侧 K 线 -->
        <div class="chart-card flex flex-col h-[640px]">
          <div class="border-b border-white/5 pb-3 mb-4">
            <div class="flex items-center justify-between">
              <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
                <DataAnalysis class="w-4 h-4" /> 个股 K 线
              </span>
              <el-radio-group v-model="klineLimit" size="small" @change="loadKline">
                <el-radio-button :value="60">60日</el-radio-button>
                <el-radio-button :value="120">120日</el-radio-button>
                <el-radio-button :value="250">1年</el-radio-button>
              </el-radio-group>
            </div>
            <div class="mt-2 flex items-center justify-between" v-if="selected">
              <div>
                <span class="text-base text-gray-100 font-bold">{{ selected.stockName }}</span>
                <span class="text-gray-500 text-xs ml-2 font-mono">{{ selected.stockCode }}</span>
              </div>
              <div :class="priceColor(selected)" class="font-mono text-sm">
                {{ formatNum(selected.latestPrice) }}
                ({{ formatPercent(selected.changePercent) }})
              </div>
            </div>
          </div>

          <div class="relative flex-1 w-full">
            <div ref="klineRef" class="absolute inset-0"></div>
            <div v-if="loadingKline" class="absolute inset-0 z-10 flex items-center justify-center bg-black/40 backdrop-blur-sm">
              <div class="text-cyan-400 text-xs tracking-widest font-mono flex items-center gap-2">
                <el-icon class="animate-spin"><Loading /></el-icon> K 线加载中...
              </div>
            </div>
            <div v-if="!selected && !loadingKline" class="absolute inset-0 flex items-center justify-center text-gray-600 text-sm pointer-events-none">
              点击左侧行情行查看 K 线
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  TrendCharts, DataLine, DataAnalysis, Refresh, Loading
} from '@element-plus/icons-vue'
import request from '../utils/request'

const quotes = ref([])
const selected = ref(null)
const klineLimit = ref(120)
const loadingQuotes = ref(false)
const loadingKline = ref(false)

const klineRef = ref(null)
let klineChart = null
let timer = null

const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toFixed(2)
}
const formatPercent = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return (n >= 0 ? '+' : '') + n.toFixed(2) + '%'
}
const priceColor = (row) => {
  const v = Number(row?.changePercent)
  if (Number.isNaN(v) || v === 0) return 'text-gray-300'
  return v > 0 ? 'text-red-400 font-mono font-semibold' : 'text-green-400 font-mono font-semibold'
}
const rowClass = ({ row }) => {
  return selected.value && selected.value.stockCode === row.stockCode ? 'is-selected-row' : ''
}

const loadQuotes = async () => {
  loadingQuotes.value = true
  try {
    const res = await request.get('/stock/quotes')
    if (res.code === 200) {
      quotes.value = res.data || []
      // 若当前选中股票在列表内，刷新选中数据
      if (selected.value) {
        const fresh = quotes.value.find(q => q.stockCode === selected.value.stockCode)
        if (fresh) selected.value = fresh
      } else if (quotes.value.length > 0) {
        // 默认选中第一只并加载 K 线
        await handleSelect(quotes.value[0])
      }
    }
  } catch (e) {
    console.error(e)
  } finally {
    loadingQuotes.value = false
  }
}

const handleSelect = async (row) => {
  if (!row || !row.stockCode) return
  selected.value = row
  await loadKline()
}

const loadKline = async () => {
  if (!selected.value) return
  loadingKline.value = true
  try {
    const res = await request.get(`/stock/kline/${selected.value.stockCode}`, {
      params: { limit: klineLimit.value }
    })
    if (res.code === 200) {
      renderKline(res.data || [])
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('K 线加载失败')
  } finally {
    loadingKline.value = false
  }
}

const renderKline = (points) => {
  if (!klineRef.value) return
  if (!klineChart) {
    klineChart = echarts.init(klineRef.value, 'dark')
  }

  const dates = points.map(p => p.tradeDate)
  // ECharts candlestick 数据格式: [open, close, low, high]
  const candles = points.map(p => [
    Number(p.openPrice),
    Number(p.closePrice),
    Number(p.lowPrice),
    Number(p.highPrice)
  ])
  const ma = (n) => {
    const arr = []
    for (let i = 0; i < points.length; i++) {
      if (i < n - 1) { arr.push('-'); continue }
      let sum = 0
      for (let j = 0; j < n; j++) sum += Number(points[i - j].closePrice)
      arr.push((sum / n).toFixed(2))
    }
    return arr
  }

  klineChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(20,20,25,0.9)',
      borderColor: '#2c2c33',
      textStyle: { color: '#e5e7eb' }
    },
    legend: {
      data: ['日K', 'MA5', 'MA20'],
      textStyle: { color: '#9ca3af' },
      top: 0
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: true,
      axisLine: { lineStyle: { color: '#3f3f46' } },
      axisLabel: { color: '#6b7280', fontSize: 10 }
    },
    yAxis: {
      scale: true,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: '#6b7280' }
    },
    dataZoom: [
      { type: 'inside', start: 50, end: 100 },
      { type: 'slider', height: 18, bottom: 6, start: 50, end: 100,
        textStyle: { color: '#6b7280' }, borderColor: '#27272a',
        fillerColor: 'rgba(59,130,246,0.15)' }
    ],
    series: [
      {
        name: '日K',
        type: 'candlestick',
        data: candles,
        itemStyle: {
          color: '#ef4444',          // 阳线 红
          color0: '#22c55e',         // 阴线 绿
          borderColor: '#ef4444',
          borderColor0: '#22c55e'
        }
      },
      {
        name: 'MA5',
        type: 'line',
        data: ma(5),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#3b82f6' }
      },
      {
        name: 'MA20',
        type: 'line',
        data: ma(20),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#f59e0b' }
      }
    ]
  }, true)

  nextTick(() => klineChart && klineChart.resize())
}

const handleResize = () => {
  klineChart && klineChart.resize()
}

onMounted(async () => {
  await loadQuotes()
  // 30 秒自动刷新
  timer = setInterval(loadQuotes, 30000)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  if (klineChart) {
    klineChart.dispose()
    klineChart = null
  }
})
</script>

<style scoped>
.chart-card {
  position: relative;
  background-color: rgba(12, 12, 14, 0.8);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.25rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.chart-card:hover { border-color: #3f3f46; }

/* 行情表样式 */
:deep(.market-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(59,130,246,0.08);
  --el-table-border-color: rgba(255,255,255,0.04);
  --el-table-text-color: #d1d5db;
  --el-table-header-text-color: #9ca3af;
}
:deep(.market-table .el-table__row) { cursor: pointer; }
:deep(.market-table .is-selected-row > td) {
  background: linear-gradient(90deg, rgba(59,130,246,0.18), rgba(59,130,246,0.04)) !important;
}
:deep(.market-table th.el-table__cell) {
  background: rgba(24,24,27,0.4) !important;
  border-bottom: 1px solid rgba(255,255,255,0.05) !important;
  font-size: 12px;
  letter-spacing: 1px;
}
</style>

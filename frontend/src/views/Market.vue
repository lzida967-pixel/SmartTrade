<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans selection:bg-blue-500/30">
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">

      <!-- 顶部页签 -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div class="text-xs text-gray-500 tracking-widest font-mono">行情 · 实时</div>
          <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
            <el-icon class="text-blue-400"><TrendCharts /></el-icon>
            行情中心
          </div>
        </div>
        <div class="flex items-center gap-3 flex-wrap">
          <el-input
            v-model="search"
            placeholder="输入股票代码或名称搜索"
            clearable
            size="default"
            class="!w-72"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" plain :loading="loadingQuotes" @click="loadQuotes">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新行情
          </el-button>
          <div class="text-xs text-gray-500 font-mono">
            数据源：东方财富 · 自动刷新 30 秒
          </div>
        </div>
      </div>

      <!-- 行情列表 -->
      <div class="chart-card overflow-hidden">
        <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center flex-wrap gap-3">
          <div class="flex items-center gap-4 flex-wrap">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <DataLine class="w-4 h-4" /> 沪深主流股票实时行情
            </span>
            <!-- 全部 / 自选 切换 -->
            <div class="market-tabs">
              <button class="market-tab" :class="{ active: tab === 'all' }" @click="tab = 'all'">
                <span>全部</span>
                <span class="tab-count">{{ quotes.length }}</span>
              </button>
              <button class="market-tab" :class="{ active: tab === 'fav' }" @click="tab = 'fav'">
                <el-icon class="text-amber-400"><Star /></el-icon>
                <span>自选</span>
                <span class="tab-count">{{ watchlist.size }}</span>
              </button>
            </div>
          </div>
          <span class="text-[10px] text-gray-500 font-mono">
            <span v-if="search">筛选 {{ filteredQuotes.length }} / </span>共 {{ filteredQuotes.length }} 支 · 点击行查看 K 线
          </span>
        </div>

        <div class="relative">
          <div v-if="loadingQuotes" class="absolute inset-0 z-10 flex items-center justify-center bg-black/40 backdrop-blur-sm rounded-lg">
            <div class="text-cyan-400 text-xs tracking-widest font-mono flex items-center gap-2">
              <el-icon class="animate-spin"><Loading /></el-icon> 行情加载中...
            </div>
          </div>
          <el-table
            :data="filteredQuotes"
            highlight-current-row
            @row-click="handleSelect"
            stripe
            max-height="720"
            :empty-text="tab === 'fav' ? '还没收藏自选股 · 点击 ⭐ 加入' : '未找到匹配的股票'"
            style="width: 100%; background: transparent;"
            class="market-table"
          >
            <el-table-column label="" width="50" align="center">
              <template #default="{ row }">
                <button class="star-btn"
                        :class="{ active: watchlist.has(row.stockCode) }"
                        @click.stop="toggleWatch(row.stockCode)"
                        :title="watchlist.has(row.stockCode) ? '取消自选' : '加入自选'">
                  <el-icon v-if="watchlist.has(row.stockCode)"><StarFilled /></el-icon>
                  <el-icon v-else><Star /></el-icon>
                </button>
              </template>
            </el-table-column>
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
    </main>

    <!-- K 线弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      width="90%"
      top="5vh"
      :show-close="true"
      :close-on-click-modal="false"
      :destroy-on-close="true"
      append-to-body
      class="kline-dialog"
      @opened="onDialogOpened"
      @closed="onDialogClosed"
    >
      <template #header>
        <div class="flex items-center justify-between pr-8 gap-3 flex-wrap">
          <div class="flex items-center gap-3">
            <el-icon class="text-cyan-400"><DataAnalysis /></el-icon>
            <span class="text-base text-gray-100 font-bold">{{ selected?.stockName || '--' }}</span>
            <span class="text-gray-500 text-xs font-mono">{{ selected?.stockCode }}</span>
            <span class="text-gray-500 text-xs">{{ selected?.industryName || selected?.plateType || '' }}</span>
          </div>
          <div class="flex items-center gap-3">
            <div v-if="selected" :class="priceColor(selected)" class="font-mono text-base">
              {{ formatNum(selected.latestPrice) }}
              <span class="text-xs ml-1">{{ formatPercent(selected.changePercent) }}</span>
            </div>
            <el-button size="small" type="danger" @click.stop="openOrder('BUY')">买入</el-button>
            <el-button size="small" type="success" @click.stop="openOrder('SELL')">卖出</el-button>
          </div>
        </div>
      </template>

      <div v-if="selected" class="flex flex-col gap-4">
        <!-- 关键数据展示卡片 -->
        <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
          <div class="stat-cell"><div class="stat-label">今开</div><div class="stat-val">{{ formatNum(selected.openPrice) }}</div></div>
          <div class="stat-cell"><div class="stat-label">昨收</div><div class="stat-val">{{ formatNum(selected.preClosePrice) }}</div></div>
          <div class="stat-cell"><div class="stat-label">最高</div><div class="stat-val text-red-400">{{ formatNum(selected.highPrice) }}</div></div>
          <div class="stat-cell"><div class="stat-label">最低</div><div class="stat-val text-green-400">{{ formatNum(selected.lowPrice) }}</div></div>
          <div class="stat-cell"><div class="stat-label">涨跌额</div><div class="stat-val" :class="priceColor(selected)">{{ formatNum(selected.changeAmount) }}</div></div>
          <div class="stat-cell"><div class="stat-label">换手率</div><div class="stat-val">{{ formatPercent(selected.turnoverRate) }}</div></div>
          <div class="stat-cell"><div class="stat-label">成交额</div><div class="stat-val">{{ formatAmount(selected.turnoverAmount) }}</div></div>
        </div>

        <!-- K 线工具栏 -->
        <div class="flex items-center justify-between border-t border-white/5 pt-3">
          <span class="text-xs font-semibold tracking-widest text-cyan-400">日 K 线（前复权）</span>
          <el-radio-group v-model="klineLimit" size="small" @change="loadKline">
            <el-radio-button :value="60">60 日</el-radio-button>
            <el-radio-button :value="120">120 日</el-radio-button>
            <el-radio-button :value="250">1 年</el-radio-button>
          </el-radio-group>
        </div>

        <!-- K 线区域 -->
        <div class="relative w-full" style="height:65vh">
          <div ref="klineRef" class="absolute inset-0"></div>
          <div v-if="loadingKline" class="absolute inset-0 z-10 flex items-center justify-center bg-black/40 backdrop-blur-sm">
            <div class="text-cyan-400 text-xs tracking-widest font-mono flex items-center gap-2">
              <el-icon class="animate-spin"><Loading /></el-icon> K 线加载中...
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 下单弹窗 -->
    <OrderDialog
      v-model="orderDialogVisible"
      :stock-code="selected?.stockCode || ''"
      :stock-name="selected?.stockName || ''"
      :latest-price="selected?.latestPrice || 0"
      :change-percent="selected?.changePercent || 0"
      :default-direction="orderDirection"
      :available-funds="availableFunds"
      :available-qty="availableQty"
      @placed="onOrderPlaced"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  TrendCharts, DataLine, DataAnalysis, Refresh, Loading, Search,
  Star, StarFilled
} from '@element-plus/icons-vue'
import request from '../utils/request'
import OrderDialog from '../components/OrderDialog.vue'
import { useWatchlistStore } from '../stores/watchlist'

const route = useRoute()
const router = useRouter()
const watchlist = useWatchlistStore()

const quotes = ref([])
const selected = ref(null)
const klineLimit = ref(120)
const loadingQuotes = ref(false)
const loadingKline = ref(false)
const search = ref('')
const tab = ref('all')   // 'all' | 'fav'
const dialogVisible = ref(false)

// 切换自选
const toggleWatch = async (code) => {
  const nowFav = await watchlist.toggle(code)
  ElMessage.success(nowFav ? '已加入自选' : '已移除自选')
}
// 缓存最近一次拉取到的 K 线数据，dialog 打开时 onOpened 钩子触发渲染
let pendingKlinePoints = null

// 下单弹窗相关状态
const orderDialogVisible = ref(false)
const orderDirection = ref('BUY')
const availableFunds = ref(0)
const availableQty = ref(0)

const openOrder = async (direction) => {
  orderDirection.value = direction
  // 拉取用户资产 + 该股可卖持仓
  try {
    const [assetRes, posRes] = await Promise.all([
      request.get('/user/asset'),
      request.get('/trade/positions')
    ])
    if (assetRes.code === 200) {
      availableFunds.value = Number(assetRes.data?.availableFunds || 0)
    }
    if (posRes.code === 200) {
      const pos = (posRes.data || []).find(p => p.stockCode === selected.value?.stockCode)
      availableQty.value = pos ? (pos.availableQuantity || 0) : 0
    }
  } catch (e) { /* ignore */ }
  orderDialogVisible.value = true
}

const onOrderPlaced = () => {
  // 下单成功后刷新一次行情，使弹窗内的最新价/涨跌幅更新
  loadQuotes()
}

const filteredQuotes = computed(() => {
  const kw = search.value.trim().toLowerCase()
  // 1) 先按 tab（全部 / 自选）过滤
  let base = tab.value === 'fav'
    ? quotes.value.filter(q => watchlist.has(q.stockCode))
    : quotes.value
  // 2) 再按搜索关键字过滤
  if (!kw) return base
  return base.filter(q => {
    return (q.stockCode && q.stockCode.toLowerCase().includes(kw))
        || (q.stockName && q.stockName.toLowerCase().includes(kw))
        || (q.industryName && q.industryName.toLowerCase().includes(kw))
  })
})

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
const formatAmount = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (n >= 1e4) return (n / 1e4).toFixed(2) + ' 万'
  return n.toFixed(0)
}
const priceColor = (row) => {
  const v = Number(row?.changePercent)
  if (Number.isNaN(v) || v === 0) return 'text-gray-300'
  return v > 0 ? 'text-red-400 font-mono font-semibold' : 'text-green-400 font-mono font-semibold'
}
const loadQuotes = async () => {
  loadingQuotes.value = true
  try {
    const res = await request.get('/stock/quotes')
    if (res.code === 200) {
      quotes.value = res.data || []
      // 若 dialog 打开中，同步刷新选中股票的最新行情数据
      if (selected.value) {
        const fresh = quotes.value.find(q => q.stockCode === selected.value.stockCode)
        if (fresh) selected.value = fresh
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
  // 重置周期为默认值
  klineLimit.value = 120
  dialogVisible.value = true
  await loadKline()
}

const onDialogOpened = () => {
  // dialog 打开后 DOM 才挂载，此时再渲染 ECharts
  if (pendingKlinePoints) {
    renderKline(pendingKlinePoints)
    pendingKlinePoints = null
  }
}

const onDialogClosed = () => {
  if (klineChart) {
    klineChart.dispose()
    klineChart = null
  }
}

const loadKline = async () => {
  if (!selected.value) return
  loadingKline.value = true
  try {
    const res = await request.get(`/stock/kline/${selected.value.stockCode}`, {
      params: { limit: klineLimit.value }
    })
    if (res.code === 200) {
      const points = res.data || []
      // dialog 还没真正打开（DOM 未渲染），先暂存，等 onOpened 钩子时再渲染
      if (klineRef.value) {
        renderKline(points)
      } else {
        pendingKlinePoints = points
      }
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
      backgroundColor: 'rgba(20,20,25,0.92)',
      borderColor: '#2c2c33',
      textStyle: { color: '#e5e7eb', fontSize: 12 },
      formatter: (params) => {
        if (!params || params.length === 0) return ''
        const date = params[0].axisValueLabel || params[0].name
        let html = `<div style="font-weight:600;margin-bottom:6px;color:#fff">${date}</div>`
        for (const p of params) {
          if (p.seriesType === 'candlestick') {
            // candlestick 在 axis tooltip 时 data 格式为 [index, open, close, low, high]
            const o = p.data[1]
            const c = p.data[2]
            const l = p.data[3]
            const h = p.data[4]
            const up = Number(c) >= Number(o)
            const color = up ? '#ef4444' : '#22c55e'
            html += `<div style="display:flex;align-items:center;gap:6px;margin:2px 0">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color}"></span>
              <span>日K</span>
            </div>`
            html += `<div style="margin-left:14px">开盘 <b>${o}</b></div>`
            html += `<div style="margin-left:14px">收盘 <b style="color:${color}">${c}</b></div>`
            html += `<div style="margin-left:14px">最低 <b>${l}</b></div>`
            html += `<div style="margin-left:14px">最高 <b>${h}</b></div>`
          } else {
            const val = (p.value === '-' || p.value === undefined || p.value === null) ? '--' : p.value
            html += `<div style="margin:2px 0">${p.marker}${p.seriesName} <b>${val}</b></div>`
          }
        }
        return html
      }
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

/**
 * 跳转或刷新进入本页时，若 URL 带 ?stockCode=xxx 则自动打开该股 K 线
 */
const openByQueryCode = async (code) => {
  if (!code) return
  let row = quotes.value.find(q => q.stockCode === code)
  if (!row) {
    if (!quotes.value.length) {
      await loadQuotes()
    }
    row = quotes.value.find(q => q.stockCode === code)
  }
  if (row) {
    await handleSelect(row)
  } else {
    ElMessage.warning(`未在股票池中找到 ${code}`)
  }
  // 清掉 query 避免再次触发或刷新时重弹
  router.replace({ path: '/market', query: {} })
}

onMounted(async () => {
  // 并行：行情列表 + 自选列表
  await Promise.all([loadQuotes(), watchlist.refresh()])
  // 30 秒自动刷新
  timer = setInterval(loadQuotes, 30000)
  window.addEventListener('resize', handleResize)
  // 处理 ?stockCode=xxx 参数
  if (route.query.stockCode) {
    await openByQueryCode(String(route.query.stockCode))
  }
})

// 已挂载状态下二次跳转（持仓 → 行情 → 委托单 → 行情）也要响应
watch(() => route.query.stockCode, (code) => {
  if (code) openByQueryCode(String(code))
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
/* ============ 全部/自选 tabs ============ */
.market-tabs {
  display: inline-flex;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}
.market-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all .15s;
}
.market-tab:hover {
  color: #d1d5db;
}
.market-tab.active {
  background: rgba(96, 165, 250, 0.12);
  color: #93c5fd;
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.3) inset;
}
.market-tab .tab-count {
  font-size: 10px;
  font-family: ui-monospace, monospace;
  padding: 1px 6px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: #9ca3af;
  min-width: 20px;
  text-align: center;
}
.market-tab.active .tab-count {
  background: rgba(96, 165, 250, 0.2);
  color: #bfdbfe;
}

/* ============ 自选星标按钮 ============ */
.star-btn {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all .15s ease;
}
.star-btn:hover {
  background: rgba(251, 191, 36, 0.08);
  color: #fbbf24;
  transform: scale(1.15);
}
.star-btn.active {
  color: #fbbf24;
}
.star-btn.active:hover {
  color: #f59e0b;
}

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
:deep(.market-table th.el-table__cell) {
  background: rgba(24,24,27,0.4) !important;
  border-bottom: 1px solid rgba(255,255,255,0.05) !important;
  font-size: 12px;
  letter-spacing: 1px;
}

/* 弹窗内统计单元 */
.stat-cell {
  background: rgba(11, 14, 22, 0.65);
  border: 1px solid rgba(96, 165, 250, 0.12);
  border-radius: 0.5rem;
  padding: 0.55rem 0.75rem;
}
.stat-label {
  font-size: 11px;
  color: #6b7280;
  letter-spacing: 1px;
  margin-bottom: 2px;
}
.stat-val {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  color: #e5e7eb;
  font-weight: 600;
}

/* K 线 Dialog 主题适配（背景比列表卡片更亮 + 蓝调辉光，避免与列表混淆） */
:deep(.kline-dialog) {
  --el-dialog-bg-color: #161a26;
  background: linear-gradient(160deg, #1b2030 0%, #161a26 60%, #11141d 100%) !important;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  box-shadow:
    0 0 0 1px rgba(96, 165, 250, 0.08),
    0 30px 60px -20px rgba(0, 0, 0, 0.8),
    0 0 80px -10px rgba(59, 130, 246, 0.25);
  overflow: hidden;
}
:deep(.kline-dialog .el-dialog__header) {
  border-bottom: 1px solid rgba(96, 165, 250, 0.15);
  background: linear-gradient(90deg, rgba(59,130,246,0.08), transparent);
  margin-right: 0;
  padding: 16px 20px;
}
:deep(.kline-dialog .el-dialog__body) {
  padding: 18px 20px 22px;
  color: #d1d5db;
  background: transparent;
}
:deep(.kline-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #9ca3af;
  font-size: 18px;
}
</style>

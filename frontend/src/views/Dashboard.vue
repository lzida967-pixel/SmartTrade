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
          <div class="flex items-center gap-3 flex-wrap">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <DataLine class="w-4 h-4" /> 资产收益曲线
            </span>
            <el-tooltip placement="top" effect="dark">
              <template #content>
                <div class="text-xs leading-relaxed">
                  <div>· 实线：每日 <b>16:35 收盘后</b>定格的总资产快照（仅历史，不含今日）</div>
                  <div>· 虚线段：从最近一笔历史快照延伸至 <b>当前实时总资产</b></div>
                  <div>· 16:35 之后今日点会变成实线（真收盘价定格）</div>
                </div>
              </template>
              <span class="text-[10px] text-gray-500 flex items-center gap-1 cursor-help">
                <InfoFilled class="w-3 h-3" />
                历史定格 · 今日实时
              </span>
            </el-tooltip>
          </div>
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
          <div class="border-b border-white/5 pb-3 mb-4 flex items-center justify-between">
            <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
              <PieChart class="w-4 h-4" /> 持仓分布
              <span v-if="pieData.length" class="text-[10px] text-gray-500 font-normal tracking-normal">
                共 {{ pieData.length }} 只
              </span>
            </span>
            <button v-if="pieData.length"
                    class="text-[11px] text-gray-400 hover:text-cyan-400 flex items-center gap-1 transition-colors"
                    @click="openPieDialog"
                    title="放大查看">
              <FullScreen class="w-3.5 h-3.5" />
              <span>放大</span>
            </button>
          </div>
          <div class="relative w-full cursor-zoom-in" style="height:300px"
               @click="pieData.length && openPieDialog()">
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

    <!-- 持仓分布放大弹窗 -->
    <el-dialog
      v-model="pieDialogVisible"
      width="min(1100px, 92vw)"
      align-center
      destroy-on-close
      :show-close="true"
      class="position-pie-dialog"
    >
      <template #header>
        <div class="flex items-center gap-2">
          <PieChart class="w-4 h-4 text-cyan-400" />
          <span class="text-base font-semibold tracking-wider text-cyan-400">持仓分布详情</span>
          <span class="text-xs text-gray-500">共 {{ pieData.length }} 只 · 总市值 ¥ {{ formatNum(totalMarketValue) }}</span>
        </div>
      </template>
      <div class="grid grid-cols-1 lg:grid-cols-5 gap-4">
        <!-- 大饼图 -->
        <div class="lg:col-span-3 relative" style="height: 520px">
          <div ref="pieBigRef" class="absolute inset-0"></div>
        </div>
        <!-- 详情列表 -->
        <div class="lg:col-span-2 overflow-auto" style="max-height: 520px">
          <table class="w-full text-xs">
            <thead class="sticky top-0 bg-[#0c1018] z-10">
              <tr class="text-gray-500 border-b border-white/5">
                <th class="text-left py-2 px-2 font-medium">股票</th>
                <th class="text-right py-2 px-2 font-medium">市值</th>
                <th class="text-right py-2 px-2 font-medium">占比</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in pieDataSorted" :key="item.name"
                  class="border-b border-white/5 hover:bg-white/[0.02]">
                <td class="py-2 px-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <span class="w-2.5 h-2.5 rounded-sm flex-shrink-0"
                          :style="{ background: pieColors[idx % pieColors.length] }"></span>
                    <span class="text-gray-200 truncate">{{ item.name }}</span>
                  </div>
                </td>
                <td class="py-2 px-2 text-right text-gray-300 font-mono">¥ {{ formatNum(item.value) }}</td>
                <td class="py-2 px-2 text-right text-cyan-400 font-mono">
                  {{ ((item.value / totalMarketValue) * 100).toFixed(2) }}%
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import {
  Wallet, Money, DataAnalysis, DataLine, TrendCharts,
  Refresh, PieChart, List, FullScreen, InfoFilled
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
const pieBigRef = ref(null)
let curveChart = null
let pieChart = null
let pieBigChart = null

// 弹窗显示
const pieDialogVisible = ref(false)

// 调色板（高对比、暗底友好），多于 16 只时复用循环
const pieColors = [
  '#60a5fa', '#34d399', '#fbbf24', '#f472b6', '#a78bfa',
  '#22d3ee', '#fb923c', '#84cc16', '#e879f9', '#f87171',
  '#818cf8', '#2dd4bf', '#facc15', '#fb7185', '#c084fc',
  '#38bdf8'
]

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
      // 当日收益的"基数" = 最后一笔"非今天"的快照（即昨日真收盘）
      const now = new Date()
      const todayStr =
        now.getFullYear() + '-' +
        String(now.getMonth() + 1).padStart(2, '0') + '-' +
        String(now.getDate()).padStart(2, '0')
      const yesterdaySnap = [...curve.value]
        .reverse()
        .find(p => String(p.snapshotDate) !== todayStr)
      if (yesterdaySnap) {
        asset.value.dailyProfitBase = Number(yesterdaySnap.totalAssets) || 0
        asset.value.dailyProfit = Number(asset.value.totalAssets) - asset.value.dailyProfitBase
      } else if (curve.value.length > 0) {
        // 整个区间只有今天一条 → 没有基数可比
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
    // loadCurve 完成时 totalAssets 可能还没拿到；这里再补刷一次让虚线段能拼上
    renderCurve()
  } finally {
    loading.value = false
  }
}

// ============== ECharts 渲染 ==============
const renderCurve = () => {
  if (!curveRef.value) return
  if (!curveChart) curveChart = echarts.init(curveRef.value, 'dark')

  // 今天的"快照"在 16:35 前其实是冷启动时拍的盘中数据，不是真正的收盘价。
  // 为了语义干净：盘中过滤掉今天那行，把今天的位置完全交给"今日实时"虚线段。
  // 16:35 之后或非交易日 today's snapshot 就是真收盘价，可以保留为实线末点。
  const now = new Date()
  const todayStr =
    now.getFullYear() + '-' +
    String(now.getMonth() + 1).padStart(2, '0') + '-' +
    String(now.getDate()).padStart(2, '0')
  const isAfterClose = now.getHours() > 16 || (now.getHours() === 16 && now.getMinutes() >= 35)

  // 过滤：盘中(or 收盘前)且最后一行就是今天，则丢掉
  const rawList = curve.value || []
  const filtered = rawList.filter((p, i) => {
    if (i !== rawList.length - 1) return true
    return !(String(p.snapshotDate) === todayStr && !isAfterClose)
  })

  const realDates = filtered.map(p => p.snapshotDate)
  const realValues = filtered.map(p => Number(p.totalAssets))
  const live = Number(asset.value.totalAssets) || 0
  const lastIdx = realValues.length - 1

  // 是否要延伸：必须有至少一个快照点 + 已经拿到实时值
  const showLive = lastIdx >= 0 && live > 0

  // x 轴：在末尾追加"今日实时"标签
  const dates = showLive ? [...realDates, '今日实时'] : realDates

  // 主曲线（实线）：仅历史快照点，末位补 null 阻止实线连到"今日实时"
  const realSeries = showLive ? [...realValues, null] : realValues

  // 延伸曲线（虚线）：从最后历史点 → 今日实时
  const liveSeries = showLive
    ? [...new Array(lastIdx).fill(null), realValues[lastIdx], live]
    : []

  curveChart.setOption({
    backgroundColor: 'transparent',
    grid: { top: 30, left: 60, right: 60, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,24,35,0.95)',
      borderColor: 'rgba(96,165,250,0.4)',
      textStyle: { color: '#e5e7eb' },
      formatter: (params) => {
        // params 中可能既有实线点也有虚线点；取非空那个
        const p = params.find(x => x.value !== null && x.value !== undefined)
        if (!p) return ''
        const isLive = p.seriesName === '今日实时'
        const tag = isLive
          ? '<span style="color:#fbbf24">● 今日实时</span>'
          : '<span style="color:#60a5fa">● 收盘快照</span>'
        return `${p.axisValue}<br/>${tag}<br/>总资产 <b style="color:#f97316">¥ ${Number(p.value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</b>`
      }
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: {
        color: '#9ca3af', fontSize: 11,
        formatter: (v) => v === '今日实时'
          ? '{live|今日实时}'
          : v,
        rich: {
          live: {
            color: '#fbbf24',
            fontWeight: 600,
            fontSize: 11
          }
        }
      }
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
    series: [
      {
        // 主线：收盘快照
        name: '收盘快照',
        type: 'line',
        data: realSeries,
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
      },
      {
        // 虚线段：今日实时
        name: '今日实时',
        type: 'line',
        data: liveSeries,
        smooth: false,
        symbol: 'circle',
        symbolSize: 7,
        // 仅在实时点显示标记
        showSymbol: true,
        lineStyle: { color: '#fbbf24', width: 2, type: 'dashed' },
        itemStyle: { color: '#fbbf24', borderColor: '#0c0c10', borderWidth: 2 },
        // 在末点上加发光效果
        emphasis: { focus: 'series' },
        // 末点上方加标签：实时值（向上偏移避免和点重叠）
        markPoint: showLive ? {
          symbol: 'rect',
          symbolSize: [0, 0],          // 隐形容器，只用它承载 label
          symbolOffset: [0, -22],      // 整体上移 22px
          label: {
            show: true,
            formatter: '¥ ' + Number(live).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
            color: '#fbbf24',
            backgroundColor: 'rgba(251,191,36,0.12)',
            borderColor: 'rgba(251,191,36,0.5)',
            borderWidth: 1,
            borderRadius: 4,
            padding: [3, 6],
            fontSize: 11,
            fontWeight: 600
          },
          data: [{ coord: [dates.length - 1, live] }]
        } : undefined
      }
    ]
  }, true)
}

// 持仓饼图数据：仅含市值 > 0 的持仓；为空但有可用资金时占位
const pieData = computed(() => {
  const data = positions.value
    .filter(p => Number(p.marketValue) > 0)
    .map(p => ({
      name: stockName(p.stockCode) || p.stockCode,
      value: Number(p.marketValue)
    }))
  if (data.length === 0 && asset.value.availableFunds > 0) {
    data.push({ name: '可用资金', value: asset.value.availableFunds })
  }
  return data
})
const pieDataSorted = computed(() =>
  [...pieData.value].sort((a, b) => b.value - a.value)
)
const totalMarketValue = computed(() =>
  pieData.value.reduce((s, x) => s + x.value, 0)
)

/**
 * 构建 ECharts 饼图配置
 * @param mode 'small' 小卡片 | 'big' 弹窗放大
 */
const buildPieOption = (mode = 'small') => {
  const data = pieDataSorted.value
  const total = totalMarketValue.value
  const big = mode === 'big'

  return {
    backgroundColor: 'transparent',
    color: pieColors,
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(20,24,35,0.95)',
      borderColor: 'rgba(96,165,250,0.4)',
      textStyle: { color: '#e5e7eb' },
      formatter: (p) =>
        `<div style="font-weight:600">${p.name}</div>` +
        `¥ ${Number(p.value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` +
        ` <span style="color:#60a5fa">(${p.percent}%)</span>`
    },
    legend: big
      ? { show: false }                  // 大图右侧已有详情列表，不需要 legend
      : {
          show: data.length > 0 && data.length <= 8,  // 大于 8 只时图例会挤，干脆隐藏
          type: 'scroll',
          orient: 'horizontal',
          bottom: 0,
          left: 'center',
          textStyle: { color: '#9ca3af', fontSize: 11 },
          itemWidth: 10, itemHeight: 10,
          pageIconColor: '#60a5fa',
          pageTextStyle: { color: '#9ca3af' }
        },
    series: [{
      type: 'pie',
      radius: big ? ['38%', '70%'] : ['52%', '78%'],
      center: ['50%', big ? '50%' : (data.length > 8 ? '50%' : '44%')],
      avoidLabelOverlap: true,
      minAngle: big ? 2 : 4,                 // 太小的扇区合并感更弱
      itemStyle: {
        borderColor: 'rgba(0,0,0,0.5)',
        borderWidth: 2,
        borderRadius: 2
      },
      label: big
        ? {
            // 大图允许显示外标签
            show: true,
            position: 'outside',
            formatter: (p) => `${p.name}\n${p.percent}%`,
            color: '#d1d5db',
            fontSize: 12,
            lineHeight: 16
          }
        : {
            // 小图：默认不显示扇区上的标签，太挤
            // 仅在中心显示总市值
            show: false
          },
      labelLine: big
        ? { show: true, length: 12, length2: 14, lineStyle: { color: 'rgba(255,255,255,0.2)' } }
        : { show: false },
      emphasis: {
        scale: true,
        scaleSize: big ? 8 : 5,
        label: big ? {} : {
          // 小图 hover 时在中心显示
          show: true,
          position: 'center',
          formatter: (p) => `{n|${p.name}}\n{v|¥ ${formatNum(p.value)}}\n{r|${p.percent}%}`,
          rich: {
            n: { color: '#e5e7eb', fontSize: 13, fontWeight: 600, padding: [0, 0, 4, 0] },
            v: { color: '#60a5fa', fontSize: 12, fontFamily: 'ui-monospace, monospace' },
            r: { color: '#9ca3af', fontSize: 11, padding: [4, 0, 0, 0] }
          }
        },
        itemStyle: {
          shadowBlur: 20,
          shadowColor: 'rgba(96, 165, 250, 0.5)'
        }
      },
      data
    }],
    // 小图正中显示总览（默认态）
    graphic: big ? [] : [
      {
        type: 'group',
        left: 'center',
        top: data.length > 8 ? 'middle' : '36%',
        children: [
          {
            type: 'text',
            top: 0,
            left: 'center',
            style: {
              text: '总市值',
              fill: '#9ca3af',
              fontSize: 11
            }
          },
          {
            type: 'text',
            top: 16,
            left: 'center',
            style: {
              text: '¥ ' + formatNum(total),
              fill: '#60a5fa',
              fontSize: 14,
              fontWeight: 600,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace'
            }
          },
          {
            type: 'text',
            top: 36,
            left: 'center',
            style: {
              text: data.length + ' 只持仓',
              fill: '#6b7280',
              fontSize: 10
            }
          }
        ]
      }
    ]
  }
}

const renderPie = () => {
  if (!pieRef.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value, 'dark')
  pieChart.setOption(buildPieOption('small'), true)
}

const renderPieBig = () => {
  if (!pieBigRef.value) return
  if (!pieBigChart) pieBigChart = echarts.init(pieBigRef.value, 'dark')
  pieBigChart.setOption(buildPieOption('big'), true)
}

const openPieDialog = () => {
  pieDialogVisible.value = true
}

// 弹窗打开后再渲染（DOM 此时才挂上）
watch(pieDialogVisible, (val) => {
  if (val) {
    nextTick(() => {
      renderPieBig()
      pieBigChart?.resize()
    })
  } else {
    pieBigChart?.dispose()
    pieBigChart = null
  }
})

// 响应窗口大小变化
const onResize = () => {
  curveChart?.resize()
  pieChart?.resize()
  pieBigChart?.resize()
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

/* 持仓分布放大弹窗暗色 */
:deep(.position-pie-dialog) {
  background: linear-gradient(180deg, #0c1018 0%, #0a0d14 100%);
  border: 1px solid rgba(96, 165, 250, 0.2);
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6);
}
:deep(.position-pie-dialog .el-dialog__header) {
  padding: 16px 20px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  margin-right: 0;
}
:deep(.position-pie-dialog .el-dialog__body) {
  padding: 20px;
}
:deep(.position-pie-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #9ca3af;
  font-size: 18px;
}
:deep(.position-pie-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #e5e7eb;
}
</style>

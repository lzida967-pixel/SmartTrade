<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><Odometer /></el-icon></span>仪表盘</h2>
        <div class="pt-sub">系统总览 · 用户 / 股票池 / 订单 / 资金</div>
      </div>
      <div>
        <el-button type="primary" plain :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 顶部 4 张主指标卡 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(99,102,241,.15); color:#a5b4fc"><el-icon><User /></el-icon></div>
        <div>
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ data.user?.total || 0 }}</div>
          <div class="text-[11px] text-gray-500 mt-1">
            管理员 <span class="text-amber-300">{{ data.user?.admin || 0 }}</span>
            · 启用 <span class="text-emerald-300">{{ data.user?.active || 0 }}</span>
          </div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(59,130,246,.12); color:#60a5fa"><el-icon><Wallet /></el-icon></div>
        <div>
          <div class="stat-label">用户可用资金合计</div>
          <div class="stat-value" style="color:#60a5fa">¥{{ formatNum(data.user?.availableFundsSum) }}</div>
          <div class="text-[11px] text-gray-500 mt-1">来源：所有用户 availableFunds 之和</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(16,185,129,.12); color:#34d399"><el-icon><Coin /></el-icon></div>
        <div>
          <div class="stat-label">股票池</div>
          <div class="stat-value">{{ data.stock?.total || 0 }}</div>
          <div class="text-[11px] text-gray-500 mt-1">
            有日 K <span class="text-emerald-300">{{ data.stock?.withData || 0 }}</span>
            · 缺数据
            <span :class="(data.stock?.missing || 0) > 0 ? 'text-rose-300' : 'text-gray-400'">
              {{ data.stock?.missing || 0 }}
            </span>
          </div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(245,158,11,.12); color:#fbbf24"><el-icon><List /></el-icon></div>
        <div>
          <div class="stat-label">今日订单</div>
          <div class="stat-value" style="color:#fbbf24">{{ data.order?.today || 0 }}</div>
          <div class="text-[11px] text-gray-500 mt-1">
            待成交 <span class="text-amber-300">{{ data.order?.pending || 0 }}</span>
            · 已成交 <span class="text-emerald-300">{{ data.order?.filled || 0 }}</span>
            · 已撤 <span class="text-rose-300">{{ data.order?.canceled || 0 }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 中部：近7日订单趋势 + 缺数据股票 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-4">
      <div class="admin-card p-5 lg:col-span-2">
        <div class="flex items-center justify-between mb-3">
          <div class="text-sm text-gray-200 font-medium flex items-center gap-2">
            <el-icon class="text-indigo-400"><TrendCharts /></el-icon>近 7 日订单数趋势
          </div>
          <div class="text-[11px] text-gray-500 font-mono">{{ trendTotal }} 单</div>
        </div>
        <div ref="trendRef" class="w-full h-[260px]"></div>
      </div>

      <div class="admin-card p-5">
        <div class="flex items-center justify-between mb-3">
          <div class="text-sm text-gray-200 font-medium flex items-center gap-2">
            <el-icon class="text-rose-400"><Warning /></el-icon>缺数据股票
          </div>
          <router-link to="/admin/stocks" class="text-[11px] text-indigo-300 hover:text-indigo-200">
            前往处理 ›
          </router-link>
        </div>
        <div v-if="!(data.missingStocks || []).length" class="text-xs text-gray-500 py-8 text-center">
          股票池日 K 数据完整 ✓
        </div>
        <ul v-else class="space-y-2">
          <li v-for="s in data.missingStocks" :key="s.stockCode"
              class="flex items-center justify-between text-xs px-3 py-2 rounded-md bg-white/[0.02] border border-white/5">
            <div class="flex items-center gap-2 min-w-0">
              <span class="font-mono text-indigo-300">{{ s.stockCode }}</span>
              <span class="text-gray-200 truncate">{{ s.stockName || '--' }}</span>
            </div>
            <span class="dot-tag" :class="s.market === 'SH' ? 'danger' : 'info'">
              {{ s.market === 'SH' ? '沪' : '深' }}
            </span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 底部：最近订单列表 -->
    <div class="admin-card p-4">
      <div class="flex items-center justify-between mb-3 px-1">
        <div class="text-sm text-gray-200 font-medium flex items-center gap-2">
          <el-icon class="text-blue-400"><Clock /></el-icon>最近 10 笔订单
        </div>
        <router-link to="/admin/orders" class="text-[11px] text-indigo-300 hover:text-indigo-200">
          查看全部 ›
        </router-link>
      </div>
      <el-table :data="data.recentOrders || []" class="admin-table" v-loading="loading"
                element-loading-background="rgba(0,0,0,0.4)" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="180">
          <template #default="{ row }">
            <span class="font-mono text-xs text-gray-300">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户" width="80" align="center">
          <template #default="{ row }">
            <span class="text-xs text-gray-400">#{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockCode" label="股票" width="100">
          <template #default="{ row }">
            <span class="font-mono text-indigo-300">{{ row.stockCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="row.direction === 'BUY' ? 'success' : 'danger'">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ row.entrustQuantity || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="委托价" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono">¥{{ formatNum(row.entrustPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="statusClass(row.orderStatus)">
              {{ statusLabel(row.orderStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            <span class="text-xs text-gray-400 font-mono">{{ formatTime(row.orderTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import {
  Odometer, Refresh, User, Wallet, Coin, List, TrendCharts, Warning, Clock
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../../utils/request'

const data = ref({})
const loading = ref(false)
const trendRef = ref(null)
let chart = null

const trendTotal = computed(() =>
  (data.value.orderTrend || []).reduce((acc, p) => acc + Number(p.count || 0), 0)
)

const formatNum = (v) => {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const formatTime = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '--'

const statusLabel = (s) => ({
  PENDING: '待成交', PARTIAL: '部分成交', FILLED: '已成交', CANCELED: '已撤销'
})[s] || s
const statusClass = (s) => ({
  PENDING: 'warn', PARTIAL: 'info', FILLED: 'success', CANCELED: 'danger'
})[s] || 'info'

const reload = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/dashboard/overview')
    if (res.code === 200) {
      data.value = res.data || {}
      await nextTick()
      renderChart()
    }
  } finally { loading.value = false }
}

const renderChart = () => {
  if (!trendRef.value) return
  if (!chart) chart = echarts.init(trendRef.value, 'dark')
  const points = data.value.orderTrend || []
  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 40, right: 20, top: 24, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map(p => p.date.slice(5)),
      axisLine: { lineStyle: { color: '#3f3f46' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    series: [{
      type: 'bar',
      data: points.map(p => Number(p.count || 0)),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#818cf8' },
          { offset: 1, color: '#6366f1' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      barMaxWidth: 36
    }]
  })
}

const onResize = () => chart && chart.resize()

onMounted(() => {
  reload()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  if (chart) { chart.dispose(); chart = null }
})

watch(() => data.value.orderTrend, () => nextTick(renderChart))
</script>

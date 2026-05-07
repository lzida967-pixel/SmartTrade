<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><DataBoard /></el-icon></span>缓存命中率监控</h2>
        <div class="pt-sub">
          实时统计 Redis 各类 key 前缀的命中 / 未命中次数，用于评估缓存策略有效性
        </div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-switch
          v-model="autoRefresh"
          inline-prompt
          active-text="自动 5s"
          inactive-text="已暂停"
          @change="onAutoRefreshChange"
        />
        <el-button plain :loading="loading" @click="loadMetrics">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="danger" plain @click="onReset">
          <el-icon class="mr-1"><Delete /></el-icon>清零
        </el-button>
      </div>
    </div>

    <!-- 4 个汇总指标卡 -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="admin-card">
        <div class="text-xs text-gray-400 mb-1">总命中率</div>
        <div class="flex items-baseline gap-1">
          <span class="text-3xl font-black" :class="hitRateColor(summary.overallHitRate)">
            {{ pct(summary.overallHitRate) }}
          </span>
          <span class="text-sm text-gray-500">%</span>
        </div>
        <div class="mt-2 text-[11px] text-gray-500">
          统计时长 {{ formatElapsed(summary.elapsedSeconds) }}
        </div>
      </div>
      <div class="admin-card">
        <div class="text-xs text-gray-400 mb-1">命中次数</div>
        <div class="text-3xl font-black text-emerald-400">
          {{ summary.totalHits.toLocaleString() }}
        </div>
        <div class="mt-2 text-[11px] text-gray-500">命中即直接返回 Redis，无 DB / 外部接口压力</div>
      </div>
      <div class="admin-card">
        <div class="text-xs text-gray-400 mb-1">未命中（穿透）</div>
        <div class="text-3xl font-black text-rose-400">
          {{ summary.totalMisses.toLocaleString() }}
        </div>
        <div class="mt-2 text-[11px] text-gray-500">未命中需回源（外部行情接口或 DB）</div>
      </div>
      <div class="admin-card">
        <div class="text-xs text-gray-400 mb-1">累计 QPS</div>
        <div class="text-3xl font-black text-sky-400">
          {{ summary.qps.toFixed(2) }}
        </div>
        <div class="mt-2 text-[11px] text-gray-500">
          总请求 {{ summary.totalRequests.toLocaleString() }} / 时长（秒）
        </div>
      </div>
    </div>

    <!-- 饼图 + 表格 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
      <!-- 饼图 -->
      <div class="admin-card md:col-span-1">
        <div class="text-sm font-semibold text-gray-200 mb-2">命中 vs 未命中</div>
        <div ref="pieRef" class="w-full h-[280px]"></div>
      </div>

      <!-- 各前缀明细表 -->
      <div class="admin-card md:col-span-2">
        <div class="flex items-center justify-between mb-2">
          <div class="text-sm font-semibold text-gray-200">按 key 前缀分组</div>
          <div class="text-[11px] text-gray-500">按总访问量降序</div>
        </div>
        <el-table
          :data="items"
          stripe
          size="small"
          :empty-text="loading ? '加载中…' : '暂无数据，请先访问业务接口产生缓存读写'"
        >
          <el-table-column prop="prefix" label="Key 前缀" min-width="170">
            <template #default="{ row }">
              <span class="font-mono text-xs text-cyan-300">{{ row.prefix }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="hits" label="命中" width="100" align="right">
            <template #default="{ row }">
              <span class="text-emerald-400 font-mono">{{ row.hits.toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="misses" label="未命中" width="100" align="right">
            <template #default="{ row }">
              <span class="text-rose-400 font-mono">{{ row.misses.toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="total" label="总访问" width="100" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ row.total.toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="命中率" min-width="180">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <el-progress
                  :percentage="+(row.hitRate * 100).toFixed(1)"
                  :stroke-width="8"
                  :color="progressColor(row.hitRate)"
                  :show-text="false"
                  class="flex-1"
                />
                <span class="font-mono text-xs w-14 text-right" :class="hitRateColor(row.hitRate)">
                  {{ pct(row.hitRate) }}%
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 说明区 -->
    <div class="admin-card mt-4">
      <div class="text-sm font-semibold text-gray-200 mb-2 flex items-center gap-2">
        <el-icon><InfoFilled /></el-icon>
        缓存策略说明
      </div>
      <div class="text-xs text-gray-400 space-y-1.5 leading-relaxed">
        <div class="text-gray-300 font-semibold mb-1">外部接口缓存（行情类）</div>
        <div>· <span class="font-mono text-cyan-300">stock:quote:*</span>　单只股票实时行情，TTL 30 秒（贴合 A 股最小 tick 节奏）</div>
        <div>· <span class="font-mono text-cyan-300">stock:batch:*</span>　全市场批量行情，TTL 30 秒，key 含股票代码集合的 md5 哈希</div>
        <div>· <span class="font-mono text-cyan-300">stock:kline:*</span>　日 K 线，TTL 5 分钟（日 K 一天才更新，盘中刷新足够）</div>
        <div class="text-gray-300 font-semibold mt-3 mb-1">内部接口缓存（DB 减压）</div>
        <div>· <span class="font-mono text-cyan-300">stock:list:*</span>　股票池基础信息，TTL 5 分钟（增删股票时主动失效）</div>
        <div>· <span class="font-mono text-cyan-300">admin:stats:*</span>　股票池统计，TTL 60 秒（增删股票时主动失效）</div>
        <div>· <span class="font-mono text-cyan-300">watchlist:codes:*</span>　用户自选代码，TTL 10 分钟（add/remove 时立即失效，保证一致性）</div>
        <div>· <span class="font-mono text-cyan-300">user:curve:*</span>　用户净值曲线，TTL 60 秒（仅靠 TTL，按 userId + days 隔离）</div>
        <div class="mt-3 pt-3 border-t border-white/5">
          · 数据源采用<span class="text-amber-300">东方财富 + 新浪财经双源 + 熔断冷却</span>策略，源站异常时降级至兜底缓存
        </div>
        <div>
          · 写时清除（Cache-Aside）：admin 增删股票、用户增删自选时<span class="text-amber-300">立即驱逐对应缓存</span>，保证读到最新数据
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataBoard, Refresh, Delete, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getMetrics, resetMetrics } from '../../api/admin/cache'

const loading = ref(false)
const items = ref([])
const summary = reactive({
  totalHits: 0,
  totalMisses: 0,
  totalRequests: 0,
  overallHitRate: 0,
  qps: 0,
  startedAt: '',
  elapsedSeconds: 0
})

const autoRefresh = ref(true)
let timer = null

const pieRef = ref(null)
let pieChart = null

const loadMetrics = async () => {
  loading.value = true
  try {
    const res = await getMetrics()
    if (res.code === 200) {
      const d = res.data || {}
      items.value = d.items || []
      summary.totalHits = d.totalHits || 0
      summary.totalMisses = d.totalMisses || 0
      summary.totalRequests = d.totalRequests || 0
      summary.overallHitRate = d.overallHitRate || 0
      summary.qps = d.qps || 0
      summary.startedAt = d.startedAt || ''
      summary.elapsedSeconds = d.elapsedSeconds || 0
      await nextTick()
      renderPie()
    }
  } finally {
    loading.value = false
  }
}

const onReset = async () => {
  try {
    await ElMessageBox.confirm(
      '将清零所有缓存命中 / 未命中计数，常用于演示压测前重置基线。确认？',
      '清零计数',
      { type: 'warning', confirmButtonText: '确认清零', cancelButtonText: '取消' }
    )
  } catch { return }
  await resetMetrics()
  ElMessage.success('已清零，下一次刷新即从零开始统计')
  loadMetrics()
}

const onAutoRefreshChange = (v) => {
  if (v) startTimer(); else stopTimer()
}

const startTimer = () => {
  stopTimer()
  timer = setInterval(loadMetrics, 5000)
}
const stopTimer = () => {
  if (timer) { clearInterval(timer); timer = null }
}

const renderPie = () => {
  if (!pieRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieRef.value, 'dark')
  }
  const opt = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#9ca3af', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#0b0f1a', borderWidth: 2 },
      label: { show: true, color: '#e5e7eb', fontSize: 11, formatter: '{b}\n{d}%' },
      data: [
        { name: '命中', value: summary.totalHits, itemStyle: { color: '#34d399' } },
        { name: '未命中', value: summary.totalMisses, itemStyle: { color: '#f87171' } }
      ]
    }]
  }
  pieChart.setOption(opt)
}

const onResize = () => pieChart?.resize()

// ============== 工具函数 ==============
const pct = (v) => ((v || 0) * 100).toFixed(1)
const hitRateColor = (v) => {
  if (v >= 0.9) return 'text-emerald-400'
  if (v >= 0.7) return 'text-sky-400'
  if (v >= 0.4) return 'text-amber-400'
  return 'text-rose-400'
}
const progressColor = (v) => {
  if (v >= 0.9) return '#34d399'
  if (v >= 0.7) return '#38bdf8'
  if (v >= 0.4) return '#fbbf24'
  return '#f87171'
}
const formatElapsed = (sec) => {
  if (!sec || sec < 60) return `${sec || 0} 秒`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return `${h} 小时 ${m} 分`
}

onMounted(() => {
  loadMetrics()
  startTimer()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  stopTimer()
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  pieChart = null
})
</script>

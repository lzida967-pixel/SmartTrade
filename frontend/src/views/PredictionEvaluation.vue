<script setup>
/**
 * 模型实证回顾页（普通用户可见）
 *
 * 数据流：
 *   GET /prediction/evaluation/overview       三模型整体准确率 + 3×3 混淆矩阵
 *   GET /prediction/evaluation/timeline       按日的三模型准确率折线
 *   GET /prediction/evaluation/by-stock       某模型最近 N 天命中率排行
 *
 * 用途：
 *   答辩 / 论文里证明模型在线运行的实证表现，配合 30/60/90 天窗口可观察模型衰减情况。
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick, DataAnalysis, Histogram, Trophy, ArrowRight } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getEvaluationOverview,
  getEvaluationTimeline,
  getEvaluationByStock
} from '../api/prediction'

const router = useRouter()

// ---------- 状态 ----------
const days = ref(30)
const loading = ref(false)
const overview = ref({ days: 30, pendingCount: 0, models: [], labelNames: ['看多', '震荡', '看空'] })
const timeline = ref([])
const rankModel = ref('lgbm_v1')
const rankList = ref([])
const rankLoading = ref(false)

// ---------- 颜色工具 ----------
const modelColor = (version) => {
  const v = (version || '').toLowerCase()
  if (v.startsWith('lgbm')) return '#60a5fa'
  if (v.startsWith('xgb')) return '#a78bfa'
  if (v.startsWith('lstm')) return '#34d399'
  return '#6366f1'
}
const progressColor = (acc) => {
  const v = Number(acc) || 0
  if (v >= 0.55) return '#34d399'
  if (v >= 0.45) return '#fbbf24'
  if (v >= 0.33) return '#f87171'
  return '#6b7280'
}
const accBadge = (acc, total) => {
  if (!total || total === 0) return { text: '暂无数据', color: '#6b7280' }
  const v = Number(acc) || 0
  if (v >= 0.55) return { text: '表现良好', color: '#34d399' }
  if (v >= 0.45) return { text: '接近基准', color: '#fbbf24' }
  if (v >= 0.33) return { text: '偏弱', color: '#f87171' }
  return { text: '低于随机', color: '#9ca3af' }
}

// 混淆矩阵单元格背景色（按行内最大值归一化，越深表示样本越多）
const cellBg = (matrix, i, j) => {
  if (!matrix) return 'rgba(255,255,255,0.02)'
  const rowMax = Math.max(...matrix[i])
  if (rowMax === 0) return 'rgba(255,255,255,0.02)'
  const ratio = matrix[i][j] / rowMax
  // 主对角线（命中）用绿色，非对角线（错判）用红橙色
  if (i === j) return `rgba(52, 211, 153, ${0.12 + ratio * 0.55})`
  return `rgba(248, 113, 113, ${0.08 + ratio * 0.45})`
}
const rowSum = (matrix, i) => matrix ? matrix[i].reduce((s, v) => s + v, 0) : 0

// ---------- 加载数据 ----------
const loadAll = async () => {
  loading.value = true
  try {
    const [a, b] = await Promise.all([
      getEvaluationOverview(days.value),
      getEvaluationTimeline(days.value)
    ])
    if (a.code === 200) overview.value = a.data
    if (b.code === 200) timeline.value = b.data || []
    await nextTick()
    renderTimelineChart()
    await loadRanking()
  } catch (e) {
    ElMessage.error('加载模型实证数据失败')
  } finally {
    loading.value = false
  }
}

const loadRanking = async () => {
  rankLoading.value = true
  try {
    const res = await getEvaluationByStock(days.value, rankModel.value, 20)
    if (res.code === 200) rankList.value = res.data || []
  } finally {
    rankLoading.value = false
  }
}

// ---------- 折线图：按日准确率 ----------
const timelineChartRef = ref(null)
let timelineChart = null

const renderTimelineChart = () => {
  if (!timelineChartRef.value) return
  if (!timelineChart) timelineChart = echarts.init(timelineChartRef.value, 'dark')

  const dates = timeline.value.map(r => r.date)
  const buildSeries = (key, name) => ({
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    connectNulls: true,
    itemStyle: { color: modelColor(key) },
    lineStyle: { width: 2, color: modelColor(key) },
    areaStyle: {
      color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: modelColor(key) + '40' },
          { offset: 1, color: modelColor(key) + '00' }
        ] }
    },
    data: timeline.value.map(r => {
      const acc = r[`${key}_accuracy`]
      return acc == null ? null : +(Number(acc) * 100).toFixed(2)
    })
  })

  timelineChart.setOption({
    backgroundColor: 'transparent',
    legend: { data: ['LightGBM', 'XGBoost', 'LSTM'], textStyle: { color: '#cbd5e1' }, top: 4 },
    grid: { left: 60, right: 30, top: 40, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: v => v == null ? '--' : v + '%'
    },
    xAxis: {
      type: 'category', data: dates, boundaryGap: false,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      axisLabel: { formatter: v => v + '%', color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.08)' } }
    },
    series: [
      buildSeries('lgbm_v1', 'LightGBM'),
      buildSeries('xgb_v1', 'XGBoost'),
      buildSeries('lstm_v1', 'LSTM')
    ]
  })
  timelineChart.resize()
}

const onResize = () => timelineChart?.resize()

// ---------- 跳转 ----------
const goToPredict = (code) => router.push({ path: '/prediction', query: { code } })

// ---------- 生命周期 ----------
onMounted(() => {
  window.addEventListener('resize', onResize)
  loadAll()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  timelineChart?.dispose()
})
watch(days, loadAll)
watch(rankModel, loadRanking)

const noData = computed(() => {
  if (!overview.value.models?.length) return true
  return overview.value.models.every(m => m.verifiedCount === 0)
})
</script>

<template>
  <div class="p-8 flex flex-col gap-6">
    <!-- 顶部标题 -->
    <div class="flex items-start justify-between flex-wrap gap-4">
      <div>
        <h2 class="text-2xl font-bold flex items-center gap-2">
          <el-icon class="text-amber-400"><Trophy /></el-icon>
          模型实证回顾
        </h2>
        <p class="text-sm text-gray-400 mt-1">
          统计三模型在最近 {{ days }} 天对历史预测的实盘命中率，T+5 真实涨跌作为基准答案
        </p>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-select v-model="days" class="!w-32">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="14" label="近 14 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="60" label="近 60 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button plain :loading="loading" @click="loadAll">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
        <el-button plain @click="$router.push('/prediction')">
          返回预测页 <el-icon class="ml-1"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 待核对提示 -->
    <el-alert
      v-if="overview.pendingCount > 0"
      type="info" show-icon :closable="false"
      :title="`当前有 ${overview.pendingCount} 条预测尚未核对（T+5 未到期或行情未入库），系统将在每个交易日 16:50 自动回填。`"
    />

    <!-- 三模型卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <div
        v-for="m in overview.models"
        :key="m.modelVersion"
        class="rounded-2xl border border-gray-800 bg-gray-900/40 p-5 flex flex-col gap-4"
      >
        <!-- 模型名 + 标签 -->
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full" :style="{ background: modelColor(m.modelVersion) }"></span>
            <span class="font-semibold text-gray-100">{{ m.modelName }}</span>
            <span class="text-[11px] text-gray-600 font-mono">{{ m.modelVersion }}</span>
          </div>
          <span class="text-[11px] px-2 py-0.5 rounded-full"
                :style="{ background: accBadge(m.accuracy, m.verifiedCount).color + '22', color: accBadge(m.accuracy, m.verifiedCount).color }">
            {{ accBadge(m.accuracy, m.verifiedCount).text }}
          </span>
        </div>

        <!-- 圆环 + 数字 -->
        <div class="flex items-center gap-5">
          <el-progress
            type="circle"
            :percentage="m.verifiedCount > 0 ? +(Number(m.accuracy) * 100).toFixed(1) : 0"
            :width="92" :stroke-width="8"
            :color="progressColor(m.accuracy)"
          >
            <template #default="{ percentage }">
              <span class="text-xl font-black" :style="{ color: progressColor(m.accuracy) }">
                {{ m.verifiedCount > 0 ? percentage.toFixed(1) : '--' }}<span v-if="m.verifiedCount > 0" class="text-sm font-normal">%</span>
              </span>
            </template>
          </el-progress>
          <div class="flex-1 space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-gray-500">已验证</span>
              <span class="font-mono text-gray-200">{{ m.verifiedCount }} 条</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">命中</span>
              <span class="font-mono text-emerald-400">{{ m.correctCount }} 条</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">vs 随机基准</span>
              <span class="font-mono text-amber-300">33.3%</span>
            </div>
          </div>
        </div>

        <!-- 混淆矩阵 -->
        <div>
          <div class="text-xs text-gray-500 mb-2 flex items-center justify-between">
            <span>混淆矩阵 <span class="text-gray-700">(行 = 实际 / 列 = 预测)</span></span>
          </div>
          <div class="grid grid-cols-[60px_repeat(3,1fr)] gap-1 text-[11px]">
            <!-- 表头 -->
            <div></div>
            <div v-for="(name, j) in overview.labelNames" :key="'h'+j"
                 class="text-center text-gray-500 font-semibold py-1">{{ name }}</div>
            <!-- 三行 -->
            <template v-for="(rowName, i) in overview.labelNames" :key="'r'+i">
              <div class="text-right pr-2 text-gray-500 font-semibold py-1 self-center">{{ rowName }}</div>
              <div
                v-for="(_, j) in overview.labelNames" :key="'c'+i+j"
                class="text-center py-2 rounded text-gray-100 font-mono font-bold"
                :style="{ background: cellBg(m.confusionMatrix, i, j) }"
              >
                {{ m.confusionMatrix?.[i]?.[j] || 0 }}
              </div>
            </template>
            <!-- 行小计 -->
            <div class="text-right pr-2 text-gray-700 text-[10px] py-1">合计</div>
            <div v-for="(rowName, i) in overview.labelNames" :key="'s'+i"
                 class="text-center text-gray-600 text-[10px] py-1">
              {{ rowSum(m.confusionMatrix, i) }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 按日准确率折线 -->
    <div class="rounded-2xl border border-gray-800 bg-gray-900/40 p-5">
      <div class="flex items-center justify-between mb-3">
        <div class="text-sm font-semibold text-gray-200 flex items-center gap-2">
          <el-icon class="text-indigo-400"><DataAnalysis /></el-icon>
          准确率随时间变化（按交易日）
        </div>
        <span class="text-xs text-gray-500">空白点表示当日无可用样本</span>
      </div>
      <div ref="timelineChartRef" class="w-full h-[300px]"></div>
      <div v-if="!timeline.length" class="text-center text-gray-600 text-sm py-6">
        所选时间窗口内还没有已核对的预测数据
      </div>
    </div>

    <!-- 按股票排行 -->
    <div class="rounded-2xl border border-gray-800 bg-gray-900/40 p-5">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div class="text-sm font-semibold text-gray-200 flex items-center gap-2">
          <el-icon class="text-amber-400"><Histogram /></el-icon>
          股票命中率排行 TOP 20
          <span class="text-[11px] text-gray-600 font-normal">（样本数 ≥ 3 才纳入）</span>
        </div>
        <el-radio-group v-model="rankModel" size="small">
          <el-radio-button value="lgbm_v1">LightGBM</el-radio-button>
          <el-radio-button value="xgb_v1">XGBoost</el-radio-button>
          <el-radio-button value="lstm_v1">LSTM</el-radio-button>
        </el-radio-group>
      </div>
      <el-table
        :data="rankList" v-loading="rankLoading"
        size="small" stripe
        empty-text="暂无可用数据"
        @row-click="(row) => goToPredict(row.stockCode)"
        style="cursor: pointer"
      >
        <el-table-column type="index" label="排名" width="70" align="center" />
        <el-table-column prop="stockCode" label="代码" width="100">
          <template #default="{ row }">
            <span class="font-mono text-gray-100">{{ row.stockCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="名称" min-width="120" />
        <el-table-column prop="verifiedCount" label="已验证" width="100" align="center">
          <template #default="{ row }">
            <span class="font-mono text-gray-300">{{ row.verifiedCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="correctCount" label="命中" width="100" align="center">
          <template #default="{ row }">
            <span class="font-mono text-emerald-400">{{ row.correctCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="准确率" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-progress
                :percentage="+(Number(row.accuracy) * 100).toFixed(1)"
                :stroke-width="8" :show-text="false"
                :color="progressColor(row.accuracy)"
                class="flex-1"
              />
              <span class="font-mono w-14 text-right" :style="{ color: progressColor(row.accuracy) }">
                {{ (Number(row.accuracy) * 100).toFixed(1) }}%
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 底部说明 -->
    <div class="rounded-2xl border border-gray-800 bg-gray-900/30 p-5">
      <div class="text-sm font-semibold text-gray-300 mb-3 flex items-center gap-2">
        <el-icon class="text-indigo-400"><MagicStick /></el-icon>
        指标说明
      </div>
      <div class="text-xs text-gray-500 space-y-1.5 leading-relaxed">
        <div>· <strong class="text-gray-300">三分类标签</strong>：实际涨幅 &gt; +3% → 看多；&lt; -3% → 看空；其余 → 震荡</div>
        <div>· <strong class="text-gray-300">命中</strong> = 预测三分类与实际三分类完全一致</div>
        <div>· <strong class="text-gray-300">混淆矩阵</strong>：主对角线（绿色）为命中数，非对角线（红色）为错判方向</div>
        <div>· <strong class="text-amber-300">随机基准 33.3%</strong>：三分类等概率猜测的期望准确率，模型显著高于此才有意义</div>
        <div>· <strong class="text-gray-300">回填机制</strong>：每个交易日 16:50 系统自动核对已凑够 T+5 的预测记录</div>
      </div>
    </div>
  </div>
</template>

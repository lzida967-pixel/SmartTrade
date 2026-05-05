<script setup>
/**
 * LSTM 序列预测页
 *
 * 数据流：
 *   用户输入代码 -> /api/prediction/predict/{code}?model=lstm (Spring Boot)
 *   -> Python FastAPI (LSTM 序列推理，最近 30 个交易日) -> 返回三类概率 + 梯度显著图特征重要性
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import request from '../utils/request'
import {
  labelMeta, formatValue, formatImportance, featureDescription
} from '../utils/predictionFormat'
import { usePredictionSse } from '../composables/usePredictionSse'

const route = useRoute()
const router = useRouter()

const codeInput = ref(route.query.code || '600519')
const loading = ref(false)
const result = ref(null)
const error = ref('')

const goTree    = () => router.push({ path: '/prediction',         query: { code: codeInput.value } })
const goCompare = () => router.push({ path: '/prediction/compare', query: { code: codeInput.value } })

const reportSse = usePredictionSse({
  buildUrl: (code) => `/api/prediction/predict/${code}/report?model=lstm`,
  errorHint: 'AI 报告生成失败',
  onBeforeStart: () => {
    if (!result.value) { ElMessage.warning('请先生成预测结果'); return false }
  }
})
const { content: reportContent, streaming: reportStreaming, errorMsg: reportError } = reportSse
const generateReport = () => reportSse.start(result.value?.code)
const stopReport  = () => reportSse.stop()
const copyReport  = () => reportSse.copy()

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => {
  if (!text) return ''
  try { return DOMPurify.sanitize(marked.parse(String(text))) }
  catch (_) { return DOMPurify.sanitize(String(text).replace(/\n/g, '<br>')) }
}

const quickCodes = [
  { code: '600519', name: '贵州茅台' },
  { code: '000858', name: '五粮液' },
  { code: '300750', name: '宁德时代' },
  { code: '002384', name: '东山精密' },
  { code: '601899', name: '紫金矿业' },
  { code: '600036', name: '招商银行' }
]

const currentMeta = computed(() => result.value ? labelMeta[result.value.label] : null)

const probaPercent = computed(() => {
  if (!result.value) return { bullish: 0, neutral: 0, bearish: 0 }
  const p = result.value.proba
  return {
    bullish: (p.bullish * 100).toFixed(1),
    neutral: (p.neutral * 100).toFixed(1),
    bearish: (p.bearish * 100).toFixed(1)
  }
})

const confidenceLevel = computed(() => {
  if (!result.value) return null
  const c = result.value.confidence
  if (c >= 0.6) return { text: '高置信', color: '#10b981' }
  if (c >= 0.45) return { text: '中等置信', color: '#f59e0b' }
  return { text: '低置信', color: '#94a3b8' }
})

const fetchPrediction = async () => {
  const raw = codeInput.value?.trim()
  if (!raw) { ElMessage.warning('请输入 6 位股票代码'); return }
  const code = raw.padStart(6, '0')
  if (!/^\d{6}$/.test(code)) { ElMessage.warning('股票代码必须是 6 位数字'); return }
  reportSse.reset()
  loading.value = true
  error.value = ''
  try {
    const res = await request.get(`/prediction/predict/${code}`, { params: { model: 'lstm' } })
    result.value = res.data
    await nextTick()
    renderProbaChart()
    renderFeatureChart()
    renderKlineForecast()
  } catch (e) {
    error.value = e?.response?.data?.msg || '预测服务调用失败'
    result.value = null
  } finally {
    loading.value = false
  }
}

const selectQuick = (code) => { codeInput.value = code; fetchPrediction() }

let probaChart = null
let featureChart = null
let klineChart = null
const probaChartRef = ref(null)
const featureChartRef = ref(null)
const klineChartRef = ref(null)
const klineLoading = ref(false)

const renderKlineForecast = async () => {
  if (!klineChartRef.value || !result.value) return
  klineLoading.value = true
  let points = []
  try {
    const r = await request.get(`/stock/kline/${result.value.code}`, { params: { limit: 60 } })
    if (r.code === 200) points = r.data || []
  } catch (_) { /* ignore */ }
  klineLoading.value = false
  if (!points.length) return

  if (!klineChart) klineChart = echarts.init(klineChartRef.value, 'dark')

  const forwardDays = result.value.forwardDays || 5
  const threshold   = result.value.threshold   || 0.03
  const last        = points[points.length - 1]
  const latestClose = Number(last.closePrice)
  const upper       = latestClose * (1 + threshold)
  const lower       = latestClose * (1 - threshold)

  const dates    = points.map(p => p.tradeDate)
  const futureXs = Array.from({ length: forwardDays }, (_, i) => `T+${i + 1}`)
  const xs       = [...dates, ...futureXs]

  const candles = points.map(p => [
    Number(p.openPrice), Number(p.closePrice),
    Number(p.lowPrice),  Number(p.highPrice),
  ])
  const data = [...candles, ...Array(forwardDays).fill('-')]

  const ma = (n) => points.map((_, i) => {
    if (i < n - 1) return '-'
    let s = 0
    for (let j = 0; j < n; j++) s += Number(points[i - j].closePrice)
    return (s / n).toFixed(2)
  }).concat(Array(forwardDays).fill('-'))

  const yHi = Math.max(...points.map(p => Number(p.highPrice)), upper * 1.04)
  const yLo = Math.min(...points.map(p => Number(p.lowPrice)),  lower * 0.96)

  const conf = result.value.confidence || 0.5
  const hotAlpha  = Math.min(0.42, 0.18 + conf * 0.4)
  const coldAlpha = 0.07
  const startX = xs[points.length - 1]
  const endX   = xs[xs.length - 1]

  const zones = [
    { label: 0, ymin: upper, ymax: yHi,    color: '#ef4444' },
    { label: 1, ymin: lower, ymax: upper,  color: '#fbbf24' },
    { label: 2, ymin: yLo,   ymax: lower,  color: '#10b981' },
  ]
  const markAreaData = zones.map(z => [
    { xAxis: startX, yAxis: z.ymin,
      itemStyle: { color: z.color, opacity: z.label === result.value.label ? hotAlpha : coldAlpha } },
    { xAxis: endX,   yAxis: z.ymax }
  ])

  klineChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 60, right: 90, top: 30, bottom: 45 },
    legend: { data: ['日K', 'MA5', 'MA20'], textStyle: { color: '#94a3b8' }, top: 0 },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(20,20,25,0.92)', borderColor: '#2c2c33',
      textStyle: { color: '#e5e7eb', fontSize: 12 },
    },
    xAxis: {
      type: 'category', data: xs, boundaryGap: true,
      axisLine:  { lineStyle: { color: '#3f3f46' } },
      axisLabel: { color: '#94a3b8', fontSize: 10 },
    },
    yAxis: {
      scale: true, min: yLo, max: yHi,
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.08)' } },
      axisLabel: { color: '#94a3b8' },
    },
    dataZoom: [
      { type: 'inside', start: 30, end: 100 },
      { type: 'slider', height: 16, bottom: 6, start: 30, end: 100,
        textStyle: { color: '#6b7280' }, borderColor: '#27272a',
        fillerColor: 'rgba(167,139,250,0.18)' }
    ],
    series: [
      {
        name: '日K', type: 'candlestick', data,
        itemStyle: { color: '#ef4444', color0: '#10b981', borderColor: '#ef4444', borderColor0: '#10b981' },
        markArea: { silent: true, z: 0, data: markAreaData },
        markLine: {
          symbol: 'none', silent: true,
          lineStyle: { type: 'dashed', width: 1 },
          data: [
            { yAxis: latestClose, lineStyle: { color: '#cbd5e1' },
              label: { formatter: `现价 ${latestClose.toFixed(2)}`, color: '#cbd5e1', position: 'insideEndTop' } },
            { yAxis: upper, lineStyle: { color: '#ef4444' },
              label: { formatter: `+${(threshold*100).toFixed(0)}%  ${upper.toFixed(2)}`, color: '#ef4444', position: 'insideEndTop' } },
            { yAxis: lower, lineStyle: { color: '#10b981' },
              label: { formatter: `-${(threshold*100).toFixed(0)}%  ${lower.toFixed(2)}`, color: '#10b981', position: 'insideEndBottom' } },
          ]
        }
      },
      { name: 'MA5',  type: 'line', data: ma(5),  smooth: true, symbol: 'none',
        lineStyle: { width: 1, color: '#818cf8' } },
      { name: 'MA20', type: 'line', data: ma(20), smooth: true, symbol: 'none',
        lineStyle: { width: 1, color: '#a78bfa' } },
    ],
  }, true)
}

const renderProbaChart = () => {
  if (!probaChartRef.value || !result.value) return
  if (!probaChart) probaChart = echarts.init(probaChartRef.value, 'dark')
  const p = result.value.proba
  probaChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 70, right: 30, top: 30, bottom: 30 },
    xAxis: {
      type: 'value', max: 1,
      axisLabel: { formatter: v => (v * 100).toFixed(0) + '%', color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)' } }
    },
    yAxis: {
      type: 'category', data: ['看空', '震荡', '看多'],
      axisLabel: { color: '#cbd5e1', fontSize: 14, fontWeight: 600 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      data: [
        { value: p.bearish, itemStyle: { color: '#10b981' } },
        { value: p.neutral, itemStyle: { color: '#64748b' } },
        { value: p.bullish, itemStyle: { color: '#ef4444' } }
      ],
      barWidth: 28,
      label: {
        show: true, position: 'right',
        formatter: ({ value }) => (value * 100).toFixed(1) + '%',
        color: '#e2e8f0', fontWeight: 600
      },
      itemStyle: { borderRadius: [0, 6, 6, 0] }
    }]
  })
}

const renderFeatureChart = () => {
  if (!featureChartRef.value || !result.value?.topFeatures) return
  if (!featureChart) featureChart = echarts.init(featureChartRef.value, 'dark')
  const feats = [...result.value.topFeatures].reverse()
  featureChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 130, right: 90, top: 20, bottom: 20 },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const f = feats[params[0].dataIndex]
        return `<div style="font-weight:600">${f.name}</div>
                <div>当前值: <b>${formatValue(f.name, f.value)}</b></div>
                <div>梯度显著度: ${formatImportance(f.importance)}</div>`
      }
    },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category', data: feats.map(f => f.name),
      axisLabel: { color: '#cbd5e1', fontSize: 12 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: [{
      type: 'bar', data: feats.map(f => f.importance), barWidth: 14,
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [{ offset: 0, color: '#818cf8' }, { offset: 1, color: '#a78bfa' }] },
        borderRadius: [0, 4, 4, 0]
      },
      label: {
        show: true, position: 'right',
        formatter: ({ dataIndex }) => formatValue(feats[dataIndex].name, feats[dataIndex].value),
        color: '#a78bfa', fontSize: 11, fontWeight: 600
      }
    }]
  })
}

const onResize = () => { probaChart?.resize(); featureChart?.resize(); klineChart?.resize() }
onMounted(() => { window.addEventListener('resize', onResize); fetchPrediction() })
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  probaChart?.dispose(); featureChart?.dispose(); klineChart?.dispose()
})

watch(() => result.value, () => nextTick(() => { renderProbaChart(); renderFeatureChart(); renderKlineForecast() }))
watch(() => route.query.code, (newCode) => {
  if (newCode && newCode !== codeInput.value) { codeInput.value = newCode; fetchPrediction() }
})
</script>

<template>
  <div style="padding: 15px; display: flex; flex-direction: column; gap: 15px;">
    <!-- 顶部标题 -->
    <div>
      <h2 class="text-2xl font-bold flex items-center gap-2 flex-wrap">
        <el-icon class="text-violet-400"><Cpu /></el-icon>
        LSTM 序列预测
        <el-tag size="small" color="#7c3aed22" style="color:#a78bfa;border-color:#7c3aed44">LSTM · T+5</el-tag>
      </h2>
      <p class="text-sm text-gray-400 mt-1">
        基于最近 <strong class="text-violet-400">30 个交易日</strong>的特征序列，使用双层 LSTM 神经网络捕捉时序依赖，预测未来 5 个交易日涨跌幅区间
      </p>
    </div>

    <!-- LSTM 说明卡 -->
    <div class="rounded-2xl border border-violet-900/40 bg-violet-950/10 flex items-start gap-4" style="padding: 16px 20px;">
      <el-icon class="text-violet-400 text-xl mt-0.5 flex-shrink-0"><InfoFilled /></el-icon>
      <div class="text-sm text-gray-300 leading-relaxed">
        <span class="font-semibold text-violet-300">与树模型的核心区别：</span>
        LightGBM / XGBoost 只看<em>当天</em>的技术指标快照；LSTM 将最近
        <strong class="text-violet-300">30 个交易日</strong>的 35 维特征拼成时序矩阵，
        让网络自行学习指标的<em>变化趋势</em>和<em>节奏</em>，更适合识别趋势延续或反转的早期信号。
        特征重要性由<strong class="text-violet-300">梯度显著图</strong>计算（∂输出/∂输入），
        反映序列中哪些维度对预测贡献最大。
      </div>
    </div>

    <!-- 操作区 -->
    <div class="rounded-2xl border border-gray-800 bg-gray-900/30 flex items-center gap-3 flex-wrap" style="padding: 14px 20px;">
      <el-input
        v-model="codeInput"
        placeholder="输入 6 位股票代码"
        class="!w-52 soft-input"
        maxlength="6"
        @keyup.enter="fetchPrediction"
        clearable
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button round type="primary" :loading="loading" @click="fetchPrediction" class="!px-6">
        <el-icon class="mr-1"><Cpu /></el-icon>LSTM 推理
      </el-button>
      <el-divider direction="vertical" class="!mx-2" />
      <el-button round plain size="small" @click="goTree">树模型预测</el-button>
      <el-button round plain size="small" @click="goCompare">模型对比</el-button>
      <el-divider direction="vertical" class="!mx-2" />
      <span class="text-xs text-gray-500 mr-1">常用：</span>
      <el-tag
        v-for="q in quickCodes" :key="q.code"
        round size="small" class="!cursor-pointer quick-tag"
        :effect="codeInput === q.code ? 'dark' : 'plain'"
        @click="selectQuick(q.code)"
      >
        {{ q.code }} {{ q.name }}
      </el-tag>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <!-- Loading -->
    <div v-if="loading && !result" class="text-center py-20 text-gray-500">
      <el-icon class="animate-spin text-3xl text-violet-400"><Loading /></el-icon>
      <div class="mt-3">正在调用 LSTM 序列推理（读取最近 30 个交易日）...</div>
    </div>

    <!-- 结果区 -->
    <template v-if="result && currentMeta">
      <!-- 结论卡 -->
      <div class="rounded-2xl border"
           :style="{ borderColor: currentMeta.color + '66', background: currentMeta.bg, padding: '20px 24px' }">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div class="flex items-center gap-5">
            <div class="text-6xl leading-none" :style="{ color: currentMeta.color }">
              {{ currentMeta.icon }}
            </div>
            <div>
              <div class="text-3xl font-black" :style="{ color: currentMeta.color }">
                {{ result.labelName }}
              </div>
              <div class="text-xs text-gray-400 mt-1">
                阈值 ±{{ (result.threshold * 100).toFixed(0) }}% · 未来 {{ result.forwardDays }} 个交易日
              </div>
            </div>
          </div>

          <div class="flex items-center gap-6 flex-wrap">
            <div class="text-center">
              <div class="text-3xl font-black" :style="{ color: currentMeta.color }">
                {{ (result.confidence * 100).toFixed(1) }}<span class="text-base font-normal">%</span>
              </div>
              <div class="text-xs text-gray-400">置信度</div>
              <el-tag size="small" class="mt-1"
                      :style="{ background: confidenceLevel.color + '22',
                                color: confidenceLevel.color,
                                borderColor: confidenceLevel.color + '44' }">
                {{ confidenceLevel.text }}
              </el-tag>
            </div>

            <div class="text-center">
              <div class="text-xl font-bold text-gray-200">{{ result.asOfDate }}</div>
              <div class="text-xs text-gray-400">数据基准日</div>
            </div>

            <div class="text-center">
              <el-tag size="small" type="primary">{{ result.modelVersion }}</el-tag>
              <div class="text-xs text-gray-400 mt-1">模型版本</div>
            </div>
          </div>
        </div>
      </div>

      <!-- K 线 + LSTM 预测目标区间 -->
      <div class="rounded-2xl border border-violet-900/40 bg-violet-950/10" style="padding: 18px 22px;">
        <div class="flex items-center justify-between mb-3 flex-wrap gap-2">
          <h3 class="text-base font-semibold flex items-center gap-2">
            <el-icon class="text-violet-400"><TrendCharts /></el-icon>
            K 线走势 · LSTM T+{{ result.forwardDays }} 预测目标区间
          </h3>
          <div class="flex items-center gap-3 text-[11px] text-gray-400 flex-wrap">
            <span class="flex items-center gap-1">
              <span class="inline-block w-3 h-3 rounded" style="background:rgba(239,68,68,.3);border:1px solid rgba(239,68,68,.6)"></span>
              看多区 (&gt; +{{ (result.threshold*100).toFixed(0) }}%)
            </span>
            <span class="flex items-center gap-1">
              <span class="inline-block w-3 h-3 rounded" style="background:rgba(251,191,36,.3);border:1px solid rgba(251,191,36,.6)"></span>
              震荡区 (±{{ (result.threshold*100).toFixed(0) }}%)
            </span>
            <span class="flex items-center gap-1">
              <span class="inline-block w-3 h-3 rounded" style="background:rgba(16,185,129,.3);border:1px solid rgba(16,185,129,.6)"></span>
              看空区 (&lt; -{{ (result.threshold*100).toFixed(0) }}%)
            </span>
            <span class="text-violet-400/80">| 高亮区域 = LSTM 预测目标</span>
          </div>
        </div>
        <div class="relative">
          <div ref="klineChartRef" class="w-full h-[380px]"></div>
          <div v-if="klineLoading" class="absolute inset-0 flex items-center justify-center bg-black/30">
            <el-icon class="animate-spin text-violet-400 text-2xl"><Loading /></el-icon>
          </div>
        </div>
      </div>

      <!-- 概率分布 + 特征重要性 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <!-- 概率分布 -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/30" style="padding: 18px 20px;">
          <h3 class="text-sm font-semibold text-gray-300 mb-3 flex items-center gap-2">
            <el-icon class="text-violet-400"><PieChart /></el-icon>
            三类概率分布
          </h3>
          <div class="space-y-3 mb-4">
            <div v-for="(key, idx) in ['bullish','neutral','bearish']" :key="key"
                 class="flex items-center gap-3 text-xs">
              <span class="w-10 text-gray-400 font-medium">{{ ['看多','震荡','看空'][idx] }}</span>
              <el-progress
                class="flex-1"
                :percentage="result.proba[key] * 100"
                :color="labelMeta[idx].color"
                :show-text="false"
                :stroke-width="12"
              />
              <span class="w-14 text-right font-mono font-semibold"
                    :style="{ color: labelMeta[idx].color }">
                {{ probaPercent[key] }}%
              </span>
            </div>
          </div>
          <div ref="probaChartRef" class="w-full h-[160px]"></div>
        </div>

        <!-- 梯度显著图特征重要性 -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/30" style="padding: 18px 20px;">
          <h3 class="text-sm font-semibold text-gray-300 mb-1 flex items-center gap-2">
            <el-icon class="text-violet-400"><DataAnalysis /></el-icon>
            梯度显著图 Top 8 特征
          </h3>
          <p class="text-xs text-gray-500 mb-3">
            LSTM 对每个特征的输出梯度绝对均值，反映序列中哪些维度影响最大
          </p>
          <div ref="featureChartRef" class="w-full h-[220px]"></div>
        </div>
      </div>

      <!-- Top 特征详情表 -->
      <div class="rounded-2xl border border-gray-800 bg-gray-900/30" style="padding: 18px 20px;">
        <h3 class="text-sm font-semibold text-gray-300 mb-3 flex items-center gap-2">
          <el-icon class="text-violet-400"><Grid /></el-icon>
          特征详情 <span class="text-xs text-gray-500 font-normal ml-1">· 当前值取最新一个交易日</span>
        </h3>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <div v-for="f in result.topFeatures" :key="f.name"
               class="rounded-xl bg-gray-800/40 border border-gray-700/30" style="padding: 12px 16px;">
            <div class="font-mono text-xs text-violet-300 font-semibold truncate" :title="f.name">
              {{ f.name }}
            </div>
            <div class="text-[11px] text-gray-500 mt-0.5 truncate">
              {{ featureDescription(f.name) }}
            </div>
            <div class="text-base font-bold text-amber-400 mt-1">{{ formatValue(f.name, f.value) }}</div>
            <div class="text-[11px] text-gray-500">梯度 {{ formatImportance(f.importance) }}</div>
          </div>
        </div>
      </div>

      <!-- AI 分析报告 -->
      <div class="rounded-2xl border border-violet-900/40 bg-gradient-to-br from-violet-950/20 to-gray-900/30 overflow-hidden">
        <div class="border-b border-violet-900/30 flex items-center justify-between flex-wrap gap-2" style="padding: 16px 22px;">
          <h3 class="text-base font-semibold flex items-center gap-2">
            <el-icon class="text-violet-400"><ChatLineRound /></el-icon>
            AI 分析报告
            <el-tag size="small" type="info">由通义千问基于 LSTM 预测结果撰写</el-tag>
          </h3>
          <div class="flex items-center gap-2">
            <el-button v-if="!reportStreaming && !reportContent"
                       round size="small" type="primary" @click="generateReport">
              生成报告
            </el-button>
            <el-button v-if="!reportStreaming && reportContent"
                       round size="small" plain @click="generateReport">重新生成</el-button>
            <el-button v-if="!reportStreaming && reportContent"
                       round size="small" plain @click="copyReport">复制</el-button>
            <el-button v-if="reportStreaming"
                       round size="small" type="danger" plain @click="stopReport">停止生成</el-button>
          </div>
        </div>
        <div style="padding: 18px 22px;">
          <div v-if="!reportContent && !reportStreaming && !reportError"
               class="text-center text-gray-500 py-6">
            <el-icon class="text-3xl text-violet-400/50"><ChatLineRound /></el-icon>
            <div class="mt-2 text-sm">点击「生成报告」，AI 将基于 LSTM 序列预测结果和关键特征撰写分析</div>
          </div>
          <el-alert v-if="reportError" :title="reportError" type="error" :closable="false" class="mb-3" />
          <div v-if="reportStreaming && !reportContent"
               class="flex items-center gap-3 text-violet-300/80 py-3 px-1">
            <div class="thinking-dots"><span></span><span></span><span></span></div>
            <span class="text-sm">正在生成 LSTM 分析报告...</span>
          </div>
          <div v-if="reportContent"
               class="markdown-body text-sm leading-relaxed"
               v-html="renderMarkdown(reportContent + (reportStreaming ? '▌' : ''))">
          </div>
        </div>
      </div>

      <el-alert :closable="false" type="warning" show-icon
                title="风险提示"
                description="LSTM 预测基于历史时序特征，不构成投资建议。模型准确率约 47-48%，请结合基本面和 LightGBM/XGBoost 信号综合判断。" />
    </template>
  </div>
</template>

<style scoped>
.quick-tag { transition: all .15s; }
.quick-tag:hover { transform: scale(1.05); }

.thinking-dots { display: inline-flex; gap: 4px; }
.thinking-dots span {
  width: 6px; height: 6px; border-radius: 50%;
  background: #a78bfa; display: inline-block;
  animation: thinking 1.2s infinite ease-in-out;
}
.thinking-dots span:nth-child(2) { animation-delay: .2s; }
.thinking-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes thinking {
  0%, 80%, 100% { opacity: .3; transform: scale(.8); }
  40%           { opacity: 1;  transform: scale(1.1); }
}

.markdown-body :deep(h2) {
  font-size: 15px; font-weight: 700; color: #e9d5ff;
  margin: 18px 0 8px; padding-bottom: 4px;
  border-bottom: 1px solid rgba(167,139,250,.2);
}
.markdown-body :deep(h3) { font-size: 14px; font-weight: 600; color: #c4b5fd; margin: 14px 0 6px; }
.markdown-body :deep(p)  { margin: 6px 0; color: #cbd5e1; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 6px 0 6px 20px; color: #cbd5e1; }
.markdown-body :deep(li) { margin: 3px 0; }
.markdown-body :deep(strong) { color: #fde68a; }
.markdown-body :deep(code) {
  background: rgba(139,92,246,.12); color: #d8b4fe;
  padding: 1px 6px; border-radius: 4px; font-size: 12px;
}
</style>

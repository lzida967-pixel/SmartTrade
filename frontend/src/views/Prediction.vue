<script setup>
/**
 * AI 智能预测页 - LightGBM T+5 三分类预测
 *
 * 数据流：
 *   用户输入代码 -> /api/prediction/lgbm/{code} (Spring Boot)
 *   -> Python FastAPI (LightGBM 推理) -> 返回三类概率 + Top 特征
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
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
const goCompare = () => router.push({ path: '/prediction/compare', query: { code: codeInput.value } })
// 支持从 URL query 读取股票代码（如 /prediction?code=600519）
const codeInput = ref(route.query.code || '600519')
const modelKey = ref('lgbm')  // 'lgbm' | 'xgb'
const modelOptions = [
  { value: 'lgbm', label: 'LightGBM', desc: '默认 / 快速' },
  { value: 'xgb',  label: 'XGBoost',  desc: '树模型对比' },
]
const currentModelLabel = computed(() =>
  modelOptions.find(m => m.value === modelKey.value)?.label || 'LightGBM'
)
const loading = ref(false)
const result = ref(null)
const error = ref('')

// AI 报告相关状态（由 usePredictionSse composable 管理）
const reportSse = usePredictionSse({
  buildUrl: (code) => `/api/prediction/predict/${code}/report?model=${modelKey.value}`,
  errorHint: 'AI 报告生成失败',
  onBeforeStart: () => {
    if (!result.value) { ElMessage.warning('请先生成预测结果'); return false }
  }
})
const { content: reportContent, streaming: reportStreaming, errorMsg: reportError } = reportSse
const generateReport = () => reportSse.start(result.value?.code)
const stopReport = () => reportSse.stop()
const copyReport = () => reportSse.copy()

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    return DOMPurify.sanitize(marked.parse(String(text)))
  } catch (_) {
    return DOMPurify.sanitize(String(text).replace(/\n/g, '<br>'))
  }
}

// 常用股票快捷
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

// 置信度等级
const confidenceLevel = computed(() => {
  if (!result.value) return null
  const c = result.value.confidence
  if (c >= 0.6) return { text: '高置信', color: '#10b981' }
  if (c >= 0.45) return { text: '中等置信', color: '#f59e0b' }
  return { text: '低置信', color: '#94a3b8' }
})

const fetchPrediction = async () => {
  const raw = codeInput.value?.trim()
  if (!raw) {
    ElMessage.warning('请输入 6 位股票代码')
    return
  }
  const code = raw.padStart(6, '0')
  if (!/^\d{6}$/.test(code)) {
    ElMessage.warning('股票代码必须是 6 位数字')
    return
  }
  // 切换股票时清空旧报告
  reportSse.reset()

  loading.value = true
  error.value = ''
  try {
    const res = await request.get(`/prediction/predict/${code}`, { params: { model: modelKey.value } })
    result.value = res.data
    await nextTick()
    renderProbaChart()
    renderFeatureChart()
  } catch (e) {
    error.value = e?.response?.data?.msg || '预测服务调用失败'
    result.value = null
  } finally {
    loading.value = false
  }
}

const selectQuick = (code) => {
  codeInput.value = code
  fetchPrediction()
}

// ---------- ECharts ----------
let probaChart = null
let featureChart = null
const probaChartRef = ref(null)
const featureChartRef = ref(null)

const renderProbaChart = () => {
  if (!probaChartRef.value || !result.value) return
  if (!probaChart) probaChart = echarts.init(probaChartRef.value, 'dark')
  const p = result.value.proba
  probaChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 70, right: 30, top: 30, bottom: 30 },
    xAxis: {
      type: 'value',
      max: 1,
      axisLabel: { formatter: v => (v * 100).toFixed(0) + '%', color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)' } }
    },
    yAxis: {
      type: 'category',
      data: ['看空', '震荡', '看多'],
      axisLabel: { color: '#cbd5e1', fontSize: 14, fontWeight: 600 },
      axisLine: { show: false },
      axisTick: { show: false }
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
  const feats = [...result.value.topFeatures].reverse() // 倒序 => 重要性高在上
  featureChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 130, right: 90, top: 20, bottom: 20 },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const f = feats[params[0].dataIndex]
        return `<div style="font-weight:600">${f.name}</div>
                <div>当前值: <b>${formatValue(f.name, f.value)}</b></div>
                <div>重要性: ${formatImportance(f.importance)}</div>`
      }
    },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: feats.map(f => f.name),
      axisLabel: { color: '#cbd5e1', fontSize: 12 },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      data: feats.map(f => f.importance),
      barWidth: 14,
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [{ offset: 0, color: '#f59e0b' }, { offset: 1, color: '#fbbf24' }] },
        borderRadius: [0, 4, 4, 0]
      },
      label: {
        show: true, position: 'right',
        formatter: ({ dataIndex }) => formatValue(feats[dataIndex].name, feats[dataIndex].value),
        color: '#fbbf24', fontSize: 11, fontWeight: 600
      }
    }]
  })
}

const onResize = () => {
  probaChart?.resize()
  featureChart?.resize()
}
onMounted(() => {
  window.addEventListener('resize', onResize)
  fetchPrediction()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  probaChart?.dispose()
  featureChart?.dispose()
})

watch(() => result.value, () => nextTick(() => { renderProbaChart(); renderFeatureChart() }))

// 监听 URL query.code 变化（从 Market.vue 跳转过来时）
watch(() => route.query.code, (newCode) => {
  if (newCode && newCode !== codeInput.value) {
    codeInput.value = newCode
    fetchPrediction()
  }
})

</script>

<template>
  <div class="p-8 flex flex-col gap-6">
    <!-- 顶部标题 -->
    <div>
      <h2 class="text-2xl font-bold flex items-center gap-2 flex-wrap">
        <el-icon class="text-amber-400"><MagicStick /></el-icon>
        智能预测
        <el-tag size="small" type="info">{{ currentModelLabel }} · T+5</el-tag>
      </h2>
      <p class="text-sm text-gray-400 mt-1">
        基于 35 维历史价量 + 基本面特征的 {{ currentModelLabel }} 三分类模型，预测未来 5 个交易日涨跌幅区间
      </p>
    </div>

    <!-- 操作区（独立一行，避免挤压） -->
    <div class="rounded-2xl p-5 border border-gray-800 bg-gray-900/30 flex items-center gap-3 flex-wrap">
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
      <el-button round :loading="loading" @click="fetchPrediction" class="amber-btn !px-6">
        开始预测
      </el-button>
      <el-divider direction="vertical" class="!mx-3" />
      <span class="text-xs text-gray-500 mr-1">模型：</span>
      <el-radio-group v-model="modelKey" size="small" class="model-switch">
        <el-radio-button v-for="m in modelOptions" :key="m.value" :value="m.value">
          {{ m.label }}
        </el-radio-button>
      </el-radio-group>
      <el-button size="small" round plain class="compare-btn" @click="goCompare">
        <el-icon class="mr-1"><Connection /></el-icon>对比模式
      </el-button>
      <el-divider direction="vertical" class="!mx-3" />
      <span class="text-xs text-gray-500 mr-1">常用：</span>
      <el-tag
        v-for="q in quickCodes" :key="q.code"
        round
        class="!cursor-pointer quick-tag"
        :effect="codeInput === q.code ? 'dark' : 'plain'"
        @click="selectQuick(q.code)"
      >
        {{ q.code }} {{ q.name }}
      </el-tag>
    </div>

    <!-- 错误提示 -->
    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <!-- Loading 骨架屏 -->
    <div v-if="loading && !result" class="text-center py-20 text-gray-500">
      <el-icon class="animate-spin text-3xl"><Loading /></el-icon>
      <div class="mt-3">正在调用 {{ currentModelLabel }} 推理...</div>
    </div>

    <!-- 结果区 -->
    <template v-if="result">
      <!-- 核心结论卡片：lg 屏 3 列，md 屏 2 列，sm 屏 1 列 -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <!-- 主信号 -->
        <div class="rounded-2xl border border-gray-800 lg:col-span-1"
             :style="{ background: currentMeta.bg, padding: '20px 24px' }">
          <div class="text-xs text-gray-400 mb-3 flex items-center gap-1">
            预测结论
            <el-tooltip placement="top">
              <template #content>
                模型对未来 {{ result.forwardDays }} 个交易日累计涨跌幅做出的三分类判断<br/>
                看多 = 涨幅 &gt; +{{ (result.threshold*100).toFixed(0) }}%<br/>
                震荡 = 涨跌幅在 ±{{ (result.threshold*100).toFixed(0) }}% 内<br/>
                看空 = 跌幅 &gt; -{{ (result.threshold*100).toFixed(0) }}%
              </template>
              <el-icon class="text-gray-500 cursor-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="flex items-center gap-3">
            <div class="text-5xl font-bold leading-none" :style="{ color: currentMeta.color }">
              {{ currentMeta.icon }}
            </div>
            <div class="min-w-0">
              <div class="text-3xl font-bold" :style="{ color: currentMeta.color }">
                {{ result.labelName }}
              </div>
              <div class="text-xs text-gray-500 mt-1 leading-tight">
                {{ result.label === 0 ? `预计 ${result.forwardDays} 日涨幅 > +${(result.threshold*100).toFixed(0)}%` :
                   result.label === 2 ? `预计 ${result.forwardDays} 日跌幅 > -${(result.threshold*100).toFixed(0)}%` :
                   `预计 ${result.forwardDays} 日波动在 ±${(result.threshold*100).toFixed(0)}% 内` }}
              </div>
            </div>
          </div>
        </div>

        <!-- 置信度 -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/40" style="padding: 20px 24px;">
          <div class="text-xs text-gray-400 mb-3 flex items-center gap-1">
            模型置信度
            <el-tooltip placement="top">
              <template #content>
                即预测类别对应的概率值。<br/>
                随机猜测 ≈ 33.3%；本模型测试集 macro-F1 ≈ 0.45。<br/>
                建议：&ge; 60% 高置信，45-60% 中等，&lt; 45% 低置信仅供参考。
              </template>
              <el-icon class="text-gray-500 cursor-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="flex items-baseline gap-2 flex-wrap">
            <div class="text-4xl font-bold text-white leading-none">
              {{ (result.confidence * 100).toFixed(1) }}<span class="text-2xl">%</span>
            </div>
            <el-tag size="small" :style="{ color: confidenceLevel.color, borderColor: confidenceLevel.color }">
              {{ confidenceLevel.text }}
            </el-tag>
          </div>
          <el-progress
            :percentage="result.confidence * 100"
            :color="confidenceLevel.color"
            :show-text="false"
            class="mt-3"
          />
          <div class="text-xs text-gray-500 mt-2 leading-tight">
            随机基线 33.3%，本预测{{ result.confidence > 0.4 ? '显著高于' : '接近' }}随机水平
          </div>
        </div>

        <!-- 元信息（在 md 屏单独占整行避免截断） -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/40 md:col-span-2 lg:col-span-1" style="padding: 20px 24px;">
          <div class="text-xs text-gray-400 mb-3 flex items-center gap-1">
            预测元信息
            <el-tooltip placement="top">
              <template #content>
                <b>基准日期</b>：模型使用的最新一根 K 线日期<br/>
                <b>预测周期</b>：模型预测未来多少个交易日<br/>
                <b>分类阈值</b>：±N% 是看多/看空的边界<br/>
                <b>模型版本</b>：训练好的模型文件标识
              </template>
              <el-icon class="text-gray-500 cursor-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <div>
              <div class="text-xs text-gray-500">股票代码</div>
              <div class="font-mono font-semibold">{{ result.code }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">基准日期</div>
              <div class="font-mono">{{ result.asOfDate }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">预测周期</div>
              <div>T+{{ result.forwardDays }} 日</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">分类阈值</div>
              <div>±{{ (result.threshold * 100).toFixed(0) }}%</div>
            </div>
            <div class="col-span-2 md:col-span-1 lg:col-span-2">
              <div class="text-xs text-gray-500">模型版本</div>
              <el-tag size="small" type="primary">{{ result.modelVersion }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 双图区 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- 三类概率分布 -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/30" style="padding: 20px 24px;">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-base font-semibold flex items-center gap-2">
              <el-icon class="text-amber-400"><PieChart /></el-icon>
              三类概率分布
            </h3>
            <span class="text-xs text-gray-500">和为 100%</span>
          </div>
          <div ref="probaChartRef" class="w-full h-[220px]"></div>
        </div>

        <!-- Top 特征 -->
        <div class="rounded-2xl border border-gray-800 bg-gray-900/30" style="padding: 20px 24px;">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-base font-semibold flex items-center gap-2">
              <el-icon class="text-amber-400"><DataAnalysis /></el-icon>
              Top 8 关键特征
            </h3>
            <span class="text-xs text-gray-500">柱长 = 模型重要性 · 右侧 = 当前值</span>
          </div>
          <div ref="featureChartRef" class="w-full h-[260px]"></div>
        </div>
      </div>

      <!-- 特征详情表 -->
      <div class="rounded-2xl border border-gray-800 bg-gray-900/30 overflow-hidden">
        <div class="text-base font-semibold border-b border-gray-800" style="padding: 16px 24px;">特征详情</div>
        <div style="padding: 12px 16px 16px;">
        <el-table :data="result.topFeatures" stripe size="small" :show-header="true">
          <el-table-column prop="name" label="特征名" width="200">
            <template #default="{ row }">
              <span class="font-mono text-xs">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="含义" min-width="280">
            <template #default="{ row }">
              <span class="text-gray-400 text-xs">{{ featureDescription(row.name) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="当前值" width="120" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ formatValue(row.name, row.value) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="重要性" width="120" align="right">
            <template #default="{ row }">
              <el-tag size="small" type="primary">{{ formatImportance(row.importance) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        </div>
      </div>

      <!-- AI 智能分析报告 -->
      <div class="rounded-2xl border border-purple-900/40 bg-gradient-to-br from-purple-950/20 to-gray-900/30 overflow-hidden">
        <div class="border-b border-purple-900/30 flex items-center justify-between flex-wrap gap-2" style="padding: 16px 24px;">
          <h3 class="text-base font-semibold flex items-center gap-2">
            <el-icon class="text-purple-400"><ChatLineRound /></el-icon>
            AI 智能分析报告
            <el-tag size="small" type="info">由通义千问基于上方预测数据生成</el-tag>
          </h3>
          <div class="flex items-center gap-2">
            <el-button
              v-if="!reportStreaming && !reportContent"
              round
              size="small"
              class="report-btn"
              @click="generateReport"
            >
              生成分析报告
            </el-button>
            <el-button
              v-if="!reportStreaming && reportContent"
              round
              size="small"
              class="ghost-btn"
              @click="generateReport"
            >
              重新生成
            </el-button>
            <el-button
              v-if="!reportStreaming && reportContent"
              round
              size="small"
              class="ghost-btn"
              @click="copyReport"
            >
              复制
            </el-button>
            <el-button
              v-if="reportStreaming"
              round
              size="small"
              class="ghost-btn-danger"
              @click="stopReport"
            >
              停止生成
            </el-button>
          </div>
        </div>

        <div style="padding: 20px 24px;">
          <!-- 未生成时的占位 -->
          <div v-if="!reportContent && !reportStreaming && !reportError"
               class="text-center text-gray-500 py-8">
            <el-icon class="text-3xl text-purple-400/50"><ChatLineRound /></el-icon>
            <div class="mt-3 text-sm">点击右上角按钮，AI 将基于模型预测数据生成结构化分析报告</div>
            <div class="mt-1 text-xs text-gray-600">报告涵盖：综合判断 / 技术面 / 估值 / 风险 / 操作建议</div>
          </div>

          <!-- 错误 -->
          <el-alert v-if="reportError" :title="reportError" type="error" :closable="false" class="mb-3" />

          <!-- 思考中（已发请求但还没收到第一个 token）-->
          <div v-if="reportStreaming && !reportContent"
               class="flex items-center gap-3 text-purple-300/80 py-3 px-1">
            <div class="thinking-dots">
              <span></span><span></span><span></span>
            </div>
            <span class="text-sm">正在调用预测模型并请通义千问撰写报告... (首次约 3-8 秒)</span>
          </div>

          <!-- 流式 / 已完成内容 -->
          <div v-if="reportContent"
               class="markdown-body text-sm leading-relaxed"
               v-html="renderMarkdown(reportContent + (reportStreaming ? '▌' : ''))">
          </div>
        </div>
      </div>

      <!-- 免责声明 -->
      <el-alert
        :closable="false"
        type="warning"
        show-icon
        title="风险提示"
        :description="result.disclaimer + ' 模型基于过去 3 年数据训练，可能不能反映极端行情。投资决策请综合多方信息，自主判断风险。'"
      />
    </template>
  </div>
</template>

<style scoped>
.font-mono { font-family: ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace; }

/* 操作区圆滑风格 */
.soft-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  padding-left: 14px;
  padding-right: 14px;
  background: rgba(15, 23, 42, 0.6);
  box-shadow: 0 0 0 1px rgba(71, 85, 105, 0.5) inset;
}
.soft-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.7) inset;
}
.quick-tag {
  transition: all 0.2s ease;
  padding: 0 12px;
  height: 26px;
  line-height: 26px;
}
.quick-tag:hover {
  transform: translateY(-1px);
  filter: brightness(1.15);
}

/* 琥珀金主按钮 */
.amber-btn {
  background: linear-gradient(135deg, #f59e0b, #d97706) !important;
  border: none !important;
  color: #1f1300 !important;
  font-weight: 600;
  letter-spacing: 0.04em;
  box-shadow: 0 4px 14px rgba(217, 119, 6, 0.35);
  transition: all 0.2s ease;
}
.amber-btn:hover {
  background: linear-gradient(135deg, #fbbf24, #f59e0b) !important;
  color: #1f1300 !important;
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(217, 119, 6, 0.45);
}
.amber-btn:active {
  transform: translateY(0);
}

/* AI 报告区按钮 */
.report-btn {
  background: linear-gradient(135deg, #7c3aed, #4f46e5) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  letter-spacing: 0.04em;
  padding: 0 18px !important;
  height: 30px;
  box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
  transition: all 0.2s ease;
}
.report-btn:hover {
  background: linear-gradient(135deg, #8b5cf6, #6366f1) !important;
  color: #fff !important;
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(124, 58, 237, 0.5);
}
.ghost-btn {
  background: rgba(148, 163, 184, 0.08) !important;
  border: 1px solid rgba(148, 163, 184, 0.25) !important;
  color: #cbd5e1 !important;
  padding: 0 14px !important;
  height: 30px;
  transition: all 0.2s ease;
}
.ghost-btn:hover {
  background: rgba(148, 163, 184, 0.18) !important;
  border-color: rgba(148, 163, 184, 0.45) !important;
  color: #f1f5f9 !important;
}
.ghost-btn-danger {
  background: rgba(239, 68, 68, 0.1) !important;
  border: 1px solid rgba(239, 68, 68, 0.4) !important;
  color: #fca5a5 !important;
  padding: 0 14px !important;
  height: 30px;
  transition: all 0.2s ease;
}
.ghost-btn-danger:hover {
  background: rgba(239, 68, 68, 0.2) !important;
  color: #fecaca !important;
}

/* AI 报告 markdown 排版（暗色主题） */
.markdown-body { color: #cbd5e1; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  color: #e2e8f0;
  font-weight: 600;
  margin: 1em 0 0.5em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid rgba(148,163,184,0.15);
}
.markdown-body :deep(h2) { font-size: 1.1rem; color: #c4b5fd; }
.markdown-body :deep(h3) { font-size: 1rem; }
.markdown-body :deep(p) { margin: 0.5em 0; line-height: 1.7; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 1.5em; margin: 0.5em 0; }
.markdown-body :deep(li) { margin: 0.25em 0; }
.markdown-body :deep(strong) { color: #fbbf24; font-weight: 600; }
.markdown-body :deep(code) {
  background: rgba(148,163,184,0.15);
  padding: 0.1em 0.4em;
  border-radius: 3px;
  font-size: 0.85em;
  color: #93c5fd;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
  font-size: 0.85em;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(148,163,184,0.2);
  padding: 0.4em 0.8em;
}
.markdown-body :deep(th) { background: rgba(148,163,184,0.08); }
.markdown-body :deep(blockquote) {
  border-left: 3px solid #a78bfa;
  padding-left: 1em;
  color: #94a3b8;
  margin: 0.5em 0;
}

/* 思考中三点跳动动画 */
.thinking-dots {
  display: inline-flex;
  gap: 4px;
}
.thinking-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a78bfa;
  animation: thinkingBounce 1.2s infinite ease-in-out;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes thinkingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}
</style>

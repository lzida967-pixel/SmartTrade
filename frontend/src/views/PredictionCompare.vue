<script setup>
/**
 * 模型对比页：同股同时间并排跑 LightGBM + XGBoost，量化两者分歧度
 *
 * 数据流：
 *   Promise.all([ /api/prediction/predict/{code}?model=lgbm,
 *                 /api/prediction/predict/{code}?model=xgb ])
 * 分歧度：
 *   - 标签一致性（同 / 异）
 *   - 置信度差 |c_lgbm - c_xgb|
 *   - 最大类别概率差  max(|p_i - q_i|)
 *   - Jensen-Shannon 距离（0 = 完全一致, 1 = 完全相反）
 *   - Top 特征交集 / Jaccard
 */
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { compareTreeModels, compareReportStreamUrl } from '../api/prediction'
import { labelMeta, formatValue } from '../utils/predictionFormat'
import { usePredictionSse } from '../composables/usePredictionSse'

const route = useRoute()
const router = useRouter()

const codeInput = ref(route.query.code || '600519')
const loading = ref(false)
const error = ref('')
const results = ref({ lgbm: null, xgb: null })

// AI 解读分歧（复用单预测页的 usePredictionSse composable）
const reportSse = usePredictionSse({
  buildUrl: (code) => compareReportStreamUrl(code),
  errorHint: 'AI 分歧解读失败',
  onBeforeStart: () => {
    if (!results.value.lgbm || !results.value.xgb) {
      ElMessage.warning('请先完成两个模型的预测')
      return false
    }
  }
})
const { content: reportContent, streaming: reportStreaming, errorMsg: reportError } = reportSse
const generateCompareReport = () => reportSse.start(results.value.lgbm?.code)
const stopReport = () => reportSse.stop()
const copyReport = () => reportSse.copy()

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

const modelMeta = {
  lgbm: { name: 'LightGBM', badge: '#60a5fa', accent: 'rgba(96,165,250,.35)' },
  xgb:  { name: 'XGBoost',  badge: '#a78bfa', accent: 'rgba(167,139,250,.35)' }
}

// ---------- 拉取两个模型 ----------
const fetchCompare = async () => {
  const raw = codeInput.value?.trim()
  if (!raw) { ElMessage.warning('请输入 6 位股票代码'); return }
  const code = raw.padStart(6, '0')
  if (!/^\d{6}$/.test(code)) { ElMessage.warning('股票代码必须是 6 位数字'); return }

  loading.value = true
  error.value = ''
  results.value = { lgbm: null, xgb: null }

  try {
    const [a, b] = await compareTreeModels(code)
    if (a.status === 'fulfilled') results.value.lgbm = a.value.data
    if (b.status === 'fulfilled') results.value.xgb  = b.value.data
    if (a.status === 'rejected' && b.status === 'rejected') {
      error.value = a.reason?.response?.data?.msg || '两个模型均调用失败'
    } else if (a.status === 'rejected') {
      ElMessage.warning('LightGBM 调用失败：' + (a.reason?.response?.data?.msg || ''))
    } else if (b.status === 'rejected') {
      ElMessage.warning('XGBoost 调用失败：' + (b.reason?.response?.data?.msg || ''))
    }
    renderProbaCompareChart()
  } finally {
    loading.value = false
  }
}

const selectQuick = (c) => { codeInput.value = c; fetchCompare() }
const goSingle = () => router.push({ path: '/prediction', query: { code: codeInput.value } })

// 切换股票时清空上一次的 AI 分歧解读
watch(codeInput, () => reportSse.reset())

// ---------- 分歧度计算 ----------
const divergence = computed(() => {
  const a = results.value.lgbm, b = results.value.xgb
  if (!a || !b) return null
  const pa = [a.proba.bullish, a.proba.neutral, a.proba.bearish]
  const pb = [b.proba.bullish, b.proba.neutral, b.proba.bearish]

  const sameLabel = a.label === b.label
  const confGap = Math.abs(a.confidence - b.confidence)
  const maxClassGap = Math.max(...pa.map((v, i) => Math.abs(v - pb[i])))

  // Jensen-Shannon distance，log 以 2 为底，结果归一化到 [0, 1]
  const log2 = (x) => Math.log(x) / Math.LN2
  const kl = (p, q) => p.reduce((s, v, i) => {
    if (v <= 0) return s
    const qi = q[i] <= 0 ? 1e-12 : q[i]
    return s + v * log2(v / qi)
  }, 0)
  const m = pa.map((v, i) => (v + pb[i]) / 2)
  const jsDiv = 0.5 * kl(pa, m) + 0.5 * kl(pb, m)  // [0, 1]
  const jsDistance = Math.sqrt(Math.max(jsDiv, 0))  // [0, 1]

  // Top 特征重叠
  const fa = new Set((a.topFeatures || []).map(f => f.name))
  const fb = new Set((b.topFeatures || []).map(f => f.name))
  const inter = [...fa].filter(x => fb.has(x))
  const union = new Set([...fa, ...fb])
  const jaccard = union.size ? inter.length / union.size : 0

  // 综合结论
  let verdict
  if (sameLabel && jsDistance < 0.25) {
    verdict = { level: 'consensus', text: '强共识', color: '#10b981',
                desc: '两模型方向一致且概率分布接近，信号较可靠' }
  } else if (sameLabel) {
    verdict = { level: 'agree', text: '方向一致', color: '#3b82f6',
                desc: '方向相同但概率分布存在差异，可作为主要参考' }
  } else if (jsDistance < 0.5) {
    verdict = { level: 'diverge', text: '温和分歧', color: '#f59e0b',
                desc: '两模型结论不同但分布相近，建议谨慎，配合其它信号' }
  } else {
    verdict = { level: 'conflict', text: '显著分歧', color: '#ef4444',
                desc: '模型结论严重冲突，不建议据此单独决策' }
  }

  return { sameLabel, confGap, maxClassGap, jsDistance, jaccard, inter, verdict }
})

// ---------- 概率并排柱图 ----------
let probaCompareChart = null
const probaCompareChartRef = ref(null)

const renderProbaCompareChart = async () => {
  // 等一帧：v-if 新建 DOM 后需要浏览器完成 layout，否则 ECharts 会按 0 宽初始化
  await nextTick()
  await new Promise(r => requestAnimationFrame(r))
  if (!probaCompareChartRef.value) return
  const a = results.value.lgbm, b = results.value.xgb
  if (!a || !b) return
  // 每次都 dispose 旧实例：切换股票时旧 DOM 被 v-if 销毁，缓存实例指向悬空节点
  if (probaCompareChart) { probaCompareChart.dispose(); probaCompareChart = null }
  probaCompareChart = echarts.init(probaCompareChartRef.value, 'dark')

  const labels = ['看多', '震荡', '看空']
  const pa = [a.proba.bullish, a.proba.neutral, a.proba.bearish]
  const pb = [b.proba.bullish, b.proba.neutral, b.proba.bearish]

  probaCompareChart.setOption({
    backgroundColor: 'transparent',
    legend: { data: ['LightGBM', 'XGBoost'], textStyle: { color: '#cbd5e1' }, top: 4 },
    grid: { left: 60, right: 40, top: 40, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' },
               valueFormatter: v => (v * 100).toFixed(1) + '%' },
    xAxis: {
      type: 'category', data: labels,
      axisLabel: { color: '#cbd5e1', fontSize: 13, fontWeight: 600 }
    },
    yAxis: {
      type: 'value', max: 1,
      axisLabel: { formatter: v => (v * 100).toFixed(0) + '%', color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,.1)' } }
    },
    series: [
      { name: 'LightGBM', type: 'bar', data: pa, barWidth: 24,
        itemStyle: { color: '#60a5fa', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top',
                 formatter: ({ value }) => (value * 100).toFixed(1) + '%',
                 color: '#60a5fa', fontSize: 11, fontWeight: 600 } },
      { name: 'XGBoost', type: 'bar', data: pb, barWidth: 24,
        itemStyle: { color: '#a78bfa', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top',
                 formatter: ({ value }) => (value * 100).toFixed(1) + '%',
                 color: '#a78bfa', fontSize: 11, fontWeight: 600 } }
    ]
  })
  probaCompareChart.resize()
}

const onResize = () => probaCompareChart?.resize()

onMounted(() => {
  window.addEventListener('resize', onResize)
  fetchCompare()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  probaCompareChart?.dispose()
})

watch(() => route.query.code, (c) => {
  if (c && c !== codeInput.value) { codeInput.value = c; fetchCompare() }
})
</script>

<template>
  <div style="padding: 15px; display: flex; flex-direction: column; gap: 15px;">
    <!-- 标题 -->
    <div>
      <h2 class="text-2xl font-bold flex items-center gap-2 flex-wrap">
        <el-icon class="text-amber-400"><Connection /></el-icon>
        模型对比
        <el-tag size="small" type="info">LightGBM vs XGBoost · T+5</el-tag>
      </h2>
      <p class="text-sm text-gray-400 mt-1">
        同一只股票、同一基准日，并排展示两个模型的概率分布、Top 特征与分歧度，辅助你判断信号可靠度
      </p>
    </div>

    <!-- 操作区 -->
    <div class="rounded-2xl p-4 border border-gray-800 bg-gray-900/30 flex items-center gap-3 flex-wrap">
      <el-input
        v-model="codeInput"
        placeholder="输入 6 位股票代码"
        class="!w-52"
        maxlength="6"
        @keyup.enter="fetchCompare"
        clearable
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button round type="primary" :loading="loading" @click="fetchCompare" class="!px-6">
        开始对比
      </el-button>
      <el-button round plain size="small" @click="goSingle">
        <el-icon class="mr-1"><Back /></el-icon>返回单模型模式
      </el-button>
      <el-divider direction="vertical" class="!mx-3" />
      <span class="text-xs text-gray-500 mr-1">常用：</span>
      <el-tag
        v-for="q in quickCodes" :key="q.code"
        round class="!cursor-pointer"
        :effect="codeInput === q.code ? 'dark' : 'plain'"
        @click="selectQuick(q.code)"
      >
        {{ q.code }} {{ q.name }}
      </el-tag>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <div v-if="loading && !results.lgbm && !results.xgb" class="text-center py-20 text-gray-500">
      <el-icon class="animate-spin text-3xl"><Loading /></el-icon>
      <div class="mt-3">正在并行调用 LightGBM 与 XGBoost 推理...</div>
    </div>

    <!-- 分歧度总览 -->
    <div v-if="divergence"
         class="rounded-2xl border bg-gray-900/40"
         :style="{ borderColor: divergence.verdict.color + '66', padding: '18px 22px' }">
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div class="flex items-center gap-3">
          <div class="text-2xl font-bold" :style="{ color: divergence.verdict.color }">
            {{ divergence.verdict.text }}
          </div>
          <span class="text-xs text-gray-400">{{ divergence.verdict.desc }}</span>
        </div>
        <div class="flex items-center gap-5 text-xs flex-wrap">
          <div>
            <span class="text-gray-500 mr-1">标签</span>
            <el-tag size="small" :type="divergence.sameLabel ? 'success' : 'danger'" effect="plain">
              {{ divergence.sameLabel ? '一致' : '不一致' }}
            </el-tag>
          </div>
          <div>
            <span class="text-gray-500 mr-1">置信度差</span>
            <span class="font-mono font-semibold">{{ (divergence.confGap * 100).toFixed(1) }}%</span>
          </div>
          <div>
            <span class="text-gray-500 mr-1">最大类别差</span>
            <span class="font-mono font-semibold">{{ (divergence.maxClassGap * 100).toFixed(1) }}%</span>
          </div>
          <div>
            <el-tooltip content="Jensen-Shannon 距离，0=分布完全一致，1=完全相反" placement="top">
              <span>
                <span class="text-gray-500 mr-1">JS 距离</span>
                <span class="font-mono font-semibold">{{ divergence.jsDistance.toFixed(3) }}</span>
              </span>
            </el-tooltip>
          </div>
          <div>
            <el-tooltip content="Top 特征集合的 Jaccard 相似度" placement="top">
              <span>
                <span class="text-gray-500 mr-1">特征重叠</span>
                <span class="font-mono font-semibold">{{ (divergence.jaccard * 100).toFixed(0) }}%</span>
              </span>
            </el-tooltip>
          </div>
        </div>
      </div>
      <el-progress
        :percentage="divergence.jsDistance * 100"
        :color="divergence.verdict.color"
        :show-text="false"
        :stroke-width="6"
        class="mt-3"
      />
    </div>

    <!-- 并排模型卡 -->
    <div v-if="results.lgbm || results.xgb"
         class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <template v-for="mk in ['lgbm','xgb']" :key="mk">
        <div class="rounded-2xl border bg-gray-900/30"
             :style="{ borderColor: modelMeta[mk].accent, padding: '18px 22px' }">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="model-badge" :style="{ background: modelMeta[mk].badge + '22',
                                                   color: modelMeta[mk].badge,
                                                   borderColor: modelMeta[mk].badge + '55' }">
                {{ modelMeta[mk].name }}
              </span>
              <span v-if="results[mk]" class="font-mono text-xs text-gray-500">
                {{ results[mk].modelVersion }}
              </span>
            </div>
            <span v-if="results[mk]" class="text-xs text-gray-500 font-mono">
              {{ results[mk].asOfDate }} · T+{{ results[mk].forwardDays }}
            </span>
          </div>

          <div v-if="!results[mk]" class="text-center py-8 text-gray-500 text-sm">
            <el-icon><Warning /></el-icon> 该模型未返回结果
          </div>

          <template v-else>
            <!-- 结论 + 置信度 -->
            <div class="flex items-center justify-between rounded-xl mb-4"
                 :style="{ background: labelMeta[results[mk].label].bg, padding: '14px 18px' }">
              <div class="flex items-center gap-3">
                <div class="text-4xl font-bold leading-none"
                     :style="{ color: labelMeta[results[mk].label].color }">
                  {{ labelMeta[results[mk].label].icon }}
                </div>
                <div>
                  <div class="text-xl font-bold"
                       :style="{ color: labelMeta[results[mk].label].color }">
                    {{ results[mk].labelName }}
                  </div>
                  <div class="text-[11px] text-gray-500">
                    阈值 ±{{ (results[mk].threshold * 100).toFixed(0) }}%
                  </div>
                </div>
              </div>
              <div class="text-right">
                <div class="text-2xl font-bold">
                  {{ (results[mk].confidence * 100).toFixed(1) }}<span class="text-sm">%</span>
                </div>
                <div class="text-[11px] text-gray-500">置信度</div>
              </div>
            </div>

            <!-- 三类概率条 -->
            <div class="space-y-2 mb-4">
              <div v-for="(key, idx) in ['bullish','neutral','bearish']" :key="key"
                   class="flex items-center gap-2 text-xs">
                <span class="w-10 text-gray-400">{{ ['看多','震荡','看空'][idx] }}</span>
                <el-progress
                  class="flex-1"
                  :percentage="results[mk].proba[key] * 100"
                  :color="labelMeta[idx].color"
                  :show-text="false"
                  :stroke-width="10"
                />
                <span class="w-14 text-right font-mono"
                      :style="{ color: labelMeta[idx].color }">
                  {{ (results[mk].proba[key] * 100).toFixed(1) }}%
                </span>
              </div>
            </div>

            <!-- Top 特征 -->
            <div class="text-xs text-gray-400 mb-2 flex items-center justify-between">
              <span>Top 8 关键特征</span>
              <span class="text-gray-600">按重要性降序 · 右侧为当前值</span>
            </div>
            <div class="feature-list">
              <div v-for="f in results[mk].topFeatures" :key="f.name"
                   class="feature-row"
                   :class="{
                     'feature-shared':
                       divergence && divergence.inter.includes(f.name)
                   }">
                <span class="font-mono text-xs truncate" :title="f.name">{{ f.name }}</span>
                <span class="text-xs font-mono feature-value">
                  {{ formatValue(f.name, f.value) }}
                </span>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>

    <!-- 概率对比柱图 -->
    <div v-if="results.lgbm && results.xgb"
         class="rounded-2xl border border-gray-800 bg-gray-900/30"
         style="padding: 18px 22px;">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-base font-semibold flex items-center gap-2">
          <el-icon class="text-amber-400"><Histogram /></el-icon>
          三类概率并排对比
        </h3>
        <span class="text-xs text-gray-500">柱高越接近 → 模型共识越强</span>
      </div>
      <div ref="probaCompareChartRef" class="w-full h-[280px]"></div>
    </div>

    <!-- 共同 / 独有特征 -->
    <div v-if="divergence"
         class="rounded-2xl border border-gray-800 bg-gray-900/30"
         style="padding: 18px 22px;">
      <h3 class="text-base font-semibold mb-3 flex items-center gap-2">
        <el-icon class="text-amber-400"><Share /></el-icon>
        Top 特征交集
        <span class="text-xs text-gray-500 font-normal">
          （两模型共同看重 {{ divergence.inter.length }} 项，Jaccard {{ (divergence.jaccard * 100).toFixed(0) }}%）
        </span>
      </h3>
      <div v-if="divergence.inter.length" class="flex flex-wrap gap-2">
        <el-tag v-for="n in divergence.inter" :key="n" effect="dark" type="success" class="!font-mono">
          {{ n }}
        </el-tag>
      </div>
      <div v-else class="text-xs text-gray-500">两模型 Top 特征完全不同，数据解读视角差异较大。</div>
    </div>

    <!-- AI 分歧解读 -->
    <div v-if="results.lgbm && results.xgb"
         class="rounded-2xl border border-purple-900/40 bg-gradient-to-br from-purple-950/20 to-gray-900/30 overflow-hidden">
      <div class="border-b border-purple-900/30 flex items-center justify-between flex-wrap gap-2"
           style="padding: 16px 22px;">
        <h3 class="text-base font-semibold flex items-center gap-2">
          <el-icon class="text-purple-400"><ChatLineRound /></el-icon>
          AI 解读分歧
          <el-tag size="small" type="info">由通义千问基于两模型差异撰写</el-tag>
        </h3>
        <div class="flex items-center gap-2">
          <el-button v-if="!reportStreaming && !reportContent"
                     round size="small" type="primary"
                     @click="generateCompareReport">
            生成分歧解读
          </el-button>
          <el-button v-if="!reportStreaming && reportContent"
                     round size="small" plain
                     @click="generateCompareReport">
            重新生成
          </el-button>
          <el-button v-if="!reportStreaming && reportContent"
                     round size="small" plain
                     @click="copyReport">
            复制
          </el-button>
          <el-button v-if="reportStreaming"
                     round size="small" type="danger" plain
                     @click="stopReport">
            停止生成
          </el-button>
        </div>
      </div>

      <div style="padding: 18px 22px;">
        <div v-if="!reportContent && !reportStreaming && !reportError"
             class="text-center text-gray-500 py-6">
          <el-icon class="text-3xl text-purple-400/50"><ChatLineRound /></el-icon>
          <div class="mt-2 text-sm">点击右上角「生成分歧解读」，AI 将基于两模型的概率分布与特征差异写一份「为什么分歧、该听谁」的分析</div>
        </div>

        <el-alert v-if="reportError" :title="reportError" type="error" :closable="false" class="mb-3" />

        <div v-if="reportStreaming && !reportContent"
             class="flex items-center gap-3 text-purple-300/80 py-3 px-1">
          <div class="thinking-dots"><span></span><span></span><span></span></div>
          <span class="text-sm">正在并行调用两模型并请通义千问分析... (首次约 3-8 秒)</span>
        </div>

        <div v-if="reportContent"
             class="markdown-body text-sm leading-relaxed"
             v-html="renderMarkdown(reportContent + (reportStreaming ? '▌' : ''))">
        </div>
      </div>
    </div>

    <el-alert
      v-if="results.lgbm || results.xgb"
      :closable="false"
      type="warning"
      show-icon
      title="风险提示"
      description="模型对比结果仅供研究参考，不构成投资建议。显著分歧往往出现在信号噪声较大的市场环境，请结合基本面与盘面自主判断。"
    />
  </div>
</template>

<style scoped>
.model-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  letter-spacing: .5px;
}
.feature-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
}
.feature-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, .02);
  border: 1px solid rgba(255, 255, 255, .04);
}
.feature-row.feature-shared {
  background: rgba(16, 185, 129, .08);
  border-color: rgba(16, 185, 129, .35);
}
.feature-value {
  color: #fbbf24;
  font-weight: 600;
  white-space: nowrap;
  margin-left: 8px;
}

/* Markdown 简易样式 */
.markdown-body :deep(h2) {
  font-size: 15px; font-weight: 700; color: #e9d5ff;
  margin: 18px 0 8px; padding-bottom: 4px;
  border-bottom: 1px solid rgba(167, 139, 250, .2);
}
.markdown-body :deep(h3) {
  font-size: 14px; font-weight: 600; color: #c4b5fd;
  margin: 14px 0 6px;
}
.markdown-body :deep(p) { margin: 6px 0; color: #cbd5e1; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 6px 0 6px 20px; color: #cbd5e1; }
.markdown-body :deep(li) { margin: 3px 0; }
.markdown-body :deep(strong) { color: #fde68a; }
.markdown-body :deep(code) {
  background: rgba(139, 92, 246, .12);
  color: #d8b4fe;
  padding: 1px 6px; border-radius: 4px;
  font-size: 12px;
}
.markdown-body :deep(table) {
  border-collapse: collapse; margin: 8px 0; font-size: 12px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(148, 163, 184, .2);
  padding: 4px 10px; text-align: left;
}
.markdown-body :deep(th) {
  background: rgba(139, 92, 246, .12); color: #e9d5ff;
}

/* 思考中三点动画 */
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
  40% { opacity: 1; transform: scale(1.1); }
}
</style>

<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><MagicStick /></el-icon></span>AI 预测准确率</h2>
        <div class="pt-sub">基于 T+5 三分类回测，统计各模型历史预测命中率</div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-select v-model="days" class="!w-32" @change="loadStats">
          <el-option :value="7"  label="近 7 天" />
          <el-option :value="14" label="近 14 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="60" label="近 60 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button type="warning" plain :loading="verifying" @click="onVerify">
          <el-icon class="mr-1"><Refresh /></el-icon>核对预测准确率
        </el-button>
        <el-button plain :loading="loading" @click="loadStats">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 待核对提示 -->
    <el-alert
      v-if="pendingCount > 0"
      type="info"
      :closable="false"
      show-icon
      :title="`当前有 ${pendingCount} 条预测记录待核对（包含 T+5 尚未到期 / 行情未入库的记录）。点击「核对预测准确率」将自动核对其中已满足 T+5 条件的记录。`"
    />
    <el-alert
      v-else-if="pendingCount === 0 && stats.length > 0"
      type="success"
      :closable="false"
      show-icon
      title="所有预测记录均已核对"
    />

    <!-- 三模型准确率卡 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <div
        v-for="row in stats"
        :key="row.modelVersion"
        class="admin-card flex flex-col gap-4"
      >
        <!-- 模型名称 -->
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span
              class="w-2.5 h-2.5 rounded-full"
              :style="{ background: modelColor(row.modelVersion) }"
            ></span>
            <span class="font-semibold text-gray-100">{{ row.modelName }}</span>
            <span class="text-[11px] text-gray-600 font-mono">{{ row.modelVersion }}</span>
          </div>
          <span class="dot-tag" :class="accClass(row.accuracy)">
            {{ row.verifiedCount > 0 ? accLabel(row.accuracy) : '暂无数据' }}
          </span>
        </div>

        <!-- 准确率圆环 + 数字 -->
        <div class="flex items-center gap-5">
          <el-progress
            type="circle"
            :percentage="row.verifiedCount > 0 ? +(row.accuracy * 100).toFixed(1) : 0"
            :width="88"
            :stroke-width="8"
            :color="progressColor(row.accuracy)"
          >
            <template #default="{ percentage }">
              <span class="text-xl font-black" :style="{ color: progressColor(row.accuracy) }">
                {{ row.verifiedCount > 0 ? percentage.toFixed(1) : '--' }}<span v-if="row.verifiedCount > 0" class="text-sm font-normal">%</span>
              </span>
            </template>
          </el-progress>
          <div class="flex-1 space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-gray-500">已验证</span>
              <span class="font-mono text-gray-200">{{ row.verifiedCount }} 条</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">命中</span>
              <span class="font-mono text-emerald-400">{{ row.correctCount }} 条</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">统计区间</span>
              <span class="font-mono text-gray-400">近 {{ row.days }} 天</span>
            </div>
          </div>
        </div>

        <!-- 准确率进度条 -->
        <div>
          <div class="flex justify-between text-[11px] text-gray-500 mb-1">
            <span>准确率</span>
            <span class="font-mono" :style="{ color: progressColor(row.accuracy) }">
              {{ row.verifiedCount > 0 ? (row.accuracy * 100).toFixed(2) + '%' : '--' }}
            </span>
          </div>
          <el-progress
            :percentage="row.verifiedCount > 0 ? +(row.accuracy * 100).toFixed(1) : 0"
            :stroke-width="6"
            :show-text="false"
            :color="progressColor(row.accuracy)"
          />
          <div class="flex justify-between text-[10px] text-gray-700 mt-1">
            <span>0%</span>
            <span class="text-amber-600/60">随机基准 33.3%</span>
            <span>100%</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 对比柱状图 -->
    <div class="admin-card">
      <div class="flex items-center justify-between mb-4">
        <div class="text-sm font-semibold text-gray-200 flex items-center gap-2">
          <el-icon class="text-indigo-400"><DataAnalysis /></el-icon>
          模型准确率对比
        </div>
        <span class="text-xs text-gray-500">水平虚线为随机基准（33.3%）</span>
      </div>
      <div ref="chartRef" class="w-full h-[240px]"></div>
    </div>

    <!-- 说明卡 -->
    <div class="admin-card">
      <div class="text-sm font-semibold text-gray-300 mb-3 flex items-center gap-2">
        <el-icon class="text-indigo-400"><InfoFilled /></el-icon>
        核对逻辑说明
      </div>
      <div class="text-xs text-gray-500 space-y-1.5 leading-relaxed">
        <div>· 每次用户调用预测接口，系统自动落库一条记录（包含预测信号 BUY/HOLD/SELL 和基准收盘价）</div>
        <div>· <strong class="text-gray-300">核对</strong> = 查找 T+5 个交易日后的实际收盘价，与预测基准价比较计算实际涨跌幅，归类三分类标签，与预测标签对比</div>
        <div>· 判断规则：实际涨幅 &gt; +3% → 看多；实际跌幅 &gt; -3% → 看空；否则震荡</div>
        <div>· <strong class="text-amber-300">随机基准</strong> 为 33.3%（三分类随机猜测期望），准确率需明显高于此才有意义</div>
        <div>· 核对依赖本地 DB 的日 K 数据，如 T+5 行情未入库则跳过，等下次同步后再核对</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Refresh, DataAnalysis, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../../utils/request'

const days = ref(30)
const loading = ref(false)
const verifying = ref(false)
const stats = ref([])

const pendingCount = computed(() => {
  if (!stats.value.length) return 0
  return stats.value[0]?.pendingCount ?? 0
})

const loadStats = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/prediction/stats', { params: { days: days.value } })
    if (res.code === 200) {
      stats.value = res.data || []
      await nextTick()
      renderChart()
    }
  } finally {
    loading.value = false
  }
}

const onVerify = async () => {
  verifying.value = true
  try {
    const res = await request.post('/admin/prediction/verify')
    if (res.code === 200) {
      const count = res.data?.verifiedCount ?? 0
      if (count > 0) {
        ElMessage.success(`本次核对了 ${count} 条预测记录`)
      } else {
        ElMessage.info('暂无可核对记录——待核对记录的 T+5 可能尚未到期，或本地日 K 数据未同步到对应日期')
      }
      await loadStats()
    }
  } finally {
    verifying.value = false
  }
}

// ---------- 颜色工具 ----------
const modelColor = (version) => {
  if (!version) return '#6366f1'
  const v = version.toLowerCase()
  if (v.startsWith('lgbm')) return '#60a5fa'
  if (v.startsWith('xgb'))  return '#a78bfa'
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

const accClass = (acc) => {
  const v = Number(acc) || 0
  if (v >= 0.55) return 'success'
  if (v >= 0.45) return 'warn'
  if (v >= 0.33) return 'danger'
  return 'muted'
}

const accLabel = (acc) => {
  const v = Number(acc) || 0
  if (v >= 0.55) return '表现良好'
  if (v >= 0.45) return '接近基准'
  if (v >= 0.33) return '偏弱'
  return '数据不足'
}

// ---------- ECharts ----------
const chartRef = ref(null)
let chart = null

const renderChart = () => {
  if (!chartRef.value || !stats.value.length) return
  if (!chart) chart = echarts.init(chartRef.value, 'dark')

  const names = stats.value.map(r => r.modelName)
  const accs  = stats.value.map(r => +(Number(r.accuracy) * 100).toFixed(2))
  const colors = stats.value.map(r => modelColor(r.modelVersion))

  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 60, right: 30, top: 30, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params[0]
        const row = stats.value[p.dataIndex]
        return `<div style="font-weight:600;margin-bottom:4px">${p.name}</div>
                <div>准确率：<b>${p.value}%</b></div>
                <div>已验证：${row.verifiedCount} 条（命中 ${row.correctCount}）</div>`
      }
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { color: '#cbd5e1', fontSize: 13, fontWeight: 600 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0, max: 100,
      axisLabel: { formatter: v => v + '%', color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.08)' } }
    },
    series: [
      {
        type: 'bar',
        data: accs.map((v, i) => ({ value: v, itemStyle: { color: colors[i], borderRadius: [6, 6, 0, 0] } })),
        barWidth: 60,
        label: {
          show: true, position: 'top',
          formatter: ({ value }) => value > 0 ? value + '%' : '--',
          color: '#e2e8f0', fontWeight: 700, fontSize: 13
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: 33.3 }],
          lineStyle: { color: '#fbbf24', type: 'dashed', width: 1.5 },
          label: { formatter: '33.3% 随机基准', color: '#fbbf24', fontSize: 11 }
        }
      }
    ]
  })
  chart.resize()
}

const onResize = () => chart?.resize()
onMounted(() => { window.addEventListener('resize', onResize); loadStats() })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); chart?.dispose() })
watch(days, loadStats)
</script>

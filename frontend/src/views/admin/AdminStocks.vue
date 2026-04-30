<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><Coin /></el-icon></span>股票池管理</h2>
        <div class="pt-sub">维护交易股票范围与日 K 数据同步</div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-button type="success" plain @click="addDialog = true">
          <el-icon class="mr-1"><Plus /></el-icon>新增股票
        </el-button>
        <el-button type="warning" plain :loading="syncMissing" @click="onSyncMissing">
          <el-icon class="mr-1"><MagicStick /></el-icon>补齐缺数据
        </el-button>
        <el-button type="primary" :loading="syncAll" @click="onSyncAll">
          <el-icon class="mr-1"><Refresh /></el-icon>全量日 K 同步
        </el-button>
      </div>
    </div>

    <!-- 统计卡片（全局口径，不跟随翻页） -->
    <div class="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-4">
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(99,102,241,.15); color:#a5b4fc"><el-icon><Coin /></el-icon></div>
        <div>
          <div class="stat-label">股票总数</div>
          <div class="stat-value">{{ stats.total }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(16,185,129,.12); color:#34d399"><el-icon><CircleCheck /></el-icon></div>
        <div>
          <div class="stat-label">已有日 K 数据</div>
          <div class="stat-value" style="color:#34d399">{{ stats.withData }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(239,68,68,.1); color:#f87171"><el-icon><Warning /></el-icon></div>
        <div>
          <div class="stat-label">缺数据股票</div>
          <div class="stat-value" :style="stats.missing > 0 ? 'color:#f87171' : 'color:#9ca3af'">{{ stats.missing }}</div>
        </div>
      </div>
    </div>

    <!-- 同步进度面板（running 时才出现） -->
    <div v-if="sync.running" class="admin-card p-4 mb-4 border border-indigo-500/20">
      <div class="flex items-center justify-between mb-2">
        <div class="flex items-center gap-2 text-sm">
          <span class="w-2 h-2 rounded-full bg-indigo-400 animate-pulse shadow-[0_0_8px_#818cf8]"></span>
          <span class="text-gray-200 font-medium">{{ syncTypeLabel }} 进行中</span>
          <span class="text-gray-500 text-xs font-mono">{{ sync.currentStock || '准备中' }}</span>
        </div>
        <div class="text-xs text-gray-500 font-mono">
          {{ sync.processed }} / {{ sync.total }} ·
          成功 <span class="text-emerald-400">{{ sync.success }}</span> ·
          失败 <span class="text-rose-400">{{ sync.failed }}</span>
        </div>
      </div>
      <el-progress
        :percentage="syncPercent"
        :stroke-width="8"
        :show-text="false"
        :color="syncPercent >= 100 ? '#34d399' : '#818cf8'"
      />
      <div v-if="sync.lastError" class="mt-2 text-[11px] text-rose-400 font-mono truncate">
        最近错误：{{ sync.lastError }}
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="admin-toolbar">
      <div class="flex items-center gap-2 text-xs text-gray-500">
        <el-icon><Search /></el-icon><span>查找股票</span>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-input v-model="search" placeholder="代码/名称/行业" clearable class="!w-56"
                  @keyup.enter="onSearch" @clear="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="marketFilter" placeholder="市场" clearable class="!w-32" @change="onSearch">
          <el-option label="全部" value="" />
          <el-option label="上交所" value="SH" />
          <el-option label="深交所" value="SZ" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="onSearch">
          <el-icon class="mr-1"><Refresh /></el-icon>查询
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card p-4">
      <el-table :data="stocks" class="admin-table" v-loading="loading"
                element-loading-background="rgba(0,0,0,0.4)" empty-text="股票池为空">
        <el-table-column prop="stockCode" label="代码" width="120">
          <template #default="{ row }">
            <span class="font-mono text-indigo-300 font-semibold bg-indigo-500/10 border border-indigo-500/20 px-2 py-0.5 rounded text-xs">
              {{ row.stockCode }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="名称" min-width="140">
          <template #default="{ row }">
            <span class="text-gray-100">{{ row.stockName || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="市场" width="100" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="row.market === 'SH' ? 'danger' : 'info'">
              {{ row.market === 'SH' ? '上交所' : '深交所' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="industryName" label="行业" min-width="140">
          <template #default="{ row }">
            <span class="text-gray-400 text-xs">{{ row.industryName || row.plateType || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="日 K 数据" min-width="160" align="center">
          <template #default="{ row }">
            <span v-if="row.dailyPriceCount > 0" class="dot-tag success">
              {{ row.dailyPriceCount }} 条
            </span>
            <span v-else class="dot-tag danger">缺数据</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end pt-3">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100, 200]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="reload"
        />
      </div>
    </div>

    <!-- 新增股票 -->
    <el-dialog v-model="addDialog" title="新增股票到股票池" width="480px" destroy-on-close>
      <el-form label-width="100px" label-position="left">
        <el-form-item label="股票代码" required>
          <el-input v-model="addForm.stockCode" placeholder="如 600519" />
        </el-form-item>
        <el-form-item label="股票名称">
          <el-input v-model="addForm.stockName" placeholder="如 贵州茅台" />
        </el-form-item>
        <el-form-item label="市场" required>
          <el-radio-group v-model="addForm.market">
            <el-radio value="SH">上交所 (SH)</el-radio>
            <el-radio value="SZ">深交所 (SZ)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="addForm.industryName" placeholder="如 白酒" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="onAdd">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Coin, Search, Plus, Refresh, MagicStick, CircleCheck, Warning
} from '@element-plus/icons-vue'
import request from '../../utils/request'

const stocks = ref([])
const loading = ref(false)
const search = ref('')
const marketFilter = ref('')

const page = ref(1)
const size = ref(20)
const total = ref(0)

const stats = ref({ total: 0, withData: 0, missing: 0 })

const sync = ref({
  running: false, type: '', currentStock: '',
  total: 0, processed: 0, success: 0, failed: 0,
  startedAt: null, finishedAt: null, lastError: null
})
let syncTimer = null
// 记录触发同步前服务端的 finishedAt；任务完成后该值会被后端更新，
// 本地只要发现不一致就能识别出“在两次轮询之间闪现完成”的短任务。
let priorFinishedAt = null

const syncPercent = computed(() => {
  const t = sync.value.total || 0
  if (!t) return 0
  return Math.min(100, Math.round((sync.value.processed / t) * 100))
})

const syncTypeLabel = computed(() => {
  const map = { ALL: '全量日 K 同步', MISSING: '补齐缺数据股票', SCHEDULED: '定时同步', COLDSTART: '冷启动同步' }
  return map[sync.value.type] || '同步任务'
})

const pollSyncStatus = async () => {
  try {
    const res = await request.get('/admin/stocks/sync-status')
    if (res.code !== 200) return
    const wasRunning = sync.value.running
    sync.value = res.data

    // 识别“完成”的两种情形：
    //   1) 上一次看到还在跑，本次 running=false （正常步调）
    //   2) 本地控制台刚触发过任务，priorFinishedAt 与服务端 finishedAt 不同
    //      → 说明中间跑完了一轮（不管轮询有没有看到中间的 running=true）
    const finishedAtChanged = priorFinishedAt !== null
        && sync.value.finishedAt
        && sync.value.finishedAt !== priorFinishedAt
    const taskFinishedNow = !sync.value.running && (wasRunning || finishedAtChanged)

    if (taskFinishedNow) {
      stopPollSync()
      priorFinishedAt = null
      ElMessage.success(`${syncTypeLabel.value}完成：成功 ${sync.value.success} / ${sync.value.total}`)
      reload()
      loadStats()
    } else if (sync.value.running) {
      // 还在跑，保证轮询在跳
      if (!syncTimer) startPollSync()
    } else if (priorFinishedAt !== null) {
      // 刚触发但任务还没指 “开始” / 也未“结束”（后台线程还没调起来）
      // 继续轮询，不要提前退出
      if (!syncTimer) startPollSync()
    }
  } catch (_) { /* 静默 */ }
}
const startPollSync = () => {
  if (syncTimer) return
  syncTimer = setInterval(pollSyncStatus, 2000)
}
const stopPollSync = () => {
  if (syncTimer) { clearInterval(syncTimer); syncTimer = null }
}

const addDialog = ref(false)
const addForm = ref({ stockCode: '', stockName: '', market: 'SH', industryName: '' })
const adding = ref(false)

const syncAll = ref(false)
const syncMissing = ref(false)

const loadStats = async () => {
  try {
    const res = await request.get('/admin/stocks/stats')
    if (res.code === 200) stats.value = res.data || { total: 0, withData: 0, missing: 0 }
  } catch (_) { /* 全局不阻断列表 */ }
}

const reload = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/stocks', {
      params: {
        page: page.value,
        size: size.value,
        keyword: search.value.trim() || undefined,
        market: marketFilter.value || undefined
      }
    })
    if (res.code === 200) {
      stocks.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

const onSearch = () => {
  page.value = 1
  reload()
}

const onAdd = async () => {
  if (!addForm.value.stockCode || !addForm.value.market) {
    ElMessage.warning('代码和市场必填')
    return
  }
  adding.value = true
  try {
    const res = await request.post('/admin/stocks', addForm.value)
    if (res.code === 200) {
      ElMessage.success('已加入股票池，可点击"仅同步缺数据"补日 K')
      addDialog.value = false
      addForm.value = { stockCode: '', stockName: '', market: 'SH', industryName: '' }
      reload()
      loadStats()
    }
  } finally { adding.value = false }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认从股票池删除 ${row.stockCode} ${row.stockName || ''}？\n该股票的 ${row.dailyPriceCount || 0} 条日 K 数据也会被清掉。`,
      '删除确认', { type: 'warning' }
    )
  } catch { return }
  const res = await request.delete(`/admin/stocks/${row.stockCode}`)
  if (res.code === 200) {
    ElMessage.success('已删除')
    reload()
    loadStats()
  }
}

const onSyncAll = async () => {
  if (sync.value.running) {
    ElMessage.warning('已有同步任务在运行，请等待完成')
    return
  }
  try {
    await ElMessageBox.confirm(
      '触发全量日 K 同步？该任务在后台异步运行，会重新拉取股票池中所有股票的最近 250 根日 K。',
      '同步确认', { type: 'warning' }
    )
  } catch { return }
  syncAll.value = true
  try {
    // 触发前先抓一下服务端现在的 finishedAt，后面用它判定本次任务是否已跑完
    try {
      const probe = await request.get('/admin/stocks/sync-status')
      if (probe.code === 200) priorFinishedAt = probe.data?.finishedAt || null
    } catch (_) { priorFinishedAt = null }

    const res = await request.post('/admin/stocks/sync')
    if (res.code === 200) {
      ElMessage.success(res.msg || '全量同步已启动')
      startPollSync()
      setTimeout(pollSyncStatus, 400)
    }
  } finally { syncAll.value = false }
}

const onSyncMissing = async () => {
  if (sync.value.running) {
    ElMessage.warning('已有同步任务在运行，请等待完成')
    return
  }
  syncMissing.value = true
  try {
    // 同上：先记下现在的 finishedAt，该是上一次任务完成时间点（或空）
    try {
      const probe = await request.get('/admin/stocks/sync-status')
      if (probe.code === 200) priorFinishedAt = probe.data?.finishedAt || null
    } catch (_) { priorFinishedAt = null }

    const res = await request.post('/admin/stocks/sync-missing')
    if (res.code === 200) {
      ElMessage.success(res.msg || '补齐任务已启动')
      startPollSync()
      setTimeout(pollSyncStatus, 400)
    }
  } finally { syncMissing.value = false }
}

onMounted(() => {
  reload()
  loadStats()
  // 页面初进去拉一次 — 可能有冷启动/定时任务正在跑
  pollSyncStatus()
})
onUnmounted(() => {
  stopPollSync()
  priorFinishedAt = null
})
</script>

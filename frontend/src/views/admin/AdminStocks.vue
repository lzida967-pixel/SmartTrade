<template>
  <div>
    <!-- 顶部 -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
      <div>
        <div class="text-xs text-gray-500 tracking-widest font-mono">股票池 · 管理</div>
        <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
          <el-icon class="text-blue-400"><Coin /></el-icon>股票池管理
        </div>
      </div>
      <div class="flex items-center gap-3 flex-wrap">
        <el-input v-model="search" placeholder="搜索代码/名称" clearable class="!w-56">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="success" plain @click="addDialog = true">
          <el-icon class="mr-1"><Plus /></el-icon>新增股票
        </el-button>
        <el-button type="warning" plain :loading="syncMissing" @click="onSyncMissing">
          <el-icon class="mr-1"><MagicStick /></el-icon>仅同步缺数据股票
        </el-button>
        <el-button type="primary" plain :loading="syncAll" @click="onSyncAll">
          <el-icon class="mr-1"><Refresh /></el-icon>触发全量日 K 同步
        </el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
      <div class="admin-card p-5 flex items-center justify-between">
        <div>
          <p class="text-xs text-gray-500 mb-1">股票总数</p>
          <h3 class="text-3xl font-bold text-gray-100 font-mono">{{ stocks.length }}</h3>
        </div>
        <div class="w-12 h-12 bg-blue-500/10 rounded-xl flex items-center justify-center text-blue-400">
          <el-icon class="text-2xl"><Coin /></el-icon>
        </div>
      </div>
      <div class="admin-card p-5 flex items-center justify-between">
        <div>
          <p class="text-xs text-gray-500 mb-1">已有日 K 数据</p>
          <h3 class="text-3xl font-bold text-green-400 font-mono">{{ withDataCount }}</h3>
        </div>
        <div class="w-12 h-12 bg-green-500/10 rounded-xl flex items-center justify-center text-green-400">
          <el-icon class="text-2xl"><CircleCheck /></el-icon>
        </div>
      </div>
      <div class="admin-card p-5 flex items-center justify-between">
        <div>
          <p class="text-xs text-gray-500 mb-1">缺数据股票</p>
          <h3 class="text-3xl font-bold font-mono"
              :class="missingCount > 0 ? 'text-red-400' : 'text-gray-300'">{{ missingCount }}</h3>
        </div>
        <div class="w-12 h-12 bg-red-500/10 rounded-xl flex items-center justify-center text-red-400">
          <el-icon class="text-2xl"><Warning /></el-icon>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card p-4">
      <el-table :data="filteredStocks" class="admin-table" v-loading="loading"
                element-loading-background="rgba(0,0,0,0.4)" empty-text="股票池为空">
        <el-table-column prop="stockCode" label="代码" width="120">
          <template #default="{ row }">
            <span class="font-mono text-blue-300 font-bold bg-blue-500/10 px-2 py-1 rounded text-xs">
              {{ row.stockCode }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="名称" min-width="140" />
        <el-table-column label="市场" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.market === 'SH' ? 'danger' : 'primary'" size="small" disable-transitions>
              {{ row.market === 'SH' ? '上交所' : '深交所' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="industryName" label="行业" min-width="140">
          <template #default="{ row }">
            <span class="text-gray-400 text-xs">{{ row.industryName || row.plateType || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="日 K 数据" min-width="160" align="center">
          <template #default="{ row }">
            <span v-if="row.dailyPriceCount > 0"
                  class="text-green-400 font-mono">{{ row.dailyPriceCount }} 条</span>
            <span v-else class="text-red-400 font-mono">缺数据</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Coin, Search, Plus, Refresh, MagicStick, CircleCheck, Warning
} from '@element-plus/icons-vue'
import request from '../../utils/request'

const stocks = ref([])
const loading = ref(false)
const search = ref('')

const addDialog = ref(false)
const addForm = ref({ stockCode: '', stockName: '', market: 'SH', industryName: '' })
const adding = ref(false)

const syncAll = ref(false)
const syncMissing = ref(false)

const withDataCount = computed(() => stocks.value.filter(s => s.dailyPriceCount > 0).length)
const missingCount = computed(() => stocks.value.filter(s => !s.dailyPriceCount).length)

const filteredStocks = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return stocks.value
  return stocks.value.filter(s =>
    (s.stockCode || '').toLowerCase().includes(kw) ||
    (s.stockName || '').toLowerCase().includes(kw) ||
    (s.industryName || '').toLowerCase().includes(kw)
  )
})

const reload = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/stocks')
    if (res.code === 200) stocks.value = res.data || []
  } finally { loading.value = false }
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
  }
}

const onSyncAll = async () => {
  try {
    await ElMessageBox.confirm(
      '触发全量日 K 同步？该任务在后台异步运行，会重新拉取股票池中所有股票的最近 250 根日 K。',
      '同步确认', { type: 'warning' }
    )
  } catch { return }
  syncAll.value = true
  try {
    const res = await request.post('/admin/stocks/sync')
    if (res.code === 200) ElMessage.success(res.msg || '全量同步已启动')
  } finally { syncAll.value = false }
}

const onSyncMissing = async () => {
  syncMissing.value = true
  try {
    const res = await request.post('/admin/stocks/sync-missing')
    if (res.code === 200) {
      ElMessage.success(res.msg || '补齐任务已启动')
      // 给后台一点时间，2秒后自动刷新
      setTimeout(reload, 3000)
    }
  } finally { syncMissing.value = false }
}

onMounted(reload)
</script>

<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans">
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">

      <!-- 顶部 -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div class="text-xs text-gray-500 tracking-widest font-mono">交易 · 委托单</div>
          <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
            <el-icon class="text-blue-400"><List /></el-icon>
            委托单记录
          </div>
        </div>
        <div class="flex items-center gap-3 flex-wrap">
          <el-input
            v-model="filters.stockCode"
            placeholder="按股票代码筛选"
            clearable
            size="default"
            class="!w-44"
            @change="reload"
            @clear="reload"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filters.status" placeholder="全部状态" clearable size="default"
                     class="!w-40" @change="reload" @clear="reload">
            <el-option label="待成交" value="PENDING" />
            <el-option label="已成交" value="FILLED" />
            <el-option label="部分成交" value="PARTIAL" />
            <el-option label="已撤销" value="CANCELED" />
          </el-select>
          <el-dropdown trigger="click" @command="onExport" :disabled="loading">
            <el-button plain :loading="exporting">
              <el-icon class="mr-1"><Download /></el-icon>导出 CSV
              <el-icon class="ml-1"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="orders">导出委托单（当前筛选）</el-dropdown-item>
                <el-dropdown-item command="deals">导出成交记录（当前筛选）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" plain :loading="loading" @click="reload">
            <el-icon class="mr-1"><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </div>

      <!-- 列表 -->
      <div class="chart-card overflow-hidden">
        <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center">
          <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
            <DataLine class="w-4 h-4" /> 我的委托单
          </span>
          <span class="text-[10px] text-gray-500 font-mono">
            共 {{ total }} 条 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页
          </span>
        </div>

        <el-table
          :data="orders"
          stripe
          :max-height="640"
          empty-text="暂无委托单记录"
          class="market-table"
          v-loading.body="loading"
          element-loading-background="rgba(0,0,0,0.4)"
        >
          <el-table-column prop="orderTime" label="委托时间" min-width="160">
            <template #default="{ row }">
              <span class="text-xs text-gray-400 font-mono">{{ formatTime(row.orderTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stockCode" label="股票" min-width="120">
            <template #default="{ row }">
              <div class="font-mono text-sm">{{ row.stockCode }}</div>
              <div class="text-[11px] text-gray-500">{{ stockName(row.stockCode) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="方向" width="80">
            <template #default="{ row }">
              <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small" disable-transitions>
                {{ row.direction === 'BUY' ? '买入' : '卖出' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <span class="text-xs text-gray-400">{{ row.orderType === 'MARKET' ? '市价' : '限价' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="委托价" width="100" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ formatNum(row.entrustPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ row.entrustQuantity }}</template>
          </el-table-column>
          <el-table-column label="成交价" width="100" align="right">
            <template #default="{ row }">
              <span class="font-mono" :class="row.matchPrice ? 'text-gray-200' : 'text-gray-600'">
                {{ formatNum(row.matchPrice) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="成交数量" width="90" align="right">
            <template #default="{ row }">
              <span :class="row.matchQuantity ? 'text-gray-200' : 'text-gray-600'">
                {{ row.matchQuantity || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="成交额" width="120" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ formatNum(row.turnoverAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.orderStatus)" size="small" disable-transitions>
                {{ statusLabel(row.orderStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goKline(row)">
                K线
              </el-button>
              <el-button v-if="row.orderStatus === 'PENDING'"
                         link type="warning" size="small" @click="onCancel(row)">
                撤单
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="flex justify-end pt-3">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="reload"
            @size-change="reload"
          />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Search, Refresh, DataLine, Download, ArrowDown } from '@element-plus/icons-vue'
import request from '../utils/request'
import { toCSV, downloadCSV, tsForFilename } from '../utils/csv'

const router = useRouter()
const goKline = (row) => {
  if (!row || !row.stockCode) return
  router.push({ path: '/market', query: { stockCode: row.stockCode } })
}

const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const filters = ref({ stockCode: '', status: '' })
const stockMap = ref({})

const statusLabel = (s) => ({
  PENDING: '待成交', PARTIAL: '部分成交', FILLED: '已成交', CANCELED: '已撤销'
}[s] || s)
const statusType = (s) => ({
  PENDING: 'warning', PARTIAL: 'warning', FILLED: 'success', CANCELED: 'info'
}[s] || 'info')

const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const formatTime = (v) => {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}

const stockName = (code) => stockMap.value[code]?.stockName || ''

const loadStockMap = async () => {
  try {
    const res = await request.get('/stock/list')
    if (res.code === 200) {
      const m = {}
      for (const s of res.data || []) m[s.stockCode] = s
      stockMap.value = m
    }
  } catch (e) { /* ignore */ }
}

const reload = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filters.value.stockCode) params.stockCode = filters.value.stockCode.trim()
    if (filters.value.status) params.status = filters.value.status
    const res = await request.get('/trade/orders', { params })
    if (res.code === 200) {
      orders.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (e) { /* request 已弹错 */ } finally {
    loading.value = false
  }
}

// ============== CSV 导出 ==============
const exporting = ref(false)

/** 拉全量数据：在当前筛选条件下，循环拉直到取完，最多 10000 条 */
const fetchAllOrders = async () => {
  const params = { page: 1, size: 10000 }
  if (filters.value.stockCode) params.stockCode = filters.value.stockCode.trim()
  if (filters.value.status) params.status = filters.value.status
  const res = await request.get('/trade/orders', { params })
  if (res.code !== 200) return []
  return res.data?.records || []
}

const fetchAllDeals = async () => {
  const params = { page: 1, size: 10000 }
  if (filters.value.stockCode) params.stockCode = filters.value.stockCode.trim()
  const res = await request.get('/trade/deals', { params })
  if (res.code !== 200) return []
  return res.data?.records || []
}

const onExport = async (cmd) => {
  if (cmd === 'orders') return exportOrders()
  if (cmd === 'deals') return exportDeals()
}

const exportOrders = async () => {
  exporting.value = true
  try {
    const list = await fetchAllOrders()
    if (!list.length) {
      ElMessage.warning('当前筛选下无委托单')
      return
    }
    const cols = [
      { key: 'orderNo',         label: '委托单号' },
      { key: 'orderTime',       label: '委托时间', format: (r) => formatTime(r.orderTime) },
      { key: 'stockCode',       label: '股票代码' },
      { key: 'stockName',       label: '股票名称', format: (r) => stockName(r.stockCode) },
      { key: 'direction',       label: '方向',     format: (r) => r.direction === 'BUY' ? '买入' : '卖出' },
      { key: 'orderType',       label: '订单类型', format: (r) => r.orderType === 'MARKET' ? '市价' : '限价' },
      { key: 'entrustPrice',    label: '委托价' },
      { key: 'entrustQuantity', label: '委托数量' },
      { key: 'matchPrice',      label: '成交价' },
      { key: 'matchQuantity',   label: '成交数量' },
      { key: 'turnoverAmount',  label: '成交金额' },
      { key: 'orderStatus',     label: '状态',     format: (r) => statusLabel(r.orderStatus) },
      { key: 'cancelTime',      label: '撤单时间', format: (r) => formatTime(r.cancelTime) }
    ]
    const csv = toCSV(cols, list)
    const summary = [
      `委托单导出  ${new Date().toLocaleString('zh-CN')}`,
      `筛选条件: 股票=${filters.value.stockCode || '全部'}, 状态=${filters.value.status ? statusLabel(filters.value.status) : '全部'}`,
      `共 ${list.length} 条`
    ]
    downloadCSV(`委托单_${tsForFilename()}.csv`, csv, summary)
    ElMessage.success(`已导出 ${list.length} 条委托单`)
  } catch (e) {
    console.error(e)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const exportDeals = async () => {
  exporting.value = true
  try {
    const list = await fetchAllDeals()
    if (!list.length) {
      ElMessage.warning('当前筛选下无成交记录')
      return
    }
    const cols = [
      { key: 'orderNo',     label: '关联委托单号' },
      { key: 'dealTime',    label: '成交时间', format: (r) => formatTime(r.dealTime) },
      { key: 'stockCode',   label: '股票代码' },
      { key: 'stockName',   label: '股票名称', format: (r) => stockName(r.stockCode) },
      { key: 'dealPrice',   label: '成交价' },
      { key: 'dealQuantity',label: '成交数量' },
      { key: 'dealAmount',  label: '成交金额' }
    ]
    const csv = toCSV(cols, list)
    const summary = [
      `成交记录导出  ${new Date().toLocaleString('zh-CN')}`,
      `筛选条件: 股票=${filters.value.stockCode || '全部'}`,
      `共 ${list.length} 条`
    ]
    downloadCSV(`成交记录_${tsForFilename()}.csv`, csv, summary)
    ElMessage.success(`已导出 ${list.length} 条成交记录`)
  } catch (e) {
    console.error(e)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const onCancel = async (row) => {
  try {
    await ElMessageBox.confirm(
      `撤销委托单 ${row.orderNo}？将释放冻结的资金或持仓`,
      '撤单确认',
      { type: 'warning', confirmButtonText: '确认撤单', cancelButtonText: '再想想' }
    )
  } catch { return }
  try {
    const res = await request.post(`/trade/order/${row.orderNo}/cancel`)
    if (res.code === 200) {
      ElMessage.success('撤单成功')
      reload()
    }
  } catch { /* ignore */ }
}

onMounted(async () => {
  await loadStockMap()
  await reload()
})
</script>

<style scoped>
.chart-card {
  position: relative;
  background-color: rgba(12, 12, 14, 0.8);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 1rem;
  padding: 1.25rem;
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
:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: transparent;
  --el-pagination-button-color: #d1d5db;
  --el-pagination-hover-color: #60a5fa;
}
</style>

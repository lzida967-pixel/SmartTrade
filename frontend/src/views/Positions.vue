<template>
  <div class="min-h-screen bg-[#030305] text-gray-200 font-sans">
    <main class="p-6 max-w-[1600px] mx-auto space-y-6">

      <!-- 顶部 -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div class="text-xs text-gray-500 tracking-widest font-mono">资产 · 持仓</div>
          <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
            <el-icon class="text-blue-400"><Wallet /></el-icon>
            我的持仓
          </div>
        </div>
        <el-button type="primary" plain :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <!-- 资产汇总卡片 -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div class="stat-card">
          <div class="stat-label">账户总资产</div>
          <div class="stat-val text-orange-300">¥ {{ formatNum(totalAssets) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">可用资金</div>
          <div class="stat-val">¥ {{ formatNum(asset.availableFunds) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">冻结资金</div>
          <div class="stat-val text-yellow-300">¥ {{ formatNum(asset.frozenFunds) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">持仓市值 / 浮盈亏</div>
          <div class="stat-val">
            ¥ {{ formatNum(asset.marketValue) }}
            <span class="text-xs ml-2" :class="profitClass(asset.floatingProfit)">
              {{ asset.floatingProfit >= 0 ? '+' : '' }}{{ formatNum(asset.floatingProfit) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 持仓列表 -->
      <div class="chart-card overflow-hidden">
        <div class="border-b border-white/5 pb-3 mb-4 flex justify-between items-center">
          <span class="text-xs font-semibold tracking-widest text-cyan-400 flex items-center gap-2">
            <DataLine class="w-4 h-4" /> 持仓明细
          </span>
          <span class="text-[10px] text-gray-500 font-mono">共 {{ positions.length }} 只</span>
        </div>

        <el-table
          :data="positions"
          stripe
          empty-text="暂无持仓 — 可前往「行情中心」买入第一只股票"
          class="market-table"
          v-loading.body="loading"
          element-loading-background="rgba(0,0,0,0.4)"
        >
          <el-table-column prop="stockCode" label="代码" width="100">
            <template #default="{ row }">
              <span class="font-mono">{{ row.stockCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="名称" width="140">
            <template #default="{ row }">
              <span>{{ stockName(row.stockCode) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="持仓 / 可卖" width="130" align="right">
            <template #default="{ row }">
              <div class="font-mono">{{ row.quantity }}</div>
              <div class="text-[11px] text-gray-500 font-mono">可卖 {{ row.availableQuantity }}</div>
            </template>
          </el-table-column>
          <el-table-column label="成本价" width="100" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ formatNum(row.costPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最新价" width="100" align="right">
            <template #default="{ row }">
              <span class="font-mono">{{ formatNum(row.latestPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="市值" width="130" align="right">
            <template #default="{ row }">
              <span class="font-mono">¥ {{ formatNum(row.marketValue) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="浮动盈亏" min-width="160" align="right">
            <template #default="{ row }">
              <div class="font-mono font-semibold" :class="profitClass(row.floatingProfit)">
                {{ row.floatingProfit >= 0 ? '+' : '' }}¥ {{ formatNum(row.floatingProfit) }}
              </div>
              <div class="text-[11px] font-mono" :class="profitClass(row.floatingProfit)">
                {{ formatProfitRate(row) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain @click="openBuy(row)">买入</el-button>
              <el-button size="small" type="success" plain
                         :disabled="!row.availableQuantity"
                         @click="openSell(row)">卖出</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </main>

    <!-- 下单弹窗 -->
    <OrderDialog
      v-model="dialogVisible"
      :stock-code="dialogStock.stockCode"
      :stock-name="dialogStock.stockName"
      :latest-price="dialogStock.latestPrice"
      :change-percent="dialogStock.changePercent"
      :default-direction="dialogDirection"
      :available-funds="asset.availableFunds || 0"
      :available-qty="dialogStock.availableQty || 0"
      @placed="onPlaced"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Wallet, Refresh, DataLine } from '@element-plus/icons-vue'
import request from '../utils/request'
import OrderDialog from '../components/OrderDialog.vue'

const positions = ref([])
const asset = ref({
  availableFunds: 0, frozenFunds: 0, marketValue: 0, floatingProfit: 0
})
const stockMap = ref({})
const loading = ref(false)

const dialogVisible = ref(false)
const dialogStock = ref({ stockCode: '', stockName: '', latestPrice: 0, changePercent: 0, availableQty: 0 })
const dialogDirection = ref('SELL')

const totalAssets = computed(() => {
  return Number(asset.value.availableFunds || 0)
       + Number(asset.value.frozenFunds || 0)
       + Number(asset.value.marketValue || 0)
})

const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const profitClass = (v) => {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'text-gray-300'
  return n > 0 ? 'text-red-400' : 'text-green-400'
}
const formatProfitRate = (row) => {
  const cost = Number(row.costPrice) || 0
  const latest = Number(row.latestPrice) || 0
  if (cost <= 0) return '--'
  const rate = (latest - cost) / cost * 100
  return (rate >= 0 ? '+' : '') + rate.toFixed(2) + '%'
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

const loadAsset = async () => {
  try {
    const res = await request.get('/user/asset')
    if (res.code === 200) {
      asset.value = {
        availableFunds: Number(res.data.availableFunds || 0),
        frozenFunds: Number(res.data.frozenFunds || 0),
        marketValue: Number(res.data.marketValue || 0),
        floatingProfit: Number(res.data.floatingProfit || 0)
      }
    }
  } catch (e) { /* ignore */ }
}

const loadPositions = async () => {
  try {
    const res = await request.get('/trade/positions')
    if (res.code === 200) {
      positions.value = res.data || []
    }
  } catch (e) { /* ignore */ }
}

const reload = async () => {
  loading.value = true
  try {
    await Promise.all([loadStockMap(), loadAsset(), loadPositions()])
  } finally {
    loading.value = false
  }
}

const openSell = (row) => {
  dialogStock.value = {
    stockCode: row.stockCode,
    stockName: stockName(row.stockCode),
    latestPrice: Number(row.latestPrice) || 0,
    changePercent: 0,
    availableQty: row.availableQuantity || 0
  }
  dialogDirection.value = 'SELL'
  dialogVisible.value = true
}

const openBuy = (row) => {
  dialogStock.value = {
    stockCode: row.stockCode,
    stockName: stockName(row.stockCode),
    latestPrice: Number(row.latestPrice) || 0,
    changePercent: 0,
    availableQty: row.availableQuantity || 0
  }
  dialogDirection.value = 'BUY'
  dialogVisible.value = true
}

const onPlaced = () => {
  ElMessage.success('已提交下单，刷新数据中...')
  reload()
}

onMounted(reload)
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
.stat-card {
  background: rgba(12, 12, 14, 0.8);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 0.75rem;
  padding: 1rem 1.25rem;
}
.stat-label {
  font-size: 11px;
  color: #6b7280;
  letter-spacing: 1.5px;
  margin-bottom: 6px;
}
.stat-val {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 18px;
  color: #e5e7eb;
  font-weight: 700;
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
</style>

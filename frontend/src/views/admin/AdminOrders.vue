<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><List /></el-icon></span>订单巡检</h2>
        <div class="pt-sub">全用户委托流水，支持多维筛选与强制撤单</div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(99,102,241,.15); color:#a5b4fc"><el-icon><List /></el-icon></div>
        <div>
          <div class="stat-label">全部订单</div>
          <div class="stat-value">{{ total }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(245,158,11,.12); color:#fbbf24"><el-icon><Clock /></el-icon></div>
        <div>
          <div class="stat-label">待成交</div>
          <div class="stat-value" style="color:#fbbf24">{{ stats.pending }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(16,185,129,.12); color:#34d399"><el-icon><CircleCheck /></el-icon></div>
        <div>
          <div class="stat-label">已成交</div>
          <div class="stat-value" style="color:#34d399">{{ stats.filled }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(156,163,175,.1); color:#9ca3af"><el-icon><CircleClose /></el-icon></div>
        <div>
          <div class="stat-label">已撤销</div>
          <div class="stat-value" style="color:#9ca3af">{{ stats.canceled }}</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="admin-toolbar">
      <div class="flex items-center gap-2 text-xs text-gray-500">
        <el-icon><Filter /></el-icon><span>筛选条件</span>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-input v-model="filters.userId" placeholder="用户 ID" clearable size="default"
                  class="!w-32" @keyup.enter="reload" @clear="reload" />
        <el-input v-model="filters.stockCode" placeholder="股票代码" clearable size="default"
                  class="!w-36" @keyup.enter="reload" @clear="reload" />
        <el-select v-model="filters.status" placeholder="状态" clearable class="!w-32" @change="reload">
          <el-option label="全部" value="" />
          <el-option label="待成交" value="PENDING" />
          <el-option label="已成交" value="FILLED" />
          <el-option label="已撤销" value="CANCELED" />
        </el-select>
        <el-select v-model="filters.direction" placeholder="方向" clearable class="!w-28" @change="reload">
          <el-option label="全部" value="" />
          <el-option label="买入" value="BUY" />
          <el-option label="卖出" value="SELL" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card p-4">
      <el-table :data="orders" class="admin-table" v-loading="loading"
                element-loading-background="rgba(0,0,0,0.4)" empty-text="无订单数据">
        <el-table-column prop="orderTime" label="委托时间" min-width="160">
          <template #default="{ row }">
            <span class="text-xs text-gray-400 font-mono">{{ formatTime(row.orderTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <div class="text-sm">{{ row.username }}</div>
            <div class="text-[11px] text-gray-500 font-mono">UID {{ row.userId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="股票" width="120">
          <template #default="{ row }">
            <span class="font-mono text-sm">{{ row.stockCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <span class="dot-tag" :class="row.direction === 'BUY' ? 'danger' : 'success'">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <span class="text-xs">{{ row.orderType === 'MARKET' ? '市价' : '限价' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="委托价 / 数量" min-width="160" align="right">
          <template #default="{ row }">
            <div class="font-mono text-sm">{{ formatNum(row.entrustPrice) }}</div>
            <div class="text-[11px] text-gray-500 font-mono">{{ row.entrustQuantity }} 股</div>
          </template>
        </el-table-column>
        <el-table-column label="成交数量" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ row.matchQuantity || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成交额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="font-mono">¥ {{ formatNum(row.turnoverAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="statusType(row.orderStatus)">
              {{ statusLabel(row.orderStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.orderStatus === 'PENDING'"
                       link type="warning" size="small" @click="onForceCancel(row)">
              强制撤单
            </el-button>
            <span v-else class="text-gray-600 text-xs">--</span>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Refresh, Filter, Clock, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import request from '../../utils/request'

const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const filters = ref({ userId: '', stockCode: '', status: '', direction: '' })

const statusLabel = (s) => ({
  PENDING: '待成交', PARTIAL: '部分成交', FILLED: '已成交', CANCELED: '已撤销'
})[s] || s
// dot-tag 颜色
const statusType = (s) => ({
  PENDING: 'warn', PARTIAL: 'primary', FILLED: 'success', CANCELED: 'muted'
})[s] || 'info'

const stats = computed(() => {
  const buckets = { pending: 0, filled: 0, canceled: 0 }
  for (const o of orders.value) {
    if (o.orderStatus === 'PENDING' || o.orderStatus === 'PARTIAL') buckets.pending++
    else if (o.orderStatus === 'FILLED') buckets.filled++
    else if (o.orderStatus === 'CANCELED') buckets.canceled++
  }
  return buckets
})

const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const formatTime = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '--'

const reload = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value, size: size.value,
      userId: filters.value.userId || undefined,
      stockCode: filters.value.stockCode || undefined,
      status: filters.value.status || undefined,
      direction: filters.value.direction || undefined
    }
    const res = await request.get('/admin/orders', { params })
    if (res.code === 200) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

const onForceCancel = async (row) => {
  try {
    await ElMessageBox.confirm(
      `强制撤销订单 ${row.orderNo}？\n用户 ${row.username} 的冻结资金/持仓将被释放。`,
      '强制撤单', { type: 'warning' }
    )
  } catch { return }
  const res = await request.post(`/admin/orders/${row.orderNo}/force-cancel`)
  if (res.code === 200) {
    ElMessage.success('已强制撤单')
    reload()
  }
}

onMounted(reload)
</script>

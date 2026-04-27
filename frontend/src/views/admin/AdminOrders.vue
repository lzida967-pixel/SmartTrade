<template>
  <div>
    <!-- 顶部 -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
      <div>
        <div class="text-xs text-gray-500 tracking-widest font-mono">订单 · 巡检</div>
        <div class="text-2xl font-bold text-gray-100 mt-1 flex items-center gap-2">
          <el-icon class="text-blue-400"><List /></el-icon>订单巡检
        </div>
      </div>
      <div class="flex items-center gap-3 flex-wrap">
        <el-input v-model="filters.userId" placeholder="用户 ID" clearable
                  class="!w-32" @keyup.enter="reload" @clear="reload" />
        <el-input v-model="filters.stockCode" placeholder="股票代码" clearable
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
        <el-button type="primary" plain :loading="loading" @click="reload">
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
            <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small" disable-transitions>
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
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
            <el-tag :type="statusType(row.orderStatus)" size="small" disable-transitions>
              {{ statusLabel(row.orderStatus) }}
            </el-tag>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Refresh } from '@element-plus/icons-vue'
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
const statusType = (s) => ({
  PENDING: 'warning', PARTIAL: 'primary', FILLED: 'success', CANCELED: 'info'
})[s] || 'info'

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

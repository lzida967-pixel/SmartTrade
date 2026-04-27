<template>
  <el-dialog
    v-model="visible"
    width="520px"
    :show-close="true"
    :close-on-click-modal="false"
    :destroy-on-close="true"
    append-to-body
    class="order-dialog"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <el-icon :class="isBuy ? 'text-red-400' : 'text-green-400'">
          <component :is="isBuy ? Top : Bottom" />
        </el-icon>
        <span class="text-base font-bold text-gray-100">
          {{ isBuy ? '买入下单' : '卖出下单' }}
        </span>
        <span class="text-gray-500 text-xs">
          {{ stockName }} <span class="font-mono">{{ stockCode }}</span>
        </span>
      </div>
    </template>

    <div class="space-y-4">
      <!-- 当前价 / 市场可用 -->
      <div class="grid grid-cols-2 gap-3">
        <div class="info-cell">
          <div class="info-label">最新价</div>
          <div class="info-val" :class="priceClass">{{ formatNum(latestPrice) }}</div>
        </div>
        <div class="info-cell">
          <div class="info-label">{{ isBuy ? '可用资金' : '可卖股数' }}</div>
          <div class="info-val">
            {{ isBuy ? formatNum(availableFunds) : `${availableQty} 股` }}
          </div>
        </div>
      </div>

      <!-- 买/卖切换 -->
      <el-radio-group v-model="form.direction" size="default" class="!w-full">
        <el-radio-button value="BUY" class="!w-1/2">买入</el-radio-button>
        <el-radio-button value="SELL" class="!w-1/2">卖出</el-radio-button>
      </el-radio-group>

      <!-- 订单类型 -->
      <div>
        <div class="text-xs text-gray-500 mb-1">订单类型</div>
        <el-radio-group v-model="form.orderType" size="small">
          <el-radio-button value="MARKET">市价单</el-radio-button>
          <el-radio-button value="LIMIT">限价单</el-radio-button>
        </el-radio-group>
        <div class="text-[11px] text-gray-600 mt-1">
          {{ form.orderType === 'MARKET' ? '按当前最新价立即成交' : '指定价格挂单，行情触达时成交' }}
        </div>
      </div>

      <!-- 委托价（限价单才出现） -->
      <div v-if="form.orderType === 'LIMIT'">
        <div class="text-xs text-gray-500 mb-1">委托价</div>
        <el-input-number
          v-model="form.price"
          :min="0.01"
          :precision="2"
          :step="0.01"
          controls-position="right"
          class="!w-full"
        />
      </div>

      <!-- 委托数量 -->
      <div>
        <div class="flex items-center justify-between mb-1">
          <span class="text-xs text-gray-500">委托数量（必须 100 整数倍）</span>
          <div class="flex gap-1">
            <el-button v-for="r in quickRatios" :key="r" link size="small"
              @click="setQuantityByRatio(r)" class="!text-xs !text-cyan-400">
              {{ r === 1 ? '全部' : `${r * 100}%` }}
            </el-button>
          </div>
        </div>
        <el-input-number
          v-model="form.quantity"
          :min="100"
          :step="100"
          :precision="0"
          controls-position="right"
          class="!w-full"
        />
      </div>

      <!-- 估算金额 -->
      <div class="border-t border-white/5 pt-3 flex items-center justify-between">
        <span class="text-xs text-gray-500">预计{{ isBuy ? '占用资金' : '到账金额' }}</span>
        <span class="font-mono text-lg font-bold" :class="isBuy ? 'text-red-400' : 'text-green-400'">
          ¥ {{ formatNum(estimatedAmount) }}
        </span>
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded p-2">
        {{ errorMsg }}
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        :type="isBuy ? 'danger' : 'success'"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        确认{{ isBuy ? '买入' : '卖出' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Top, Bottom } from '@element-plus/icons-vue'
import request from '../utils/request'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stockCode: { type: String, default: '' },
  stockName: { type: String, default: '' },
  latestPrice: { type: [Number, String], default: 0 },
  changePercent: { type: [Number, String], default: 0 },
  defaultDirection: { type: String, default: 'BUY' },
  availableFunds: { type: [Number, String], default: 0 },
  availableQty: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue', 'placed'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const form = ref({
  direction: 'BUY',
  orderType: 'MARKET',
  price: 0,
  quantity: 100
})
const submitting = ref(false)
const errorMsg = ref('')

const isBuy = computed(() => form.value.direction === 'BUY')

const priceClass = computed(() => {
  const v = Number(props.changePercent)
  if (Number.isNaN(v) || v === 0) return 'text-gray-300'
  return v > 0 ? 'text-red-400' : 'text-green-400'
})

const effectivePrice = computed(() => {
  if (form.value.orderType === 'LIMIT') return Number(form.value.price) || 0
  return Number(props.latestPrice) || 0
})

const estimatedAmount = computed(() => {
  return effectivePrice.value * (form.value.quantity || 0)
})

const quickRatios = computed(() => isBuy.value ? [0.25, 0.5, 1] : [0.25, 0.5, 1])

const canSubmit = computed(() => {
  if (!form.value.quantity || form.value.quantity < 100) return false
  if (form.value.quantity % 100 !== 0) return false
  if (form.value.orderType === 'LIMIT' && (!form.value.price || form.value.price <= 0)) return false
  return true
})

watch(() => props.modelValue, (v) => {
  if (v) {
    // 弹窗打开时重置表单
    form.value = {
      direction: props.defaultDirection || 'BUY',
      orderType: 'MARKET',
      price: Number(props.latestPrice) || 0,
      quantity: 100
    }
    errorMsg.value = ''
  }
})

watch(() => form.value.orderType, (v) => {
  if (v === 'LIMIT' && (!form.value.price || form.value.price <= 0)) {
    form.value.price = Number(props.latestPrice) || 0
  }
})

const setQuantityByRatio = (ratio) => {
  let target = 0
  if (isBuy.value) {
    if (effectivePrice.value <= 0) return
    const cash = Number(props.availableFunds) || 0
    target = Math.floor((cash * ratio) / effectivePrice.value / 100) * 100
  } else {
    target = Math.floor((props.availableQty || 0) * ratio / 100) * 100
  }
  form.value.quantity = Math.max(100, target)
}

const formatNum = (v) => {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const onSubmit = async () => {
  errorMsg.value = ''
  // 校验
  if (isBuy.value) {
    const need = estimatedAmount.value
    const avail = Number(props.availableFunds) || 0
    if (need > avail) {
      errorMsg.value = `可用资金不足，预计需要 ¥${formatNum(need)}，可用 ¥${formatNum(avail)}`
      return
    }
  } else {
    if (form.value.quantity > (props.availableQty || 0)) {
      errorMsg.value = `可卖持仓不足，当前可卖 ${props.availableQty} 股`
      return
    }
  }

  try {
    await ElMessageBox.confirm(
      `${isBuy.value ? '买入' : '卖出'} ${props.stockName} ${props.stockCode}，` +
      `${form.value.orderType === 'MARKET' ? '市价' : `限价 ¥${form.value.price}`}，` +
      `数量 ${form.value.quantity} 股，` +
      `预计${isBuy.value ? '占用' : '到账'} ¥${formatNum(estimatedAmount.value)}`,
      '确认下单',
      {
        confirmButtonText: '确认下单',
        cancelButtonText: '再想想',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      stockCode: props.stockCode,
      direction: form.value.direction,
      orderType: form.value.orderType,
      quantity: form.value.quantity
    }
    if (form.value.orderType === 'LIMIT') {
      payload.price = form.value.price
    }
    const res = await request.post('/trade/order', payload)
    if (res.code === 200) {
      ElMessage.success(res.msg || '下单成功')
      visible.value = false
      emit('placed', res.data)
    }
  } catch (e) {
    // request 拦截器已经弹了 ElMessage.error，这里仅显示在弹窗内
    errorMsg.value = e?.message || '下单失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.info-cell {
  background: rgba(11, 14, 22, 0.65);
  border: 1px solid rgba(96, 165, 250, 0.12);
  border-radius: 0.5rem;
  padding: 0.6rem 0.8rem;
}
.info-label {
  font-size: 11px;
  color: #6b7280;
  letter-spacing: 1px;
  margin-bottom: 2px;
}
.info-val {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 16px;
  color: #e5e7eb;
  font-weight: 700;
}

:deep(.order-dialog) {
  --el-dialog-bg-color: #161a26;
  background: linear-gradient(160deg, #1b2030 0%, #161a26 60%, #11141d 100%) !important;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 0.85rem;
  box-shadow:
    0 0 0 1px rgba(96, 165, 250, 0.08),
    0 30px 60px -20px rgba(0, 0, 0, 0.8),
    0 0 80px -10px rgba(59, 130, 246, 0.25);
  overflow: hidden;
}
:deep(.order-dialog .el-dialog__header) {
  border-bottom: 1px solid rgba(96, 165, 250, 0.15);
  background: linear-gradient(90deg, rgba(59,130,246,0.08), transparent);
  margin-right: 0;
  padding: 14px 18px;
}
:deep(.order-dialog .el-dialog__body) {
  padding: 18px;
  color: #d1d5db;
  background: transparent;
}
:deep(.order-dialog .el-dialog__footer) {
  border-top: 1px solid rgba(255,255,255,0.05);
  padding: 12px 18px;
}
</style>

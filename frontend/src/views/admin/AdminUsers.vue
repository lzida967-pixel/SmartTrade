<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2><span class="pt-icon"><el-icon><User /></el-icon></span>用户管理</h2>
        <div class="pt-sub">账号权限、资金、状态一站式调度</div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(99,102,241,.15); color:#a5b4fc"><el-icon><User /></el-icon></div>
        <div>
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ total }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(245,158,11,.12); color:#fbbf24"><el-icon><Setting /></el-icon></div>
        <div>
          <div class="stat-label">管理员</div>
          <div class="stat-value" style="color:#fbbf24">{{ adminCount }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(16,185,129,.12); color:#34d399"><el-icon><CircleCheck /></el-icon></div>
        <div>
          <div class="stat-label">启用中</div>
          <div class="stat-value" style="color:#34d399">{{ activeCount }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:rgba(59,130,246,.12); color:#60a5fa"><el-icon><Wallet /></el-icon></div>
        <div>
          <div class="stat-label">本页可用资金</div>
          <div class="stat-value" style="color:#60a5fa">¥{{ formatNum(fundsSum) }}</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="admin-toolbar">
      <div class="flex items-center gap-2 text-xs text-gray-500">
        <el-icon><Search /></el-icon><span>查找用户</span>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-input v-model="filters.keyword" placeholder="账号/昵称" clearable
                  class="!w-56" @keyup.enter="reload" @clear="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.role" placeholder="角色" clearable class="!w-32" @change="reload">
          <el-option label="全部" value="" />
          <el-option label="USER" value="USER" />
          <el-option label="ADMIN" value="ADMIN" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable class="!w-32" @change="reload">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card p-4">
      <el-table :data="users" class="admin-table" v-loading="loading"
                element-loading-background="rgba(0,0,0,0.4)" empty-text="暂无用户">
        <el-table-column prop="id" label="ID" width="70">
          <template #default="{ row }">
            <span class="text-xs text-gray-500 font-mono">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold shrink-0">
                {{ (row.nickname || row.username || 'U').charAt(0).toUpperCase() }}
              </div>
              <div class="min-w-0">
                <div class="text-sm text-gray-100 truncate">{{ row.nickname || row.username }}</div>
                <div class="text-[11px] text-gray-500 font-mono truncate">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="row.role === 'ADMIN' ? 'warn' : 'info'">
              {{ row.role || 'USER' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="dot-tag" :class="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="可用资金" min-width="140" align="right">
          <template #default="{ row }">
            <span class="font-mono">¥ {{ formatNum(row.availableFunds) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冻结资金" min-width="120" align="right">
          <template #default="{ row }">
            <span class="font-mono text-gray-500">¥ {{ formatNum(row.frozenFunds) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="160">
          <template #default="{ row }">
            <span class="text-xs text-gray-400 font-mono">{{ formatTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openFunds(row)">改资金</el-button>
            <el-button link type="warning" size="small" @click="toggleRole(row)">
              {{ row.role === 'ADMIN' ? '降为普通' : '设为管理员' }}
            </el-button>
            <el-button link size="small"
                       :type="row.status === 1 ? 'danger' : 'success'"
                       @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="info" size="small" @click="resetPwd(row)">重置密码</el-button>
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

    <!-- 改资金弹窗 -->
    <el-dialog v-model="fundsDialog" title="调整用户资金" width="480px" destroy-on-close>
      <div v-if="editing" class="space-y-4">
        <div class="text-xs text-gray-500">
          用户：<span class="font-mono">{{ editing.username }}</span>
          <span v-if="editing.nickname"> · {{ editing.nickname }}</span>
        </div>
        <el-radio-group v-model="fundsForm.action">
          <el-radio value="SET">直接设置</el-radio>
          <el-radio value="ADD">在原值上加减</el-radio>
        </el-radio-group>
        <el-form label-width="100px" label-position="left">
          <el-form-item label="可用资金">
            <el-input-number v-model="fundsForm.available" :precision="2" :step="1000" class="!w-full" />
            <div class="text-[11px] text-gray-500 mt-1">
              当前 ¥ {{ formatNum(editing.availableFunds) }}
            </div>
          </el-form-item>
          <el-form-item label="冻结资金">
            <el-input-number v-model="fundsForm.frozen" :precision="2" :step="100" class="!w-full" />
            <div class="text-[11px] text-gray-500 mt-1">
              当前 ¥ {{ formatNum(editing.frozenFunds) }}
              <span class="text-yellow-500">（一般无需手动改）</span>
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="fundsDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveFunds">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Search, Refresh, Setting, CircleCheck, Wallet } from '@element-plus/icons-vue'
import request from '../../utils/request'

const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const filters = ref({ keyword: '', role: '', status: '' })

const fundsDialog = ref(false)
const editing = ref(null)
const fundsForm = ref({ action: 'SET', available: 0, frozen: 0 })
const saving = ref(false)

const adminCount = computed(() => users.value.filter(u => u.role === 'ADMIN').length)
const activeCount = computed(() => users.value.filter(u => u.status === 1).length)
const fundsSum = computed(() =>
  users.value.reduce((acc, u) => acc + Number(u.availableFunds || 0), 0)
)

const formatNum = (v) => {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const formatTime = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '--'

const reload = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value, size: size.value,
      keyword: filters.value.keyword || undefined,
      role: filters.value.role || undefined,
      status: filters.value.status === '' ? undefined : filters.value.status
    }
    const res = await request.get('/admin/users', { params })
    if (res.code === 200) {
      users.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

const openFunds = (row) => {
  editing.value = row
  fundsForm.value = {
    action: 'SET',
    available: Number(row.availableFunds || 0),
    frozen: Number(row.frozenFunds || 0)
  }
  fundsDialog.value = true
}

const saveFunds = async () => {
  if (!editing.value) return
  saving.value = true
  try {
    const res = await request.put(`/admin/users/${editing.value.id}/funds`, {
      action: fundsForm.value.action,
      available: fundsForm.value.available,
      frozen: fundsForm.value.frozen
    })
    if (res.code === 200) {
      ElMessage.success('资金已更新')
      fundsDialog.value = false
      reload()
    }
  } finally {
    saving.value = false
  }
}

const toggleRole = async (row) => {
  const next = row.role === 'ADMIN' ? 'USER' : 'ADMIN'
  try {
    await ElMessageBox.confirm(
      `确认将 ${row.username} 的角色改为 ${next}？`,
      '角色变更', { type: 'warning' }
    )
  } catch { return }
  const res = await request.put(`/admin/users/${row.id}/role`, { role: next })
  if (res.code === 200) {
    ElMessage.success('角色已更新')
    reload()
  }
}

const toggleStatus = async (row) => {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${next === 1 ? '启用' : '禁用'} ${row.username}？` + (next === 0 ? '禁用后该账号无法登录。' : ''),
      '状态变更', { type: 'warning' }
    )
  } catch { return }
  const res = await request.put(`/admin/users/${row.id}/status`, { status: next })
  if (res.code === 200) {
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    reload()
  }
}

const resetPwd = async (row) => {
  try {
    await ElMessageBox.confirm(
      `重置 ${row.username} 的密码为 123456？`,
      '重置密码', { type: 'warning' }
    )
  } catch { return }
  const res = await request.put(`/admin/users/${row.id}/reset-password`)
  if (res.code === 200) {
    ElMessage.success('已重置为 123456')
  }
}

onMounted(reload)
</script>

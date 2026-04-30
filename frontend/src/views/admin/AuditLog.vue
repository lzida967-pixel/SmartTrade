<template>
  <div>
    <!-- 页面标题 -->
    <div class="admin-page-title">
      <div>
        <h2>
          <span class="pt-icon"><el-icon><Document /></el-icon></span>
          操作审计日志
        </h2>
        <div class="pt-sub">记录所有用户/管理员关键操作 · 仅查询不可删除（合规可追溯）</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="admin-toolbar">
      <div class="flex items-center gap-2 text-xs text-gray-500">
        <el-icon><Filter /></el-icon><span>筛选条件</span>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <el-input v-model="filters.username" placeholder="用户名" clearable size="default"
                  class="!w-36" @keyup.enter="reload" @clear="reload" />
        <el-select v-model="filters.category" placeholder="分类" clearable class="!w-44" @change="reload">
          <el-option v-for="o in dict.categories" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.action" placeholder="动作" clearable class="!w-44" @change="reload">
          <el-option v-for="o in dict.actions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.result" placeholder="结果" clearable class="!w-28" @change="reload">
          <el-option v-for="o in dict.results" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="filters.targetId" placeholder="目标ID" clearable size="default"
                  class="!w-36" @keyup.enter="reload" @clear="reload" />
        <el-date-picker
          v-model="filters.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          @change="reload"
          class="!w-[380px]"
        />
        <el-button type="primary" plain :loading="loading" @click="reload">
          <el-icon class="mr-1"><Refresh /></el-icon>查询
        </el-button>
        <el-button plain @click="resetFilters">
          <el-icon class="mr-1"><Close /></el-icon>重置
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="admin-card">
      <el-table
        :data="rows"
        stripe
        v-loading="loading"
        element-loading-background="rgba(0,0,0,0.4)"
        empty-text="暂无审计日志"
        class="audit-table"
        row-key="id"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-block">
              <div v-if="row.detailsJson" class="mb-3">
                <div class="expand-label">关键参数</div>
                <pre class="expand-pre">{{ formatJson(row.detailsJson) }}</pre>
              </div>
              <div v-if="row.errorMsg" class="mb-3">
                <div class="expand-label text-red-400">错误信息</div>
                <pre class="expand-pre text-red-300">{{ row.errorMsg }}</pre>
              </div>
              <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-[12px] text-gray-400">
                <div><span class="meta-key">UA：</span><span class="meta-val">{{ row.userAgent || '--' }}</span></div>
                <div><span class="meta-key">IP：</span><span class="meta-val font-mono">{{ row.ip || '--' }}</span></div>
                <div><span class="meta-key">耗时：</span><span class="meta-val font-mono">{{ row.costMs }} ms</span></div>
                <div><span class="meta-key">日志ID：</span><span class="meta-val font-mono">{{ row.id }}</span></div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            <span class="font-mono text-xs">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作者" min-width="160">
          <template #default="{ row }">
            <div class="font-medium">{{ row.username || '<匿名>' }}</div>
            <div class="text-[11px] text-gray-500 font-mono">
              ID:{{ row.userId || '--' }} · {{ row.role || '--' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="categoryTagType(row.category)" disable-transitions>
              {{ categoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="150">
          <template #default="{ row }">
            <span class="font-mono text-xs">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="160">
          <template #default="{ row }">
            <div v-if="row.targetType || row.targetId">
              <div class="text-[11px] text-gray-500">{{ row.targetType }}</div>
              <div class="font-mono text-xs">{{ row.targetId || '--' }}</div>
            </div>
            <span v-else class="text-gray-600">--</span>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="280">
          <template #default="{ row }">
            <span class="text-sm text-gray-300">{{ row.summary || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'" disable-transitions>
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }">
            <span class="font-mono text-xs text-gray-400">{{ row.ip || '--' }}</span>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Document, Filter, Refresh, Close } from '@element-plus/icons-vue'
import request from '../../utils/request'

const rows    = ref([])
const total   = ref(0)
const page    = ref(1)
const size    = ref(20)
const loading = ref(false)

const dict = reactive({ categories: [], actions: [], results: [] })

const filters = reactive({
  username: '',
  category: '',
  action:   '',
  result:   '',
  targetId: '',
  dateRange: []
})

const loadDict = async () => {
  try {
    const res = await request.get('/admin/audit/dict')
    if (res.code === 200) {
      dict.categories = res.data?.categories || []
      dict.actions    = res.data?.actions    || []
      dict.results    = res.data?.results    || []
    }
  } catch (_) { /* ignore */ }
}

const reload = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value
    }
    if (filters.username) params.username = filters.username.trim()
    if (filters.category) params.category = filters.category
    if (filters.action)   params.action   = filters.action
    if (filters.result)   params.result   = filters.result
    if (filters.targetId) params.targetId = filters.targetId.trim()
    if (filters.dateRange && filters.dateRange.length === 2) {
      params.startTime = filters.dateRange[0]
      params.endTime   = filters.dateRange[1]
    }
    const res = await request.get('/admin/audit', { params })
    if (res.code === 200) {
      rows.value  = res.data?.records || []
      total.value = res.data?.total   || 0
    }
  } catch (_) { /* request 已弹错 */ }
  finally { loading.value = false }
}

const resetFilters = () => {
  filters.username = ''
  filters.category = ''
  filters.action   = ''
  filters.result   = ''
  filters.targetId = ''
  filters.dateRange = []
  page.value = 1
  reload()
}

// -------- 显示助手 --------
const formatTime = (v) => {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}
const formatJson = (s) => {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch (_) { return s }
}
const categoryLabel = (c) => dict.categories.find(o => o.value === c)?.label || c
const actionLabel   = (a) => dict.actions.find(o => o.value === a)?.label || a
const categoryTagType = (c) => {
  switch (c) {
    case 'AUTH':         return 'info'
    case 'TRADE':        return 'success'
    case 'ACCOUNT':      return ''
    case 'ADMIN_USER':   return 'warning'
    case 'ADMIN_STOCK':  return 'warning'
    case 'ADMIN_ORDER':  return 'danger'
    default:             return ''
  }
}

onMounted(async () => {
  await loadDict()
  await reload()
})
</script>

<style scoped>
.admin-card {
  background: rgba(12, 12, 14, 0.8);
  border: 1px solid rgba(39, 39, 42, 0.8);
  border-radius: 0.875rem;
  padding: 1rem 1.25rem;
}
.expand-block {
  padding: 8px 24px 12px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 8px;
}
.expand-label {
  font-size: 11px;
  color: #6b7280;
  letter-spacing: 1px;
  margin-bottom: 6px;
  text-transform: uppercase;
}
.expand-pre {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 10px 14px;
  color: #d1d5db;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
.meta-key { color: #6b7280; }
.meta-val { color: #d1d5db; }

:deep(.audit-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(99, 102, 241, 0.06);
  --el-table-border-color: rgba(255, 255, 255, 0.04);
  --el-table-text-color: #d1d5db;
  --el-table-header-text-color: #9ca3af;
  --el-table-expanded-cell-bg-color: transparent;
}
:deep(.audit-table th.el-table__cell) {
  background: rgba(24, 24, 27, 0.4) !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05) !important;
  font-size: 12px;
  letter-spacing: 1px;
}
:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: transparent;
  --el-pagination-button-color: #d1d5db;
  --el-pagination-hover-color: #6366f1;
}
</style>

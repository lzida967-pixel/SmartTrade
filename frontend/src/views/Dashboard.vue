<template>
  <div class="p-6">
    <div class="flex justify-between items-center mb-8">
      <h1 class="text-3xl font-bold tracking-tight text-gray-100">SmartTrade Dashboard</h1>
      <div class="flex gap-4">
        <vs-button type="flat" @click="handleLogout" color="danger" animation-type="scale">
          安全退出
          <template #animate>
            <el-icon><SwitchButton /></el-icon>
          </template>
        </vs-button>
      </div>
    </div>

    <el-row :gutter="20" class="mb-4">
      <el-col :span="8">
        <el-card shadow="hover" >
          <template #header>
            <div class="card-header">
              <span>总资产估值 (CNY)</span>
            </div>
          </template>
          <div class="text-4xl font-bold text-red-500">
             1,000,000.00
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>可用资金</span>
            </div>
          </template>
          <div class="text-2xl font-bold">
             1,000,000.00
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>今日盈亏</span>
            </div>
          </template>
          <div class="text-2xl font-bold text-gray-400">
             0.00 (0.00%)
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const handleLogout = () => {
  userStore.clearAuth()
  ElMessage.success('已安全断开服务端连接，登出成功')
  router.push('/login')
}
</script>

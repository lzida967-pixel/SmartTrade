<template>
  <el-config-provider>
    <!-- 对于登录/注册页面，隐藏所有框架菜单，直接展现主体 -->
    <template v-if="$route.meta.hideLayout">
      <router-view />
    </template>

    <!-- 已登录的业务系统骨架架构 -->
    <div v-else class="min-h-screen bg-[#141414] text-white">
      <el-container class="h-screen outline-none">
        <el-aside width="240px" class="border-r border-gray-800">
          <div class="h-16 flex items-center justify-center font-bold text-xl border-b border-gray-800 text-blue-500 tracking-wider">
            <el-icon class="mr-2"><TrendCharts /></el-icon> SmartTrade
          </div>
          <el-menu default-active="/" router class="border-none !bg-transparent" active-text-color="#409eff">
            <el-menu-item index="/">
              <el-icon><DataBoard /></el-icon>
              <span>交易看盘</span>
            </el-menu-item>
            <el-menu-item index="/orders">
              <el-icon><List /></el-icon>
              <span>委托单记录</span>
            </el-menu-item>
            <el-menu-item index="/positions">
              <el-icon><Wallet /></el-icon>
              <span>我的持仓</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        
        <el-container>
          <el-header class="h-16 border-b border-gray-800 flex justify-between items-center px-6">
            <div class="font-medium">A股虚拟撮合交易系统</div>
            <div class="flex items-center gap-4">
              <el-avatar size="small" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
              <span>测试账户</span>
            </div>
          </el-header>
          
          <el-main class="p-0">
            <router-view />
          </el-main>
        </el-container>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup>
</script>

<style>
/* 覆盖 Element Plus 侧边栏自带的边框 */
.el-menu {
  border-right: none;
}
</style>

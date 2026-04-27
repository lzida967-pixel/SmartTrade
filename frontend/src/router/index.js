import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { hideLayout: true }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue')
  },
  {
    path: '/market',
    name: 'Market',
    component: () => import('../views/Market.vue')
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('../views/Orders.vue')
  },
  {
    path: '/positions',
    name: 'Positions',
    component: () => import('../views/Positions.vue')
  },
  {
    path: '/admin',
    component: () => import('../views/SystemAdmin.vue'),
    meta: { requireAdmin: true, hideLayout: true },
    children: [
      { path: '', redirect: '/admin/users' },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../views/admin/AdminUsers.vue')
      },
      {
        path: 'stocks',
        name: 'AdminStocks',
        component: () => import('../views/admin/AdminStocks.vue')
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('../views/admin/AdminOrders.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：检查登录态且处理新标签页刷新的情况
router.beforeEach(async (to, from) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    return '/login'
  }

  // 如果访问的是新标签页/刷新页面，userInfo可能尚未加载，此时必须先等待拉取信息
  if (userStore.token && !userStore.userInfo && to.path !== '/login') {
    try {
      const res = await request.get('/user/info')
      if (res.code === 200) {
        userStore.setUserInfo(res.data)
      } else {
        userStore.clearAuth()
        return '/login'
      }
    } catch (e) {
      userStore.clearAuth()
      return '/login'
    }
  }

  if (to.meta.requireAdmin && userStore.userInfo?.role !== 'ADMIN') {
    return '/' // 不是管理员则重定向回首页
  }
  return true
})

export default router

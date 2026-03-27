import axios from 'axios'
import { useUserStore } from '../stores/user'
import router from '../router'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：携带 Token
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
        config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理全局异常和 401 错误
request.interceptors.response.use(
  response => {
    const res = response.data
    // 如果返回的自定义状态码是 200，说明业务成功
    if (res.code === 200) {
      return res
    } else {
      // 业务错误，如账号密码错误等
      ElMessage.error(res.msg || '系统异常')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
  },
  error => {
    // 处理 HTTP 状态码错误
    if (error.response) {
      if (error.response.status === 401) {
        ElMessage.warning('身份校验过期，请重新登录')
        const userStore = useUserStore()
        userStore.clearAuth()
        router.push('/login')
      } else {
        ElMessage.error(error.response.data?.msg || '网络或服务器错误')
      }
    } else {
      ElMessage.error('请求超时或网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
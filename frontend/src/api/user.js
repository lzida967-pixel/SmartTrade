/**
 * 用户认证 / 个人中心相关 API
 */
import request from '../utils/request'

/** 登录 */
export const login = (username, password) =>
  request.post('/user/login', { username, password })

/** 注册 */
export const register = (data) =>
  request.post('/user/register', data)

/** 申请重置密码（联系管理员核实身份） */
export const requestPasswordReset = (username) =>
  request.post('/user/password-reset-request', { username })

/** 获取当前登录用户信息 */
export const getUserInfo = () => request.get('/user/info')

/** 修改个人资料（昵称、头像） */
export const updateProfile = (data) =>
  request.put('/user/profile', data)

/** 修改密码 */
export const changePassword = (oldPassword, newPassword) =>
  request.put('/user/password', { oldPassword, newPassword })

/** 用户资产快照 */
export const getAsset = () => request.get('/user/asset')

/** 用户净值曲线 */
export const getAssetCurve = (days = 30) =>
  request.get('/user/asset/curve', { params: { days } })

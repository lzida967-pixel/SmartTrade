/**
 * 后台 - 用户管理 API
 */
import request from '../../utils/request'

/** 用户列表（分页 / 关键字 / 角色 / 状态过滤） */
export const list = (params) =>
  request.get('/admin/users', { params })

/** 调整资金（入金/出金/冻结/解冻等） */
export const updateFunds = (id, payload) =>
  request.put(`/admin/users/${id}/funds`, payload)

/** 修改角色 */
export const updateRole = (id, role) =>
  request.put(`/admin/users/${id}/role`, { role })

/** 启用 / 禁用账号 */
export const updateStatus = (id, status) =>
  request.put(`/admin/users/${id}/status`, { status })

/** 重置密码（默认 123456） */
export const resetPassword = (id) =>
  request.put(`/admin/users/${id}/reset-password`)

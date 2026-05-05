/**
 * 后台 - 委托单管理 API
 */
import request from '../../utils/request'

/** 全平台委托单分页查询 */
export const list = (params) =>
  request.get('/admin/orders', { params })

/** 强制撤单 */
export const forceCancel = (orderNo) =>
  request.post(`/admin/orders/${orderNo}/force-cancel`)

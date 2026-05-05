/**
 * 后台 - 股票池 / 数据同步 API
 */
import request from '../../utils/request'

/** 股票池分页列表 */
export const list = (params) =>
  request.get('/admin/stocks', { params })

/** 加入股票池 */
export const add = (payload) =>
  request.post('/admin/stocks', payload)

/** 从股票池移除 */
export const remove = (stockCode) =>
  request.delete(`/admin/stocks/${stockCode}`)

/** 股票池统计（总数 / 已有数据 / 缺口） */
export const getStats = () => request.get('/admin/stocks/stats')

/** 数据同步任务状态 */
export const getSyncStatus = () => request.get('/admin/stocks/sync-status')

/** 触发全量同步 */
export const syncAll = () => request.post('/admin/stocks/sync')

/** 仅同步缺数据的股票 */
export const syncMissing = () => request.post('/admin/stocks/sync-missing')

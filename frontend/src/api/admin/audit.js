/**
 * 后台 - 审计日志 API
 */
import request from '../../utils/request'

/** 字典：所有 category / action 枚举值 */
export const getDict = () => request.get('/admin/audit/dict')

/** 审计日志分页查询 */
export const list = (params) =>
  request.get('/admin/audit', { params })

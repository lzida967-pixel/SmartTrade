/**
 * 后台 - 预测准确率监控 API
 */
import request from '../../utils/request'

/** 模型预测统计（最近 N 天命中率、覆盖率等） */
export const getStats = (days = 30) =>
  request.get('/admin/prediction/stats', { params: { days } })

/** 触发待回测预测的核对（返回 verifiedCount） */
export const verify = () =>
  request.post('/admin/prediction/verify')

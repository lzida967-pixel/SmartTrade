/**
 * 后台 - 总览仪表盘 API
 */
import request from '../../utils/request'

/** 总览数据：用户、订单、持仓、最新预测等聚合 */
export const getOverview = () => request.get('/admin/dashboard/overview')

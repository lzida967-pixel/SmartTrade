/**
 * 交易 / 持仓 / 委托 / 成交 相关 API
 */
import request from '../utils/request'

/** 当前用户持仓列表 */
export const getPositions = () => request.get('/trade/positions')

/** 委托单分页查询 */
export const getOrders = (params) =>
  request.get('/trade/orders', { params })

/** 成交记录分页查询 */
export const getDeals = (params) =>
  request.get('/trade/deals', { params })

/** 下单（限价 / 市价） */
export const placeOrder = (payload) =>
  request.post('/trade/order', payload)

/** 撤销委托单 */
export const cancelOrder = (orderNo) =>
  request.post(`/trade/order/${orderNo}/cancel`)

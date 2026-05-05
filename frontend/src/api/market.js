/**
 * 行情 / K 线 / 股票池相关 API
 */
import request from '../utils/request'

/** 获取全市场股票基础信息列表 */
export const getStockList = () => request.get('/stock/list')

/** 实时行情快照 */
export const getQuotes = () => request.get('/stock/quotes')

/**
 * 获取股票最近 N 个交易日的日 K 线
 * @param {string} code  6 位股票代码
 * @param {number} [limit=60]
 */
export const getKline = (code, limit = 60) =>
  request.get(`/stock/kline/${code}`, { params: { limit } })

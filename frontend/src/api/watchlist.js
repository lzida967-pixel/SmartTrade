/**
 * 自选股 API
 */
import request from '../utils/request'

/** 获取当前用户的自选代码集合 */
export const getWatchlist = () => request.get('/watchlist/codes')

/** 添加自选 */
export const addWatch = (code) => request.post(`/watchlist/${code}`)

/** 移除自选 */
export const removeWatch = (code) => request.delete(`/watchlist/${code}`)

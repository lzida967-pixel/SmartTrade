/**
 * 后台 - 缓存命中率监控 API
 */
import request from '../../utils/request'

/**
 * 获取缓存监控快照
 *
 * 返回字段：
 *   items[]:           各前缀的命中详情 [{prefix, hits, misses, total, hitRate}]
 *   totalHits:         总命中
 *   totalMisses:       总未命中
 *   totalRequests:     总请求数
 *   overallHitRate:    总命中率 [0,1]
 *   qps:               累计 QPS = 总请求 / 统计时长
 *   startedAt:         统计起始时间（ISO 字符串）
 *   elapsedSeconds:    统计时长（秒）
 */
export const getMetrics = () => request.get('/admin/cache/metrics')

/** 清零计数器（演示压测前可调用） */
export const resetMetrics = () => request.post('/admin/cache/metrics/reset')

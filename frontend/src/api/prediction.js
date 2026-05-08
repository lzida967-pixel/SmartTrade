/**
 * 智能预测相关 API
 *
 * 后端路径：/api/prediction/**  →  Spring Boot  →  Python FastAPI
 */
import request from '../utils/request'

/**
 * 单只股票预测
 * @param {string} code  6 位股票代码
 * @param {'lgbm'|'xgb'|'lstm'} [model='lgbm']
 * @returns {Promise<{code:number, data:object}>}
 */
export const predict = (code, model = 'lgbm') =>
  request.get(`/prediction/predict/${code}`, { params: { model } })

/**
 * 同股并行调 LightGBM + XGBoost，返回 [a, b] 的 settled 结果
 */
export const compareTreeModels = (code) =>
  Promise.allSettled([predict(code, 'lgbm'), predict(code, 'xgb')])

/**
 * AI 单模型报告 SSE URL（usePredictionSse 的 buildUrl 用）
 * 由于 SSE 走原生 fetch 而非 axios，URL 里要带上 /api 前缀
 */
export const reportStreamUrl = (code, model = 'lgbm') =>
  `/api/prediction/predict/${code}/report?model=${model}`

/**
 * AI 模型对比报告 SSE URL
 */
export const compareReportStreamUrl = (code) =>
  `/api/prediction/compare/${code}/report`

// ====================================================================
// 模型实证评估（最近 N 天三模型准确率回顾）
// ====================================================================

/** 整体概览：三模型准确率 + 3×3 混淆矩阵 */
export const getEvaluationOverview = (days = 30) =>
  request.get('/prediction/evaluation/overview', { params: { days } })

/** 按日时序：每个交易日的三模型准确率 */
export const getEvaluationTimeline = (days = 30) =>
  request.get('/prediction/evaluation/timeline', { params: { days } })

/** 按股票排行：某模型最近 N 天命中率最高的股票 */
export const getEvaluationByStock = (days = 30, model = 'lgbm_v1', limit = 20) =>
  request.get('/prediction/evaluation/by-stock', { params: { days, model, limit } })

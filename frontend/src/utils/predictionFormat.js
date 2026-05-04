/**
 * 预测页共享工具：标签元信息、特征值 / 重要性格式化、特征中文释义
 * 被 Prediction.vue 与 PredictionCompare.vue 复用
 */

// 三分类标签元信息：0 看多 / 1 震荡 / 2 看空
export const labelMeta = {
  0: { name: '看多', color: '#ef4444', bg: 'rgba(239,68,68,0.12)', icon: '↑' },
  1: { name: '震荡', color: '#94a3b8', bg: 'rgba(148,163,184,0.12)', icon: '—' },
  2: { name: '看空', color: '#10b981', bg: 'rgba(16,185,129,0.12)', icon: '↓' }
}

// 百分比类特征（原始值为小数，展示时乘 100 加 %）
const PCT_FEATS = new Set([
  'ret_1d', 'ret_5d', 'ret_10d', 'ret_20d', 'ret_60d',
  'close_ma5', 'close_ma10', 'close_ma20', 'close_ma60',
  'ma5_ma20', 'ma10_ma60',
  'vol_5d', 'vol_20d', 'vol_60d',
  'atr_pct', 'dist_high_20', 'dist_low_20', 'bb_pos', 'bb_width'
])

/**
 * 特征重要性格式化：
 *   LightGBM 输出整数 split 次数；XGBoost 输出 [0, 1] 归一化浮点。
 */
export const formatImportance = (v) => {
  if (v == null || Number.isNaN(v)) return '—'
  const n = Number(v)
  if (Math.abs(n) < 1) return n.toFixed(4)
  return Math.round(n).toString()
}

/**
 * 特征当前值格式化：根据特征名的语义换算单位。
 *   - 比率类：小数 → 百分比
 *   - 换手率：baostock 原始即百分比单位
 *   - 量比：倍数
 *   - 连涨天数：整数
 *   - 其它：两位小数
 */
export const formatValue = (name, value) => {
  if (value == null || Number.isNaN(Number(value))) return '—'
  if (PCT_FEATS.has(name)) return (value * 100).toFixed(2) + '%'
  if (name === 'turnover_rate_ma5') return Number(value).toFixed(2) + '%'
  if (name === 'vol_ratio_5' || name === 'vol_ratio_20') return Number(value).toFixed(2) + 'x'
  if (name === 'up_streak') return Math.round(value).toString()
  return Number(value).toFixed(2)
}

// 特征中文释义
export const featureDescMap = {
  ret_1d: '近 1 日涨跌幅',
  ret_5d: '近 5 日累计涨跌幅',
  ret_10d: '近 10 日累计涨跌幅',
  ret_20d: '近 20 日累计涨跌幅',
  ret_60d: '近 60 日累计涨跌幅（中期动量）',
  close_ma5: '收盘价相对 5 日均线偏离度',
  close_ma10: '收盘价相对 10 日均线偏离度',
  close_ma20: '收盘价相对 20 日均线偏离度',
  close_ma60: '收盘价相对 60 日均线偏离度',
  ma5_ma20: '5 日均线相对 20 日均线发散度',
  ma10_ma60: '10 日均线相对 60 日均线发散度',
  vol_5d: '近 5 日收益率标准差（短期波动）',
  vol_20d: '近 20 日收益率标准差（中期波动）',
  vol_60d: '近 60 日收益率标准差（长期波动）',
  atr_pct: '14 日平均真实波幅 / 收盘价',
  rsi_6: 'RSI(6) 强弱指标，>70 超买 / <30 超卖',
  rsi_14: 'RSI(14) 强弱指标',
  macd: 'MACD 主线值',
  macd_signal: 'MACD 信号线值',
  macd_diff: 'MACD 柱（差值）',
  kdj_k: 'KDJ 的 K 值',
  kdj_d: 'KDJ 的 D 值',
  kdj_j: 'KDJ 的 J 值',
  bb_pos: '布林带相对位置（0=下轨，1=上轨）',
  bb_width: '布林带带宽 / 中轨',
  vol_ratio_5: '当日量 / 5 日均量（量比）',
  vol_ratio_20: '当日量 / 20 日均量',
  turnover_rate_ma5: '5 日平均换手率',
  up_streak: '连涨天数',
  dist_high_20: '收盘价相对 20 日最高价距离',
  dist_low_20: '收盘价相对 20 日最低价距离',
  pe_ttm: 'PE-TTM 动态市盈率',
  pb_mrq: 'PB 市净率',
  ps_ttm: 'PS-TTM 市销率',
  log_amount: '成交额对数（log1p）'
}

export const featureDescription = (name) => featureDescMap[name] || '—'

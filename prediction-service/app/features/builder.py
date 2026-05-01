"""特征工程：从单只股票时间序列构造 30+ 维特征 + 三分类标签。

输入 df 必须含列：
    date, code, open, high, low, close, preclose, volume,
    turnover, turnover_rate, pct_change,
    peTTM, pbMRQ, psTTM, pcfNcfTTM (basic; 可选)

特征分类：
    - 收益率类     ret_*
    - 均线相对位置 close_ma_*, ma_cross_*
    - 波动率类     vol_*, atr_pct
    - 技术指标     rsi_*, macd_*, kdj_*, bb_*
    - 量能类       vol_ratio_*, turnover_*
    - 形态类       up_streak, dist_high_20, dist_low_20
    - 基本面       pe_ttm, pb_mrq, ps_ttm, log_amount
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from ta.momentum import RSIIndicator
from ta.trend import MACD
from ta.volatility import AverageTrueRange, BollingerBands

from app.core.config import settings


# 最终训练用的特征列（顺序固定，便于推理时对齐）
FEATURE_COLUMNS: list[str] = [
    # 收益率
    "ret_1d", "ret_5d", "ret_10d", "ret_20d", "ret_60d",
    # 均线相对位置
    "close_ma5", "close_ma10", "close_ma20", "close_ma60",
    "ma5_ma20", "ma10_ma60",
    # 波动率
    "vol_5d", "vol_20d", "vol_60d", "atr_pct",
    # 技术指标
    "rsi_6", "rsi_14",
    "macd", "macd_signal", "macd_diff",
    "kdj_k", "kdj_d", "kdj_j",
    "bb_pos", "bb_width",
    # 量能
    "vol_ratio_5", "vol_ratio_20", "turnover_rate_ma5",
    # 形态
    "up_streak", "dist_high_20", "dist_low_20",
    # 基本面
    "pe_ttm", "pb_mrq", "ps_ttm", "log_amount",
]


# ===================== 工具函数 =====================

def _rolling_streak_up(returns: pd.Series) -> pd.Series:
    """连涨天数：向上累计连续 >0 的次数。"""
    pos = (returns > 0).astype(int)
    grp = (pos != pos.shift()).cumsum()
    return pos.groupby(grp).cumsum()


def _kdj(df: pd.DataFrame, n: int = 9) -> pd.DataFrame:
    """计算 KDJ（k/d/j 列），weighted moving average 公式（中国常用）。"""
    low_n = df["low"].rolling(n, min_periods=1).min()
    high_n = df["high"].rolling(n, min_periods=1).max()
    rsv = (df["close"] - low_n) / (high_n - low_n).replace(0, np.nan) * 100

    k = pd.Series(index=df.index, dtype="float64")
    d = pd.Series(index=df.index, dtype="float64")
    k_prev = 50.0
    d_prev = 50.0
    for i, v in enumerate(rsv.fillna(50.0)):
        k_cur = (2.0 / 3.0) * k_prev + (1.0 / 3.0) * v
        d_cur = (2.0 / 3.0) * d_prev + (1.0 / 3.0) * k_cur
        k.iloc[i] = k_cur
        d.iloc[i] = d_cur
        k_prev, d_prev = k_cur, d_cur
    j = 3 * k - 2 * d
    return pd.DataFrame({"kdj_k": k, "kdj_d": d, "kdj_j": j})


# ===================== 主函数 =====================

def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """对单只股票时间序列构造特征列。返回的 df 仍按 date 升序。

    要求 df 已按 date 升序。会丢弃前若干行（指标 warmup）。
    """
    df = df.copy().sort_values("date").reset_index(drop=True)

    close = df["close"]
    high = df["high"]
    low = df["low"]
    volume = df["volume"]

    # ---- 收益率 ----
    df["ret_1d"] = close.pct_change(1)
    df["ret_5d"] = close.pct_change(5)
    df["ret_10d"] = close.pct_change(10)
    df["ret_20d"] = close.pct_change(20)
    df["ret_60d"] = close.pct_change(60)

    # ---- 均线 ----
    ma5 = close.rolling(5).mean()
    ma10 = close.rolling(10).mean()
    ma20 = close.rolling(20).mean()
    ma60 = close.rolling(60).mean()
    df["close_ma5"] = close / ma5 - 1
    df["close_ma10"] = close / ma10 - 1
    df["close_ma20"] = close / ma20 - 1
    df["close_ma60"] = close / ma60 - 1
    df["ma5_ma20"] = ma5 / ma20 - 1
    df["ma10_ma60"] = ma10 / ma60 - 1

    # ---- 波动率 ----
    df["vol_5d"] = df["ret_1d"].rolling(5).std()
    df["vol_20d"] = df["ret_1d"].rolling(20).std()
    df["vol_60d"] = df["ret_1d"].rolling(60).std()

    atr = AverageTrueRange(high=high, low=low, close=close, window=14, fillna=False)
    df["atr_pct"] = atr.average_true_range() / close

    # ---- 技术指标 ----
    df["rsi_6"] = RSIIndicator(close=close, window=6, fillna=False).rsi()
    df["rsi_14"] = RSIIndicator(close=close, window=14, fillna=False).rsi()

    macd_obj = MACD(close=close, window_slow=26, window_fast=12, window_sign=9, fillna=False)
    df["macd"] = macd_obj.macd()
    df["macd_signal"] = macd_obj.macd_signal()
    df["macd_diff"] = macd_obj.macd_diff()

    kdj = _kdj(df)
    df["kdj_k"] = kdj["kdj_k"]
    df["kdj_d"] = kdj["kdj_d"]
    df["kdj_j"] = kdj["kdj_j"]

    bb = BollingerBands(close=close, window=20, window_dev=2, fillna=False)
    bb_h = bb.bollinger_hband()
    bb_l = bb.bollinger_lband()
    bb_m = bb.bollinger_mavg()
    df["bb_pos"] = (close - bb_l) / (bb_h - bb_l).replace(0, np.nan)
    df["bb_width"] = (bb_h - bb_l) / bb_m.replace(0, np.nan)

    # ---- 量能 ----
    vol_ma5 = volume.rolling(5).mean()
    vol_ma20 = volume.rolling(20).mean()
    df["vol_ratio_5"] = volume / vol_ma5
    df["vol_ratio_20"] = volume / vol_ma20
    if "turnover_rate" in df.columns:
        df["turnover_rate_ma5"] = df["turnover_rate"].rolling(5).mean()
    else:
        df["turnover_rate_ma5"] = np.nan

    # ---- 形态 ----
    df["up_streak"] = _rolling_streak_up(df["ret_1d"])
    high_20 = high.rolling(20).max()
    low_20 = low.rolling(20).min()
    df["dist_high_20"] = close / high_20 - 1
    df["dist_low_20"] = close / low_20 - 1

    # ---- 基本面 ----
    for col, target in [
        ("peTTM", "pe_ttm"),
        ("pbMRQ", "pb_mrq"),
        ("psTTM", "ps_ttm"),
    ]:
        if col in df.columns:
            df[target] = df[col]
        else:
            df[target] = np.nan

    if "turnover" in df.columns:
        # log 缩放成交额，量纲合理
        df["log_amount"] = np.log1p(df["turnover"].clip(lower=0))
    else:
        df["log_amount"] = np.nan

    return df


def add_label(df: pd.DataFrame, forward_days: int | None = None,
              threshold: float | None = None) -> pd.DataFrame:
    """添加 T+N 涨跌幅三分类标签。

    label:
        0 看多 (未来 forward_days 涨幅 > +threshold)
        1 震荡 (-threshold ~ +threshold)
        2 看空 (跌幅 > -threshold)

    返回的 df 末尾 forward_days 行 label 为 NaN（无法标注）。
    """
    fwd = forward_days or settings.forward_days
    th = threshold if threshold is not None else settings.label_threshold

    df = df.copy()
    df["future_close"] = df["close"].shift(-fwd)
    df["future_ret"] = df["future_close"] / df["close"] - 1

    def _label(r: float) -> float:
        if pd.isna(r):
            return np.nan
        if r > th:
            return 0
        if r < -th:
            return 2
        return 1

    df["label"] = df["future_ret"].apply(_label)
    return df


def build_features_with_label(df: pd.DataFrame) -> pd.DataFrame:
    """一站式：构造特征 + 标签，返回完整 df（仍含 NaN，未筛选）。"""
    df = build_features(df)
    df = add_label(df)
    return df


__all__ = [
    "FEATURE_COLUMNS",
    "build_features",
    "add_label",
    "build_features_with_label",
]

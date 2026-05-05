"""推理时实时拉取单只股票最近 N 天 K 线（带 5 分钟内存缓存）。"""
from __future__ import annotations

import time
from datetime import datetime, timedelta

import pandas as pd
from loguru import logger

from app.data.loader import _fetch_baostock, _ensure_bs_login, bs_reset_login
from app.data.db_fallback import fetch_from_db


# 简单内存缓存：{code: (timestamp, df)}
_CACHE: dict[str, tuple[float, pd.DataFrame]] = {}
_CACHE_TTL = 300  # 5 分钟

# baostock 重试参数
_BS_MAX_ATTEMPTS = 2
_BS_RETRY_DELAY = 1.0  # 秒


def fetch_recent(code: str, days: int = 120) -> pd.DataFrame | None:
    """拉取单只股票最近 days 天 K 线。

    策略：
      1. 命中内存缓存（5 分钟）直接返回
      2. baostock 重试 2 次（间隔 1 秒，每次失败后重置登录态）
      3. 仍失败则回退到本地 MySQL zidatrade_stock_daily_price

    用于推理：特征构造需要 ~60 天 warmup，所以默认拉 120 天保守。
    """
    code = str(code).zfill(6)
    now = time.time()

    # 命中缓存
    if code in _CACHE:
        ts, df = _CACHE[code]
        if now - ts < _CACHE_TTL:
            return df

    end = datetime.now()
    start = end - timedelta(days=days * 2)  # 节假日 buffer
    start_iso = start.strftime("%Y-%m-%d")
    end_iso = end.strftime("%Y-%m-%d")

    # ---------- 1) baostock 带重试 ----------
    df: pd.DataFrame | None = None
    for attempt in range(1, _BS_MAX_ATTEMPTS + 1):
        try:
            _ensure_bs_login()
            df = _fetch_baostock(code, start_iso, end_iso)
            if df is not None and not df.empty:
                break
            df = None
        except Exception as e:
            logger.warning(f"[{code}] baostock 第 {attempt}/{_BS_MAX_ATTEMPTS} 次失败: {e}")
            bs_reset_login()  # 连接可能已断，强制下次重新登录
            df = None
            if attempt < _BS_MAX_ATTEMPTS:
                time.sleep(_BS_RETRY_DELAY)

    # ---------- 2) DB 回退 ----------
    if df is None or df.empty:
        logger.warning(f"[{code}] baostock 全部失败，回退本地 DB")
        df = fetch_from_db(code, start_iso, end_iso)

    if df is None or df.empty:
        logger.error(f"[{code}] baostock 和 DB 均无数据")
        return None

    _CACHE[code] = (now, df)
    return df


def clear_cache() -> int:
    """清空缓存，返回清除条数。"""
    n = len(_CACHE)
    _CACHE.clear()
    return n


__all__ = ["fetch_recent", "clear_cache"]

"""推理时实时拉取单只股票最近 N 天 K 线（带 5 分钟内存缓存）。"""
from __future__ import annotations

import time
from datetime import datetime, timedelta

import pandas as pd
from loguru import logger

from app.data.loader import _fetch_baostock, _ensure_bs_login


# 简单内存缓存：{code: (timestamp, df)}
_CACHE: dict[str, tuple[float, pd.DataFrame]] = {}
_CACHE_TTL = 300  # 5 分钟


def fetch_recent(code: str, days: int = 120) -> pd.DataFrame | None:
    """拉取单只股票最近 days 天 K 线。

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

    try:
        _ensure_bs_login()
        df = _fetch_baostock(code, start_iso, end_iso)
    except Exception as e:
        logger.error(f"[{code}] 实时拉取失败: {e}")
        return None

    if df is None or df.empty:
        return None

    _CACHE[code] = (now, df)
    return df


def clear_cache() -> int:
    """清空缓存，返回清除条数。"""
    n = len(_CACHE)
    _CACHE.clear()
    return n


__all__ = ["fetch_recent", "clear_cache"]

"""baostock 失败时的回退数据源：从本地 MySQL 读取日 K 线。

数据表：zidatrade_stock_daily_price
注意：DB 只有 OHLCV + 涨跌幅，缺少基本面字段（peTTM/pbMRQ/psTTM），
以 0.0 占位，避免下游 feature builder 因 NaN 被全量 dropna 清空。
"""
from __future__ import annotations

import pandas as pd
import pymysql
from loguru import logger

from app.core.config import settings


_SQL = """
SELECT trade_date, open_price, close_price, high_price, low_price,
       pre_close_price, volume, turnover_amount, change_percent
FROM zidatrade_stock_daily_price
WHERE stock_code = %s
  AND trade_date >= %s
  AND trade_date <= %s
ORDER BY trade_date
"""


def fetch_from_db(code: str, start_iso: str, end_iso: str) -> pd.DataFrame | None:
    """从本地 DB 读取 K 线并对齐 baostock 格式。找不到或失败返回 None。"""
    try:
        conn = pymysql.connect(
            host=settings.db_host,
            port=settings.db_port,
            user=settings.db_user,
            password=settings.db_password,
            database=settings.db_name,
            charset="utf8mb4",
            connect_timeout=3,
            read_timeout=5,
        )
    except Exception as e:
        logger.error(f"DB 连接失败: {e}")
        return None

    try:
        df = pd.read_sql(_SQL, conn, params=(code, start_iso, end_iso))
    except Exception as e:
        logger.error(f"[{code}] DB 查询失败: {e}")
        return None
    finally:
        conn.close()

    if df is None or df.empty:
        return None

    df = df.rename(columns={
        "trade_date":      "date",
        "open_price":      "open",
        "close_price":     "close",
        "high_price":      "high",
        "low_price":       "low",
        "pre_close_price": "preclose",
        "turnover_amount": "turnover",
        "change_percent":  "pct_change",
    })
    df["date"] = pd.to_datetime(df["date"])
    df["code"] = code
    # DB 里没有的字段用 0 占位（feature builder 会把 NaN 行全删）
    df["turnover_rate"] = 0.0
    df["peTTM"] = 0.0
    df["pbMRQ"] = 0.0
    df["psTTM"] = 0.0
    df["pcfNcfTTM"] = 0.0
    df["isST"] = "0"

    for c in ("open", "high", "low", "close", "preclose",
              "volume", "turnover", "pct_change"):
        df[c] = pd.to_numeric(df[c], errors="coerce")

    df = df.sort_values("date").reset_index(drop=True)
    logger.info(
        f"[{code}] DB fallback 读取 {len(df)} 行 "
        f"({df['date'].min().date()} ~ {df['date'].max().date()})"
    )
    return df


__all__ = ["fetch_from_db"]

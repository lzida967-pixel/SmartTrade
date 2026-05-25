"""历史 K 线数据获取与缓存。

主源：baostock（免费、稳定、不限速、无需 token）
备源：akshare（东财，限速严重，仅当 baostock 失败时使用）

落地到 data/raw/<code>.parquet。
"""
from __future__ import annotations

import socket
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable

import akshare as ak
import baostock as bs
import pandas as pd
from loguru import logger

from app.core.config import RAW_DIR, settings, PROJECT_ROOT


# ===================== 全局 baostock 会话管理 =====================
_BS_LOGGED_IN = False


def _ensure_bs_login() -> None:
    """惰性登录 baostock，整个进程复用一次。"""
    global _BS_LOGGED_IN
    if not _BS_LOGGED_IN:
        rs = bs.login()
        if rs.error_code != "0":
            raise RuntimeError(f"baostock 登录失败: {rs.error_msg}")
        _BS_LOGGED_IN = True
        logger.info("baostock 登录成功")


def bs_logout() -> None:
    """关闭 baostock 会话（脚本结束时调用）。"""
    global _BS_LOGGED_IN
    if _BS_LOGGED_IN:
        bs.logout()
        _BS_LOGGED_IN = False
        logger.info("baostock 已退出")


def bs_reset_login() -> None:
    """连接被断开时，重置登录标记以便下次重新登录。"""
    global _BS_LOGGED_IN
    try:
        bs.logout()
    except Exception:
        pass
    _BS_LOGGED_IN = False


def _to_bs_code(code: str) -> str:
    """6 位数字转 baostock 代码：sh.600519 / sz.000001 / bj.430510"""
    code = str(code).zfill(6)
    if code.startswith(("60", "68", "9")):
        return f"sh.{code}"
    if code.startswith(("00", "30", "20")):
        return f"sz.{code}"
    if code.startswith(("4", "8")):
        return f"bj.{code}"
    return f"sz.{code}"


# ===================== 股票池构建 =====================

def get_csi300_codes() -> list[str]:
    """获取沪深 300 成分股代码（优先 baostock，失败兜底 akshare）。"""
    try:
        _ensure_bs_login()
        rs = bs.query_hs300_stocks()
        rows = []
        while rs.error_code == "0" and rs.next():
            rows.append(rs.get_row_data())
        if rows:
            df = pd.DataFrame(rows, columns=rs.fields)
            codes = df["code"].astype(str).str[-6:].tolist()
            logger.info(f"沪深 300 成分股数量(baostock): {len(codes)}")
            return codes
    except Exception as e:
        logger.warning(f"baostock 取 HS300 失败: {e}，尝试 akshare")

    try:
        df = ak.index_stock_cons_csindex(symbol="000300")
        for col in ("成分券代码", "成份券代码", "代码", "stock_code"):
            if col in df.columns:
                codes = df[col].astype(str).str.zfill(6).tolist()
                logger.info(f"沪深 300 成分股数量(akshare): {len(codes)}")
                return codes
    except Exception as e:
        logger.error(f"akshare 取 HS300 也失败: {e}")
    return []


def get_project_stocks() -> list[str]:
    f = PROJECT_ROOT / settings.project_stocks_file
    if not f.exists():
        logger.info(f"未发现 {f.name}，跳过项目自选股")
        return []
    codes = []
    for line in f.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s and not s.startswith("#"):
            codes.append(s.zfill(6))
    logger.info(f"项目自选股数量: {len(codes)}")
    return codes


def build_stock_pool() -> list[str]:
    pool: set[str] = set()
    if settings.use_csi300:
        pool.update(get_csi300_codes())
    pool.update(get_project_stocks())
    codes = sorted(pool)
    logger.info(f"最终股票池规模: {len(codes)}")
    return codes


# ===================== K 线拉取 =====================

def _parquet_path(code: str) -> Path:
    return RAW_DIR / f"{code}.parquet"


def _fetch_baostock(code: str, start_iso: str, end_iso: str) -> pd.DataFrame | None:
    """baostock 拉取日 K 线（前复权 adjustflag=2）。"""
    _ensure_bs_login()
    socket.setdefaulttimeout(30)  # 30 秒无响应则抛 socket.timeout
    bs_code = _to_bs_code(code)
    fields = (
        "date,code,open,high,low,close,preclose,volume,amount,"
        "turn,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
    )
    rs = bs.query_history_k_data_plus(
        bs_code, fields,
        start_date=start_iso, end_date=end_iso,
        frequency="d", adjustflag="2",
    )
    if rs.error_code != "0":
        raise RuntimeError(f"baostock {bs_code} 错误: {rs.error_msg}")
    rows = []
    while rs.next():
        rows.append(rs.get_row_data())
    if not rows:
        return None
    df = pd.DataFrame(rows, columns=rs.fields)
    df["date"] = pd.to_datetime(df["date"])
    df["code"] = code
    num_cols = ["open", "high", "low", "close", "preclose", "volume",
                "amount", "turn", "pctChg", "peTTM", "pbMRQ", "psTTM", "pcfNcfTTM"]
    for c in num_cols:
        df[c] = pd.to_numeric(df[c], errors="coerce")
    df = df.rename(columns={
        "amount": "turnover",
        "turn": "turnover_rate",
        "pctChg": "pct_change",
    })
    return df.sort_values("date").reset_index(drop=True)


def _fetch_akshare(code: str, start: str, end: str) -> pd.DataFrame | None:
    """akshare 兜底（注意会限速）。"""
    df = ak.stock_zh_a_hist(
        symbol=code, period="daily",
        start_date=start, end_date=end, adjust="qfq",
    )
    if df is None or df.empty:
        return None
    df = df.rename(columns={
        "日期": "date", "开盘": "open", "收盘": "close",
        "最高": "high", "最低": "low", "成交量": "volume",
        "成交额": "turnover", "振幅": "amplitude",
        "涨跌幅": "pct_change", "涨跌额": "change",
        "换手率": "turnover_rate",
    })
    df["date"] = pd.to_datetime(df["date"])
    df["code"] = code
    for c in ("open", "close", "high", "low", "volume", "turnover",
              "amplitude", "pct_change", "change", "turnover_rate"):
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df.sort_values("date").reset_index(drop=True)


def fetch_history(
    code: str,
    start: str,
    end: str,
    retries: int = 2,
    backoff: float = 1.5,
) -> pd.DataFrame | None:
    """拉取单只股票日 K 线。baostock 两次失败后直接返回 None。

    start/end: YYYYMMDD 格式（与脚本兼容），内部转 ISO。
    """
    start_iso = f"{start[0:4]}-{start[4:6]}-{start[6:8]}"
    end_iso = f"{end[0:4]}-{end[4:6]}-{end[6:8]}"

    last_err: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            df = _fetch_baostock(code, start_iso, end_iso)
            if df is not None and not df.empty:
                return df
            return None
        except Exception as e:
            last_err = e
            wait = backoff ** attempt
            logger.warning(f"[{code}] baostock 第 {attempt}/{retries} 次失败: {e}. {wait:.1f}s 后重试")
            bs_reset_login()  # 超时/断连后重置会话，下次重新登录
            time.sleep(wait)

    logger.error(f"[{code}] baostock 连续 {retries} 次失败，停止拉取该股票（最后错误: {last_err}）")
    return None


def save_kline(df: pd.DataFrame, code: str) -> Path:
    p = _parquet_path(code)
    df.to_parquet(p, index=False)
    return p


def load_kline(code: str) -> pd.DataFrame | None:
    p = _parquet_path(code)
    if not p.exists():
        return None
    return pd.read_parquet(p)


def fetch_pool(
    codes: Iterable[str],
    years: int | None = None,
    skip_exists: bool = True,
    sleep: float = 0.1,
) -> dict[str, str]:
    """批量拉取股票池数据。返回 {code: status}。"""
    years = years or settings.history_years
    end = datetime.now().strftime("%Y%m%d")
    start = (datetime.now() - timedelta(days=365 * years)).strftime("%Y%m%d")

    result: dict[str, str] = {}
    codes = list(codes)
    total = len(codes)
    try:
        for i, code in enumerate(codes, 1):
            if skip_exists and _parquet_path(code).exists():
                result[code] = "cached"
                logger.info(f"[{i}/{total}] {code} 已缓存，跳过")
                continue
            logger.info(f"[{i}/{total}] 开始拉取 {code}")
            df = fetch_history(code, start, end)
            if df is None or df.empty:
                result[code] = "empty"
                logger.warning(f"[{i}/{total}] {code} 无数据")
            else:
                save_kline(df, code)
                result[code] = f"ok ({len(df)} rows)"
                logger.info(f"[{i}/{total}] {code} 已保存 {len(df)} 行")
            time.sleep(sleep)
    finally:
        bs_logout()
    return result


__all__ = [
    "get_csi300_codes",
    "get_project_stocks",
    "build_stock_pool",
    "fetch_history",
    "save_kline",
    "load_kline",
    "fetch_pool",
    "bs_logout",
]

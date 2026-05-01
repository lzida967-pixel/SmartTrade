"""一次性数据拉取脚本：从 akshare 拉取股票池历史 K 线落到 data/raw/。

用法：
    python scripts/fetch_data.py                 # 拉取 HS300 + project_stocks.txt
    python scripts/fetch_data.py --years 5       # 拉取最近 5 年
    python scripts/fetch_data.py --no-skip       # 强制重新拉取已缓存

成功后下一步：P2 特征工程 + 训练。
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 允许脚本直接 python scripts/fetch_data.py 运行
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from loguru import logger  # noqa: E402

from app.data.loader import build_stock_pool, fetch_pool  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="拉取股票池历史 K 线")
    parser.add_argument("--years", type=int, default=None, help="历史年限")
    parser.add_argument("--no-skip", action="store_true", help="强制重新拉取已缓存")
    parser.add_argument("--codes", nargs="*", help="指定代码（覆盖默认股票池）")
    parser.add_argument("--sleep", type=float, default=0.3, help="单次请求间隔（秒）")
    args = parser.parse_args()

    if args.codes:
        codes = [c.zfill(6) for c in args.codes]
        logger.info(f"使用指定股票池: {codes}")
    else:
        codes = build_stock_pool()

    if not codes:
        logger.error("股票池为空，退出。请检查 akshare 网络或 project_stocks.txt")
        return 1

    result = fetch_pool(
        codes=codes,
        years=args.years,
        skip_exists=not args.no_skip,
        sleep=args.sleep,
    )

    ok = sum(1 for v in result.values() if v.startswith("ok"))
    cached = sum(1 for v in result.values() if v == "cached")
    empty = sum(1 for v in result.values() if v == "empty")
    logger.info(f"汇总: 成功 {ok}  已缓存 {cached}  无数据 {empty}  共 {len(result)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

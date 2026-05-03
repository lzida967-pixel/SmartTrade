"""一键更新模型工作流。

依次执行:
    1. 删掉旧 parquet（可选 --keep-cache 跳过）
    2. 全量重拉行情数据（baostock）
    3. 重建数据集 + 重训模型（默认同时训 lgbm+xgb）
    4. 通知运行中的 FastAPI 服务热重载（可选）

用法:
    python scripts/update.py                       # 全量更新，两个模型都重训
    python scripts/update.py --model lgbm          # 只重训 LightGBM
    python scripts/update.py --model xgb           # 只重训 XGBoost
    python scripts/update.py --keep-cache          # 不删旧 parquet（增量补缺失股票）
    python scripts/update.py --no-reload           # 不调用 FastAPI reload 接口
    python scripts/update.py --reload-url http://...  # 自定义 reload 地址
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from loguru import logger  # noqa: E402

from app.core.config import RAW_DIR, PROCESSED_DIR  # noqa: E402


PYTHON = sys.executable  # 当前 venv 的 python


def step(title: str) -> None:
    bar = "=" * 60
    logger.info(f"\n{bar}\n>>> {title}\n{bar}")


def run_subprocess(cmd: list[str]) -> None:
    """运行子进程，实时输出，失败抛异常。"""
    logger.info(f"$ {' '.join(cmd)}")
    proc = subprocess.run(cmd, cwd=str(ROOT))
    if proc.returncode != 0:
        raise RuntimeError(f"子进程失败 (code={proc.returncode}): {' '.join(cmd)}")


def clear_raw_cache() -> int:
    """清空 raw 目录里所有 parquet。返回清除条数。"""
    if not RAW_DIR.exists():
        return 0
    files = list(RAW_DIR.glob("*.parquet"))
    for f in files:
        f.unlink()
    return len(files)


def clear_processed_cache() -> None:
    """清空 processed 目录的派生数据集。"""
    ds = PROCESSED_DIR / "dataset.parquet"
    if ds.exists():
        ds.unlink()


def reload_fastapi(url: str, retries: int = 2) -> bool:
    """通知 FastAPI 热重载模型。失败不抛异常，返回 bool。"""
    try:
        import httpx  # 局部 import，主流程不强依赖
    except ImportError:
        logger.warning("httpx 未安装，跳过 reload 通知")
        return False

    for attempt in range(1, retries + 1):
        try:
            resp = httpx.post(url, timeout=10.0)
            if resp.status_code == 200:
                logger.info(f"FastAPI 已重载: {resp.json()}")
                return True
            logger.warning(f"reload 返回 {resp.status_code}: {resp.text}")
        except Exception as e:
            logger.warning(f"reload 第 {attempt}/{retries} 次失败: {e}")
            if attempt < retries:
                time.sleep(1.5)
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="一键更新预测模型")
    parser.add_argument("--model", choices=["lgbm", "xgb", "both"], default="both",
                        help="重训哪些模型，默认 both")
    parser.add_argument("--keep-cache", action="store_true",
                        help="不删除旧 parquet（增量补缺失股票）")
    parser.add_argument("--no-reload", action="store_true",
                        help="跳过通知 FastAPI 热重载")
    parser.add_argument("--reload-url", type=str,
                        default="http://127.0.0.1:8001/predict/_reload",
                        help="FastAPI 重载接口地址")
    parser.add_argument("--sleep", type=float, default=0.05,
                        help="拉取数据时单只之间的间隔秒数")
    args = parser.parse_args()

    t0 = time.time()

    # 1. 清缓存
    step("Step 1/3  清理旧缓存")
    if args.keep_cache:
        logger.info("--keep-cache 模式，保留 raw 缓存（仅删除 dataset.parquet）")
        clear_processed_cache()
    else:
        n = clear_raw_cache()
        clear_processed_cache()
        logger.info(f"已清除 {n} 个 raw parquet + dataset.parquet")

    # 2. 拉数据
    step("Step 2/3  从 baostock 拉取最新行情")
    run_subprocess([PYTHON, "scripts/fetch_data.py", "--sleep", str(args.sleep)])

    # 3. 重训模型
    step(f"Step 3/3  重建数据集 + 训练模型 (--model {args.model})")
    run_subprocess([PYTHON, "scripts/train.py", "--rebuild-dataset", "--model", args.model])

    elapsed = time.time() - t0
    logger.info(f"\n✅ 模型更新完成，总耗时 {elapsed/60:.1f} 分钟")

    # 4. 可选：通知 FastAPI 热重载
    if not args.no_reload:
        step("通知 FastAPI 热重载新模型")
        ok = reload_fastapi(args.reload_url)
        if not ok:
            logger.warning(
                "热重载失败（可能 FastAPI 未在运行）。"
                "下次启动 uvicorn 时会自动加载新模型。"
            )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

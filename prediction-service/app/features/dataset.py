"""数据集构建：遍历 data/raw 下所有股票的 parquet，构造完整训练集。

输出：
    data/processed/dataset.parquet  —— 含特征 + 标签，长表格式（每行一只股票一日）
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd
from loguru import logger

from app.core.config import PROCESSED_DIR, RAW_DIR
from app.features.builder import (
    FEATURE_COLUMNS,
    build_features_with_label,
)


DATASET_PATH = PROCESSED_DIR / "dataset.parquet"


def build_dataset(min_rows: int = 80) -> pd.DataFrame:
    """遍历 raw 目录构造全池数据集。

    Args:
        min_rows: 单只股票最少行数（特征需要 ~60 日 warmup）

    Returns:
        包含 [date, code, *FEATURE_COLUMNS, future_ret, label] 的长表
    """
    files = sorted(RAW_DIR.glob("*.parquet"))
    if not files:
        raise FileNotFoundError(f"{RAW_DIR} 为空，先运行 fetch_data.py")

    logger.info(f"发现 {len(files)} 只股票数据")
    frames: list[pd.DataFrame] = []
    skipped = 0

    for i, fp in enumerate(files, 1):
        try:
            raw = pd.read_parquet(fp)
            if len(raw) < min_rows:
                skipped += 1
                continue
            feat = build_features_with_label(raw)
            keep_cols = ["date", "code", *FEATURE_COLUMNS, "future_ret", "label"]
            feat = feat[keep_cols]
            frames.append(feat)
        except Exception as e:
            logger.warning(f"[{fp.name}] 处理失败: {e}")
            skipped += 1

        if i % 50 == 0:
            logger.info(f"已处理 {i}/{len(files)}")

    if not frames:
        raise RuntimeError("没有有效数据，请检查 raw 目录")

    df = pd.concat(frames, ignore_index=True)
    logger.info(f"合并后总行数: {len(df):,}，跳过 {skipped} 只")
    return df


def save_dataset(df: pd.DataFrame, path: Path = DATASET_PATH) -> Path:
    df.to_parquet(path, index=False)
    logger.info(f"数据集已保存: {path} ({path.stat().st_size / 1024 / 1024:.1f} MB)")
    return path


def load_dataset(path: Path = DATASET_PATH) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"{path} 不存在，先 build_dataset")
    return pd.read_parquet(path)


def filter_for_training(df: pd.DataFrame) -> pd.DataFrame:
    """过滤掉特征/标签缺失的行，得到可训练的纯净集。"""
    before = len(df)
    df = df.dropna(subset=["label"])
    df = df.dropna(subset=FEATURE_COLUMNS, how="any")
    logger.info(f"训练集过滤: {before:,} → {len(df):,} 行")
    return df.reset_index(drop=True)


def time_split(df: pd.DataFrame, val_days: int = 60, test_days: int = 60):
    """按时间切分 train / val / test，避免未来数据泄漏。"""
    df = df.sort_values("date").reset_index(drop=True)
    max_date = df["date"].max()
    test_start = max_date - pd.Timedelta(days=test_days)
    val_start = test_start - pd.Timedelta(days=val_days)

    train = df[df["date"] < val_start]
    val = df[(df["date"] >= val_start) & (df["date"] < test_start)]
    test = df[df["date"] >= test_start]
    logger.info(
        f"时间切分: train {len(train):,} ({train['date'].min().date()}~{train['date'].max().date()})"
        f"  val {len(val):,}  test {len(test):,}"
    )
    return train, val, test


__all__ = [
    "DATASET_PATH",
    "build_dataset",
    "save_dataset",
    "load_dataset",
    "filter_for_training",
    "time_split",
]

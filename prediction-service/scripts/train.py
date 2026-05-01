"""离线训练入口。

流程:
    1. 从 data/raw 构造数据集（特征+标签），保存到 data/processed/dataset.parquet
    2. 时间切分 train / val / test
    3. 训练 LightGBM
    4. 在 test 集评估
    5. 保存模型到 models/lgbm_v1.pkl

用法:
    python scripts/train.py
    python scripts/train.py --rebuild-dataset    # 强制重建数据集
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from loguru import logger  # noqa: E402

from app.features.dataset import (  # noqa: E402
    DATASET_PATH,
    build_dataset,
    filter_for_training,
    load_dataset,
    save_dataset,
    time_split,
)
from app.models import lgbm_model  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="训练 LightGBM 三分类模型")
    parser.add_argument("--rebuild-dataset", action="store_true",
                        help="即使 dataset.parquet 已存在也强制重建")
    parser.add_argument("--val-days", type=int, default=60, help="验证集天数")
    parser.add_argument("--test-days", type=int, default=60, help="测试集天数")
    args = parser.parse_args()

    # 1. 数据集
    if args.rebuild_dataset or not DATASET_PATH.exists():
        logger.info("=== 构造数据集 ===")
        df = build_dataset()
        save_dataset(df)
    else:
        logger.info(f"加载已有数据集: {DATASET_PATH}")
        df = load_dataset()

    df = filter_for_training(df)

    # 2. 时间切分
    logger.info("=== 时间切分 ===")
    train_df, val_df, test_df = time_split(df, val_days=args.val_days, test_days=args.test_days)
    if len(val_df) == 0 or len(test_df) == 0:
        logger.error("验证集或测试集为空，请检查数据时间范围")
        return 1

    # 3. 训练
    logger.info("=== 训练 LightGBM ===")
    result = lgbm_model.train(train_df, val_df)

    # 4. 测试集评估
    logger.info("=== 测试集评估 ===")
    X_test = test_df[result.feature_names].astype("float32")
    y_test = test_df["label"].astype(int)
    test_metrics = lgbm_model.evaluate(result.model, X_test, y_test, prefix="test")
    result.metrics["test"] = test_metrics

    # 5. 保存
    lgbm_model.save(result)

    # 6. 特征重要性 Top 15
    logger.info("=== 特征重要性 Top 15 ===")
    print(result.feature_importance.head(15).to_string(index=False))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

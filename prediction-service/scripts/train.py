"""离线训练入口。

流程:
    1. 从 data/raw 构造数据集（特征+标签），保存到 data/processed/dataset.parquet
    2. 时间切分 train / val / test
    3. 训练模型（LightGBM 或 XGBoost，可同时训两个）
    4. 在 test 集评估
    5. 保存模型到 models/{model}_v1.pkl

用法:
    python scripts/train.py                       # 默认 LightGBM
    python scripts/train.py --model xgb           # 只训 XGBoost
    python scripts/train.py --model both          # 两个都训，便于横向对比
    python scripts/train.py --rebuild-dataset     # 强制重建数据集
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
from app.models import lgbm_model, lstm_model, xgb_model  # noqa: E402


MODEL_REGISTRY = {
    "lgbm": ("LightGBM", lgbm_model),
    "xgb":  ("XGBoost",  xgb_model),
    "lstm": ("LSTM",     lstm_model),
}


def main() -> int:
    parser = argparse.ArgumentParser(description="训练股票涨跌三分类模型")
    parser.add_argument("--model", choices=["lgbm", "xgb", "lstm", "both", "all"], default="lgbm",
                        help="选择训练模型: lgbm / xgb / lstm / both(树模型) / all(全部)")
    parser.add_argument("--rebuild-dataset", action="store_true",
                        help="即使 dataset.parquet 已存在也强制重建")
    parser.add_argument("--val-days", type=int, default=60, help="验证集天数（LSTM 默认自动扩大为 120）")
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

    # 3-5. 逐个训练指定模型
    if args.model == "all":
        targets = ["lgbm", "xgb", "lstm"]
    elif args.model == "both":
        targets = ["lgbm", "xgb"]
    else:
        targets = [args.model]
    summary: dict[str, dict] = {}
    for key in targets:
        name, mod = MODEL_REGISTRY[key]
        # LSTM 使用更大的验证集，树模型用手动指定的 val_days
        _val_days = 120 if getattr(mod, "SEQUENCE_BASED", False) and args.val_days == 60 else args.val_days
        logger.info(f"=== 时间切分 (val_days={_val_days}) ===")
        train_df, val_df, test_df = time_split(df, val_days=_val_days, test_days=args.test_days)
        if len(val_df) == 0 or len(test_df) == 0:
            logger.error("验证集或测试集为空，请检查数据时间范围")
            return 1
        logger.info(f"=== 训练 {name} ===")
        result = mod.train(train_df, val_df)

        logger.info(f"=== {name} 测试集评估 ===")
        if getattr(mod, "SEQUENCE_BASED", False):
            from app.models.lstm_model import _build_sequences
            X_test_seq, y_test_seq = _build_sequences(test_df, result.feature_names, mod.SEQ_LEN)
            import numpy as _np
            n = X_test_seq.shape[0]
            f = X_test_seq.shape[2]
            X_test_seq = result.scaler.transform(X_test_seq.reshape(-1, f)).reshape(n, mod.SEQ_LEN, f).astype("float32")
            test_metrics = mod.evaluate(result.model, X_test_seq, y_test_seq, prefix=f"{key}/test")
        else:
            X_test = test_df[result.feature_names].astype("float32")
            y_test = test_df["label"].astype(int)
            test_metrics = mod.evaluate(result.model, X_test, y_test, prefix=f"{key}/test")
        result.metrics["test"] = test_metrics

        mod.save(result)

        logger.info(f"=== {name} 特征重要性 Top 15 ===")
        print(result.feature_importance.head(15).to_string(index=False))

        summary[key] = {
            "name": name,
            "val_macro_f1": result.metrics.get("macro_f1"),
            "test_macro_f1": test_metrics["macro_f1"],
            "test_accuracy": test_metrics["accuracy"],
        }

    if len(summary) > 1:
        logger.info("=== 模型横向对比（测试集）===")
        for key, m in summary.items():
            logger.info(
                f"  {m['name']:<10} acc={m['test_accuracy']:.4f}  macro-F1={m['test_macro_f1']:.4f}"
            )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""XGBoost 三分类模型：训练、评估、保存、加载、预测。

接口与 ``lgbm_model`` 保持一致，便于上层切换。
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import xgboost as xgb
from loguru import logger
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
)
from sklearn.utils.class_weight import compute_sample_weight

from app.core.config import MODELS_DIR
from app.features.builder import FEATURE_COLUMNS


MODEL_PATH = MODELS_DIR / "xgb_v1.pkl"

LABEL_NAMES = {0: "看多", 1: "震荡", 2: "看空"}


@dataclass
class TrainResult:
    model: xgb.XGBClassifier
    feature_names: list[str]
    metrics: dict[str, Any]
    feature_importance: pd.DataFrame


def _default_params() -> dict:
    """与 LightGBM 配置在量级上对齐，便于横向对比。"""
    return dict(
        objective="multi:softprob",
        num_class=3,
        eval_metric="mlogloss",
        learning_rate=0.05,
        n_estimators=600,
        max_depth=6,
        min_child_weight=5,
        subsample=0.85,
        colsample_bytree=0.85,
        reg_alpha=0.1,
        reg_lambda=0.2,
        gamma=0.0,
        tree_method="hist",
        random_state=42,
        n_jobs=-1,
        verbosity=0,
    )


def train(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    feature_names: list[str] | None = None,
    params: dict | None = None,
    early_stopping_rounds: int = 50,
) -> TrainResult:
    """训练 XGBoost 三分类模型，返回结果。"""
    feature_names = feature_names or FEATURE_COLUMNS
    params = {**_default_params(), **(params or {})}
    # XGBClassifier 在 sklearn 接口里通过构造参数指定早停
    params.setdefault("early_stopping_rounds", early_stopping_rounds)

    X_tr = train_df[feature_names].astype("float32")
    y_tr = train_df["label"].astype(int)
    X_val = val_df[feature_names].astype("float32")
    y_val = val_df["label"].astype(int)

    logger.info(f"训练集: {X_tr.shape}  验证集: {X_val.shape}")
    logger.info(f"训练集标签分布: {y_tr.value_counts(normalize=True).round(3).to_dict()}")

    # XGBoost 没有 class_weight 参数，使用 sample_weight 实现 balanced 效果
    sample_weight = compute_sample_weight(class_weight="balanced", y=y_tr)

    model = xgb.XGBClassifier(**params)
    model.fit(
        X_tr, y_tr,
        sample_weight=sample_weight,
        eval_set=[(X_val, y_val)],
        verbose=50,
    )

    metrics = evaluate(model, X_val, y_val, prefix="val")
    importance = pd.DataFrame({
        "feature": feature_names,
        "importance": model.feature_importances_,
    }).sort_values("importance", ascending=False).reset_index(drop=True)

    return TrainResult(
        model=model,
        feature_names=feature_names,
        metrics=metrics,
        feature_importance=importance,
    )


def evaluate(
    model: xgb.XGBClassifier,
    X: pd.DataFrame,
    y: pd.Series,
    prefix: str = "test",
) -> dict[str, Any]:
    """评估模型：accuracy / macro-f1 / 混淆矩阵 / 各类报告。"""
    pred = model.predict(X)
    proba = model.predict_proba(X)
    acc = accuracy_score(y, pred)
    f1m = f1_score(y, pred, average="macro")
    cm = confusion_matrix(y, pred, labels=[0, 1, 2])
    report = classification_report(
        y, pred, labels=[0, 1, 2],
        target_names=[LABEL_NAMES[i] for i in (0, 1, 2)],
        digits=3, zero_division=0,
    )

    logger.info(f"[{prefix}] accuracy={acc:.4f}  macro-F1={f1m:.4f}")
    logger.info(f"[{prefix}] 混淆矩阵 (行=真实, 列=预测) labels=[0看多,1震荡,2看空]\n{cm}")
    logger.info(f"[{prefix}] 分类报告\n{report}")

    return {
        "accuracy": float(acc),
        "macro_f1": float(f1m),
        "confusion_matrix": cm.tolist(),
        "report": report,
        "n_samples": int(len(y)),
        "proba_mean": proba.mean(axis=0).tolist(),
    }


def save(result: TrainResult, path: Path = MODEL_PATH) -> Path:
    payload = {
        "model": result.model,
        "feature_names": result.feature_names,
        "metrics": result.metrics,
        "feature_importance": result.feature_importance.to_dict(orient="records"),
    }
    joblib.dump(payload, path)
    logger.info(f"模型已保存: {path}")
    return path


def load(path: Path = MODEL_PATH) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"{path} 不存在，先 train")
    return joblib.load(path)


def predict_proba(model: xgb.XGBClassifier, X: pd.DataFrame, feature_names: list[str]) -> np.ndarray:
    """对齐特征顺序后预测三分类概率。"""
    X = X[feature_names].astype("float32")
    return model.predict_proba(X)


__all__ = [
    "MODEL_PATH",
    "LABEL_NAMES",
    "TrainResult",
    "train",
    "evaluate",
    "save",
    "load",
    "predict_proba",
]

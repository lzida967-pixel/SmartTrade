"""推理服务：模型加载 + 单只 / 批量预测。"""
from __future__ import annotations

from datetime import date
from typing import Any

import numpy as np
import pandas as pd
from loguru import logger

from app.core.config import settings
from app.data.realtime import fetch_recent
from app.features.builder import FEATURE_COLUMNS, build_features
from app.models import lgbm_model
from app.schemas import (
    BatchPredictionItem,
    BatchPredictionResponse,
    FeatureContrib,
    PredictionResponse,
)


# 模型缓存（进程级单例，启动时加载一次）
_MODEL_CACHE: dict[str, Any] | None = None
_MODEL_VERSION: str = "lgbm_v1"


def load_model() -> dict[str, Any]:
    """加载或返回已缓存的模型 payload。"""
    global _MODEL_CACHE
    if _MODEL_CACHE is None:
        logger.info("加载 LightGBM 模型...")
        _MODEL_CACHE = lgbm_model.load()
        logger.info(f"模型加载完成: features={len(_MODEL_CACHE['feature_names'])}")
    return _MODEL_CACHE


def reload_model() -> None:
    """强制重载（如换了模型文件）。"""
    global _MODEL_CACHE
    _MODEL_CACHE = None
    load_model()


def predict_one(code: str) -> PredictionResponse:
    """对单只股票做一次推理。"""
    payload = load_model()
    model = payload["model"]
    feature_names: list[str] = payload["feature_names"]
    importance_records: list[dict] = payload["feature_importance"]

    raw = fetch_recent(code, days=120)
    if raw is None or len(raw) < 60:
        raise ValueError(f"股票 {code} 数据不足或无法获取（至少需要 60 个交易日）")

    feat = build_features(raw)
    feat = feat.dropna(subset=feature_names)
    if feat.empty:
        raise ValueError(f"股票 {code} 特征构造后全部为 NaN，可能停牌或数据异常")

    last_row = feat.iloc[[-1]]
    X = last_row[feature_names].astype("float32")

    proba = model.predict_proba(X)[0]
    label = int(np.argmax(proba))
    confidence = float(proba[label])

    # Top 8 重要特征 + 当前值
    top_n = 8
    top_records = importance_records[:top_n]
    feat_values = last_row.iloc[0]
    contribs = [
        FeatureContrib(
            name=r["feature"],
            value=float(feat_values[r["feature"]]) if not pd.isna(feat_values[r["feature"]]) else 0.0,
            importance=int(r["importance"]),
        )
        for r in top_records
    ]

    label_name_map = lgbm_model.LABEL_NAMES
    return PredictionResponse(
        code=code.zfill(6),
        as_of_date=last_row["date"].iloc[0].date() if hasattr(last_row["date"].iloc[0], "date") else date.today(),
        forward_days=settings.forward_days,
        threshold=settings.label_threshold,
        proba={
            "bullish": float(proba[0]),
            "neutral": float(proba[1]),
            "bearish": float(proba[2]),
        },
        label=label,
        label_name=label_name_map[label],
        confidence=confidence,
        top_features=contribs,
        model_version=_MODEL_VERSION,
    )


def predict_batch(codes: list[str]) -> BatchPredictionResponse:
    """批量预测，单只失败不影响其他。"""
    items: list[BatchPredictionItem] = []
    success = 0
    for code in codes:
        try:
            r = predict_one(code)
            items.append(BatchPredictionItem(code=code, success=True, result=r))
            success += 1
        except Exception as e:
            items.append(BatchPredictionItem(code=code, success=False, error=str(e)))
    return BatchPredictionResponse(
        total=len(codes),
        success=success,
        failed=len(codes) - success,
        items=items,
    )


__all__ = [
    "load_model",
    "reload_model",
    "predict_one",
    "predict_batch",
]

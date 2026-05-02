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
from app.models import lgbm_model, xgb_model
from app.schemas import (
    BatchPredictionItem,
    BatchPredictionResponse,
    FeatureContrib,
    PredictionResponse,
)


# 模型注册表：key -> (人读名、模块、版本字符串）
MODEL_REGISTRY: dict[str, tuple[str, Any, str]] = {
    "lgbm": ("LightGBM", lgbm_model, "lgbm_v1"),
    "xgb":  ("XGBoost",  xgb_model,  "xgb_v1"),
}
DEFAULT_MODEL = "lgbm"

# 进程级多模型缓存：key -> payload
_MODEL_CACHE: dict[str, dict[str, Any]] = {}


def _resolve(model_key: str | None) -> tuple[str, Any, str]:
    key = (model_key or DEFAULT_MODEL).lower()
    if key not in MODEL_REGISTRY:
        raise ValueError(
            f"不支持的模型: {model_key}。可选: {list(MODEL_REGISTRY.keys())}"
        )
    return MODEL_REGISTRY[key]


def load_model(model_key: str | None = None) -> dict[str, Any]:
    """加载或返回已缓存的模型 payload。"""
    name, mod, _ = _resolve(model_key)
    key = (model_key or DEFAULT_MODEL).lower()
    if key not in _MODEL_CACHE:
        logger.info(f"加载 {name} 模型...")
        _MODEL_CACHE[key] = mod.load()
        logger.info(
            f"{name} 加载完成: features={len(_MODEL_CACHE[key]['feature_names'])}"
        )
    return _MODEL_CACHE[key]


def reload_model(model_key: str | None = None) -> None:
    """强制重载指定模型；model_key=None 表示重载全部已缓存的模型。"""
    global _MODEL_CACHE
    if model_key is None:
        keys = list(_MODEL_CACHE.keys()) or [DEFAULT_MODEL]
        _MODEL_CACHE = {}
        for k in keys:
            load_model(k)
        return
    key = model_key.lower()
    _MODEL_CACHE.pop(key, None)
    load_model(key)


def predict_one(code: str, model_key: str | None = None) -> PredictionResponse:
    """对单只股票做一次推理。"""
    name, mod, version = _resolve(model_key)
    payload = load_model(model_key)
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
            importance=float(r["importance"]),
        )
        for r in top_records
    ]

    label_name_map = mod.LABEL_NAMES
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
        model_version=version,
    )


def predict_batch(codes: list[str], model_key: str | None = None) -> BatchPredictionResponse:
    """批量预测，单只失败不影响其他。"""
    items: list[BatchPredictionItem] = []
    success = 0
    for code in codes:
        try:
            r = predict_one(code, model_key=model_key)
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

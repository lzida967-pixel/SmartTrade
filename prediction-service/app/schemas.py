"""Pydantic 入参/出参模型。"""
from __future__ import annotations

from datetime import date as Date
from typing import Optional

from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    code: str = Field(..., description="6 位股票代码", examples=["600519"])


class FeatureContrib(BaseModel):
    name: str
    value: float
    importance: float


class PredictionResponse(BaseModel):
    code: str
    as_of_date: Date = Field(..., description="使用的最新交易日")
    forward_days: int = Field(..., description="预测未来 N 个交易日")
    threshold: float = Field(..., description="三分类阈值（如 0.03 = ±3%）")
    proba: dict[str, float] = Field(..., description="三类概率: bullish/neutral/bearish")
    label: int = Field(..., description="预测标签 0=看多 1=震荡 2=看空")
    label_name: str
    confidence: float = Field(..., description="预测类的概率")
    top_features: list[FeatureContrib] = Field(..., description="特征重要性 Top N")
    model_version: str
    disclaimer: str = (
        "本预测基于历史数据机器学习模型，仅供学习研究使用，不构成任何投资建议。"
    )


class BatchPredictionRequest(BaseModel):
    codes: list[str] = Field(..., min_length=1, max_length=50)


class BatchPredictionItem(BaseModel):
    code: str
    success: bool
    result: Optional[PredictionResponse] = None
    error: Optional[str] = None


class BatchPredictionResponse(BaseModel):
    total: int
    success: int
    failed: int
    items: list[BatchPredictionItem]


__all__ = [
    "PredictionRequest",
    "PredictionResponse",
    "BatchPredictionRequest",
    "BatchPredictionResponse",
    "BatchPredictionItem",
    "FeatureContrib",
]

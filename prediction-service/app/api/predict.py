"""预测相关 HTTP 路由。"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from app.schemas import (
    BatchPredictionRequest,
    BatchPredictionResponse,
    PredictionResponse,
)
from app.services.prediction_service import (
    DEFAULT_MODEL,
    MODEL_REGISTRY,
    predict_batch,
    predict_one,
    reload_model,
)


router = APIRouter(prefix="/predict", tags=["prediction"])

MODEL_KEYS = list(MODEL_REGISTRY.keys())


@router.get("/models", summary="列出可用模型")
def list_models():
    return {
        "default": DEFAULT_MODEL,
        "models": [
            {"key": k, "name": v[0], "version": v[2]}
            for k, v in MODEL_REGISTRY.items()
        ],
    }


@router.get("/{code}", response_model=PredictionResponse, summary="单只股票 T+5 三分类预测")
def get_prediction(
    code: str,
    model: str = Query(DEFAULT_MODEL, description=f"模型 key，可选: {MODEL_KEYS}"),
):
    code = code.strip().zfill(6)
    if not code.isdigit() or len(code) != 6:
        raise HTTPException(status_code=400, detail="股票代码必须是 6 位数字")
    try:
        return predict_one(code, model_key=model)
    except ValueError as e:
        # 未知模型走 400，股票数据不足 / 特征异常走 404
        msg = str(e)
        status = 400 if msg.startswith("不支持的模型") else 404
        raise HTTPException(status_code=status, detail=msg)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"内部错误: {e}")


@router.post("/batch", response_model=BatchPredictionResponse, summary="批量预测")
def post_batch(
    req: BatchPredictionRequest,
    model: str = Query(DEFAULT_MODEL, description=f"模型 key，可选: {MODEL_KEYS}"),
):
    codes = [c.strip().zfill(6) for c in req.codes if c.strip()]
    return predict_batch(codes, model_key=model)


@router.post("/_reload", summary="重新加载模型（运维接口）")
def reload(
    model: str | None = Query(None, description="指定重载某个模型，不传则重载全部已缓存模型"),
):
    reload_model(model_key=model)
    return {"status": "reloaded", "model": model or "all"}

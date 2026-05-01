"""预测相关 HTTP 路由。"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.schemas import (
    BatchPredictionRequest,
    BatchPredictionResponse,
    PredictionResponse,
)
from app.services.prediction_service import (
    predict_batch,
    predict_one,
    reload_model,
)


router = APIRouter(prefix="/predict", tags=["prediction"])


@router.get("/{code}", response_model=PredictionResponse, summary="单只股票 T+5 三分类预测")
def get_prediction(code: str):
    code = code.strip().zfill(6)
    if not code.isdigit() or len(code) != 6:
        raise HTTPException(status_code=400, detail="股票代码必须是 6 位数字")
    try:
        return predict_one(code)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"内部错误: {e}")


@router.post("/batch", response_model=BatchPredictionResponse, summary="批量预测")
def post_batch(req: BatchPredictionRequest):
    codes = [c.strip().zfill(6) for c in req.codes if c.strip()]
    return predict_batch(codes)


@router.post("/_reload", summary="重新加载模型（运维接口）")
def reload():
    reload_model()
    return {"status": "reloaded"}

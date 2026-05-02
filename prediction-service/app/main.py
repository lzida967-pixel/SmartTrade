"""FastAPI 入口。启动时预加载 LightGBM 模型，挂载 /predict/* 路由。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from app.api.predict import router as predict_router
from app.core.config import settings
from app.services.prediction_service import MODEL_REGISTRY, load_model


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 尝试预热所有已注册模型；任意一个缺失不阻断服务启动
    for key in MODEL_REGISTRY:
        try:
            load_model(key)
        except FileNotFoundError as e:
            logger.warning(f"[{key}] 模型未找到，该模型接口将不可用: {e}")
        except Exception as e:
            logger.error(f"[{key}] 模型加载失败: {e}")
    yield


app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="A 股 T+5 涨跌幅三分类预测服务",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(predict_router)


@app.get("/", tags=["meta"])
def root():
    return {
        "service": settings.app_name,
        "version": app.version,
        "status": "ok",
    }


@app.get("/health", tags=["meta"])
def health():
    return {"status": "UP"}

"""PyTorch LSTM 三分类模型：训练、评估、保存、加载、推理。

与 lgbm_model / xgb_model 接口对齐：
  - load()  -> dict  (model, scaler, feature_names, feature_importance)
  - train() -> TrainResult
  - evaluate()
  - save()
  - infer(payload, feat_df) -> np.ndarray  [bullish, neutral, bearish] 概率

关键差异：
  - SEQUENCE_BASED = True        —— prediction_service 据此走序列推理路径
  - 需要 seq_len 个交易日的特征序列（默认 30）
  - 使用 StandardScaler 归一化特征
  - 特征重要性：梯度显著图（gradient saliency）在验证集上平均绝对梯度
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from loguru import logger
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
)
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, TensorDataset

from app.core.config import MODELS_DIR
from app.features.builder import FEATURE_COLUMNS


# ──────────────────────────────────────────────
# 超参 / 常量
# ──────────────────────────────────────────────
SEQ_LEN: int = 30          # 输入序列长度（交易日数）
HIDDEN_SIZE: int = 64
NUM_LAYERS: int = 2
DROPOUT: float = 0.4
BATCH_SIZE: int = 1024
MAX_EPOCHS: int = 80
PATIENCE: int = 15         # early stopping
LR: float = 3e-4

MODEL_PATH = MODELS_DIR / "lstm_v1.pkl"
LABEL_NAMES = {0: "看多", 1: "震荡", 2: "看空"}
SEQUENCE_BASED: bool = True  # prediction_service.py 据此分流到序列推理


# ──────────────────────────────────────────────
# 模型定义
# ──────────────────────────────────────────────
class LSTMClassifier(nn.Module):
    """双层 LSTM + Dropout + 全连接，输出三类 logits。"""

    def __init__(
        self,
        input_size: int,
        hidden_size: int = HIDDEN_SIZE,
        num_layers: int = NUM_LAYERS,
        num_classes: int = 3,
        dropout: float = DROPOUT,
    ):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size, hidden_size, num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )
        self.dropout = nn.Dropout(dropout)
        self.fc = nn.Linear(hidden_size, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """x: (batch, seq_len, input_size) -> logits: (batch, num_classes)"""
        out, _ = self.lstm(x)
        out = self.dropout(out[:, -1, :])  # 取最后时间步的隐状态
        return self.fc(out)


# ──────────────────────────────────────────────
# 数据辅助
# ──────────────────────────────────────────────
def _build_sequences(
    df: pd.DataFrame,
    feature_cols: list[str],
    seq_len: int,
) -> tuple[np.ndarray, np.ndarray]:
    """按股票代码分组，滑窗构造序列数据集。

    Returns:
        X: (N, seq_len, F)  float32
        y: (N,)             int64
    """
    Xs: list[np.ndarray] = []
    ys: list[int] = []
    for _, gdf in df.groupby("code"):
        gdf = gdf.sort_values("date").reset_index(drop=True)
        feats = gdf[feature_cols].values.astype("float32")
        labels = gdf["label"].values.astype(np.int64)
        n = len(gdf)
        if n <= seq_len:
            continue
        for i in range(seq_len, n):
            Xs.append(feats[i - seq_len: i])
            ys.append(labels[i])
    if not Xs:
        raise RuntimeError("序列数据集为空，请检查数据量是否充足")
    return np.stack(Xs, axis=0), np.array(ys, dtype=np.int64)


def _class_weights(y: np.ndarray, num_classes: int = 3) -> torch.Tensor:
    """计算逆频率类别权重，缓解三类不均衡问题。"""
    counts = np.bincount(y, minlength=num_classes).astype(float)
    counts = np.where(counts == 0, 1, counts)
    weights = 1.0 / counts
    weights /= weights.sum()
    return torch.tensor(weights, dtype=torch.float32)


# ──────────────────────────────────────────────
# 训练 / 评估
# ──────────────────────────────────────────────
@dataclass
class TrainResult:
    model: LSTMClassifier
    scaler: StandardScaler
    feature_names: list[str]
    metrics: dict[str, Any]
    feature_importance: pd.DataFrame


def train(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    feature_names: list[str] | None = None,
    params: dict | None = None,
) -> TrainResult:
    """训练 LSTM 三分类模型。

    Args:
        train_df / val_df: 含 [date, code, *FEATURE_COLUMNS, label] 的长表。
        feature_names:     特征列名，默认 FEATURE_COLUMNS。
        params:            可覆盖超参（hidden_size / num_layers / dropout /
                           batch_size / max_epochs / patience / lr）。
    """
    feature_names = feature_names or FEATURE_COLUMNS
    p = {
        "hidden_size": HIDDEN_SIZE,
        "num_layers":  NUM_LAYERS,
        "dropout":     DROPOUT,
        "batch_size":  BATCH_SIZE,
        "max_epochs":  MAX_EPOCHS,
        "patience":    PATIENCE,
        "lr":          LR,
        **(params or {}),
    }

    logger.info("=== 构造序列数据集 ===")
    X_tr, y_tr = _build_sequences(train_df, feature_names, SEQ_LEN)
    X_val, y_val = _build_sequences(val_df, feature_names, SEQ_LEN)
    logger.info(f"训练序列: {X_tr.shape}  验证序列: {X_val.shape}")

    # 归一化：在特征维度上拟合（reshape 为 2-D）
    scaler = StandardScaler()
    n_tr, s, f = X_tr.shape
    scaler.fit(X_tr.reshape(-1, f))
    X_tr = scaler.transform(X_tr.reshape(-1, f)).reshape(n_tr, s, f).astype("float32")
    n_val = X_val.shape[0]
    X_val = scaler.transform(X_val.reshape(-1, f)).reshape(n_val, s, f).astype("float32")

    # DataLoader
    train_ds = TensorDataset(torch.tensor(X_tr), torch.tensor(y_tr))
    val_ds = TensorDataset(torch.tensor(X_val), torch.tensor(y_val))
    train_loader = DataLoader(train_ds, batch_size=p["batch_size"], shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=p["batch_size"] * 2, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    logger.info(f"设备: {device}")

    model = LSTMClassifier(
        input_size=f,
        hidden_size=p["hidden_size"],
        num_layers=p["num_layers"],
        dropout=p["dropout"],
    ).to(device)

    class_w = _class_weights(y_tr).to(device)
    criterion = nn.CrossEntropyLoss(weight=class_w)
    optimizer = torch.optim.AdamW(model.parameters(), lr=p["lr"], weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", factor=0.5, patience=5, min_lr=1e-5
    )

    best_val_loss = float("inf")
    best_state: dict | None = None
    no_improve = 0

    for epoch in range(1, p["max_epochs"] + 1):
        # Train
        model.train()
        tr_loss = 0.0
        for xb, yb in train_loader:
            xb, yb = xb.to(device), yb.to(device)
            optimizer.zero_grad()
            loss = criterion(model(xb), yb)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            tr_loss += loss.item() * len(xb)
        tr_loss /= len(train_ds)

        # Val
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for xb, yb in val_loader:
                xb, yb = xb.to(device), yb.to(device)
                val_loss += criterion(model(xb), yb).item() * len(xb)
        val_loss /= len(val_ds)
        scheduler.step(val_loss)

        if epoch % 5 == 0 or epoch == 1:
            logger.info(
                f"Epoch {epoch:3d}/{p['max_epochs']}  "
                f"train_loss={tr_loss:.4f}  val_loss={val_loss:.4f}"
            )

        if val_loss < best_val_loss - 1e-5:
            best_val_loss = val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            no_improve = 0
        else:
            no_improve += 1
            if no_improve >= p["patience"]:
                logger.info(f"Early stopping at epoch {epoch}")
                break

    if best_state is not None:
        model.load_state_dict(best_state)
    model.to("cpu")

    metrics = evaluate(model, X_val, y_val, device="cpu", prefix="val")

    # 梯度显著图：以验证集的一个批次计算各特征绝对梯度均值
    importance_df = _gradient_importance(model, X_val[:min(2048, len(X_val))], feature_names)

    return TrainResult(
        model=model,
        scaler=scaler,
        feature_names=feature_names,
        metrics=metrics,
        feature_importance=importance_df,
    )


def _gradient_importance(
    model: LSTMClassifier,
    X: np.ndarray,
    feature_names: list[str],
) -> pd.DataFrame:
    """梯度显著图特征重要性：|∂output/∂input| 在时间和样本维度平均。"""
    model.eval()
    x_t = torch.tensor(X, dtype=torch.float32, requires_grad=True)
    logits = model(x_t)
    # 对所有类 logit 求和，再反向传播
    logits.sum().backward()
    importance = x_t.grad.abs().mean(dim=(0, 1)).detach().numpy()
    df = pd.DataFrame({
        "feature": feature_names,
        "importance": importance,
    }).sort_values("importance", ascending=False).reset_index(drop=True)
    return df


def evaluate(
    model: LSTMClassifier,
    X: np.ndarray,
    y: np.ndarray,
    device: str = "cpu",
    prefix: str = "test",
) -> dict[str, Any]:
    """评估模型：accuracy / macro-f1 / 混淆矩阵 / 各类报告。"""
    model.eval()
    with torch.no_grad():
        x_t = torch.tensor(X, dtype=torch.float32).to(device)
        logits = model(x_t)
        proba = torch.softmax(logits, dim=-1).cpu().numpy()
    pred = proba.argmax(axis=1)

    acc = accuracy_score(y, pred)
    f1m = f1_score(y, pred, average="macro", zero_division=0)
    cm = confusion_matrix(y, pred, labels=[0, 1, 2])
    report = classification_report(
        y, pred, labels=[0, 1, 2],
        target_names=[LABEL_NAMES[i] for i in (0, 1, 2)],
        digits=3, zero_division=0,
    )
    logger.info(f"[{prefix}] accuracy={acc:.4f}  macro-F1={f1m:.4f}")
    logger.info(f"[{prefix}] 混淆矩阵 (行=真实, 列=预测)\n{cm}")
    logger.info(f"[{prefix}] 分类报告\n{report}")
    return {
        "accuracy": float(acc),
        "macro_f1": float(f1m),
        "confusion_matrix": cm.tolist(),
        "report": report,
        "n_samples": int(len(y)),
        "proba_mean": proba.mean(axis=0).tolist(),
    }


# ──────────────────────────────────────────────
# 保存 / 加载
# ──────────────────────────────────────────────
def save(result: TrainResult, path: Path = MODEL_PATH) -> Path:
    payload = {
        "model":              result.model,
        "scaler":             result.scaler,
        "feature_names":      result.feature_names,
        "metrics":            result.metrics,
        "feature_importance": result.feature_importance.to_dict(orient="records"),
        "seq_len":            SEQ_LEN,
    }
    joblib.dump(payload, path)
    logger.info(f"LSTM 模型已保存: {path}")
    return path


def load(path: Path = MODEL_PATH) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"{path} 不存在，先运行 train.py --model lstm")
    return joblib.load(path)


# ──────────────────────────────────────────────
# 序列推理接口（供 prediction_service.py 调用）
# ──────────────────────────────────────────────
def infer(payload: dict, feat_df: pd.DataFrame) -> np.ndarray:
    """从特征 DataFrame 提取最近 seq_len 行，归一化后推理。

    Returns:
        proba: np.ndarray shape (3,)  [bullish, neutral, bearish]
    """
    model: LSTMClassifier = payload["model"]
    scaler: StandardScaler = payload["scaler"]
    feature_names: list[str] = payload["feature_names"]
    seq_len: int = payload.get("seq_len", SEQ_LEN)

    seq = feat_df[feature_names].tail(seq_len).values.astype("float32")
    if len(seq) < seq_len:
        raise ValueError(
            f"特征行数 {len(seq)} 不足序列长度 {seq_len}，"
            "股票历史数据可能不足 60 + 30 个交易日"
        )
    seq_scaled = scaler.transform(seq).astype("float32")
    x = torch.tensor(seq_scaled[np.newaxis], dtype=torch.float32)  # (1, seq_len, F)

    model.eval()
    with torch.no_grad():
        logits = model(x)
        proba = torch.softmax(logits, dim=-1).cpu().numpy()[0]

    return proba


__all__ = [
    "SEQ_LEN",
    "SEQUENCE_BASED",
    "MODEL_PATH",
    "LABEL_NAMES",
    "LSTMClassifier",
    "TrainResult",
    "train",
    "evaluate",
    "save",
    "load",
    "infer",
]

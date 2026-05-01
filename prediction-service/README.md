# SmartTrade 股票预测服务 (prediction-service)

基于 LightGBM 的 A 股 T+5 涨跌幅三分类预测服务，独立 Python FastAPI 微服务，由 Spring Boot 后端通过 HTTP 调用。

## 技术栈

- **Web**: FastAPI + Uvicorn
- **数据**: akshare（A 股历史行情）+ pandas + parquet 缓存
- **特征**: ta（技术指标库） + 自定义量价/横截面特征
- **模型**: LightGBM 三分类
- **服务**: REST API（同步） + 离线训练脚本

## 预测目标

- **目标**：T+5 收盘相对今日涨跌幅
- **三分类标签**：
  - `0` 看多（未来 5 日涨幅 > +3%）
  - `1` 震荡（-3% ~ +3%）
  - `2` 看空（跌幅 > -3%）
- **股票池**：沪深 300 + 项目自选 102 只

## 目录结构

```
prediction-service/
├── requirements.txt
├── README.md
├── .gitignore
├── app/
│   ├── __init__.py
│   ├── main.py                # FastAPI 入口
│   ├── core/
│   │   ├── __init__.py
│   │   └── config.py          # 配置（路径、端口等）
│   ├── data/
│   │   ├── __init__.py
│   │   └── loader.py          # akshare 拉数据 + parquet 缓存
│   ├── features/
│   │   └── __init__.py        # 后续 P2 实现
│   ├── models/
│   │   └── __init__.py        # 后续 P2 实现
│   ├── api/
│   │   └── __init__.py        # 后续 P4 实现
│   └── schemas.py             # Pydantic 模型
├── scripts/
│   └── fetch_data.py          # 一次性数据拉取脚本
├── data/
│   ├── raw/                   # 原始 K 线 parquet（gitignore）
│   └── processed/             # 特征 parquet（gitignore）
└── models/
    └── (训练后产物，gitignore)
```

## 快速开始

### 1. 创建虚拟环境（推荐 Python 3.10+）

```powershell
cd prediction-service
python -m venv .venv
.venv\Scripts\Activate.ps1
```

### 2. 安装依赖

```powershell
pip install -r requirements.txt
```

> 国内可加镜像：`pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple`

### 3. P1：拉取历史 K 线（先验证 akshare 通畅）

```powershell
# 默认拉取沪深 300 + 项目自选股近 3 年日 K，落到 data/raw/
python scripts/fetch_data.py
```

成功后会在 `data/raw/` 下生成 `<股票代码>.parquet` 文件。

### 4. 启动 FastAPI 服务（P4 后才会有真实预测接口）

```powershell
uvicorn app.main:app --reload --port 8001
```

访问 http://localhost:8001/docs 查看 API。

## 路线图

- [x] P1 骨架 + akshare 数据拉取
- [ ] P2 特征工程 + 三分类标签 + LightGBM 训练
- [ ] P3 模型评估 + 简易回测
- [ ] P4 FastAPI `/predict/{code}` 推理接口
- [ ] P5 Spring Boot 网关接入
- [ ] P6 Vue 预测页
- [ ] P7 收盘后定时增量训练

## 免责声明

本服务输出**仅供学习研究使用**，不构成任何投资建议。模型基于历史数据训练，不能保证未来表现。

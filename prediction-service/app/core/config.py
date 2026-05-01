"""全局配置：路径、参数、股票池等。"""
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = PROJECT_ROOT / "data"
RAW_DIR = DATA_DIR / "raw"
PROCESSED_DIR = DATA_DIR / "processed"
MODELS_DIR = PROJECT_ROOT / "models"

for _d in (RAW_DIR, PROCESSED_DIR, MODELS_DIR):
    _d.mkdir(parents=True, exist_ok=True)


class Settings(BaseSettings):
    """运行时设置，可通过环境变量或 .env 覆盖。"""

    # 服务
    app_name: str = "SmartTrade Prediction Service"
    api_port: int = 8001

    # 数据
    history_years: int = 3                  # 历史 K 线长度（年）
    forward_days: int = 5                   # 预测周期 T+N
    label_threshold: float = 0.03           # 三分类阈值 ±3%

    # 股票池：项目库里已有的 102 只 + 沪深 300（脚本里取并集）
    use_csi300: bool = True
    project_stocks_file: str = "project_stocks.txt"

    model_config = SettingsConfigDict(
        env_file=str(PROJECT_ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()

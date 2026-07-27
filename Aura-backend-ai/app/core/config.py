from urllib.parse import quote_plus

from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    """应用配置类，自动从 .env 文件和环境变量加载配置"""

    # ==================== 服务基础配置 ====================
    APP_NAME: str = "Aura AI Service"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = True
    HOST: str = "0.0.0.0"
    PORT: int = 8002

    # ==================== MySQL 数据库配置 ====================
    MYSQL_HOST: str = "localhost"
    MYSQL_PORT: int = 3306
    MYSQL_USER: str = "root"
    MYSQL_PASSWORD: str = ""
    MYSQL_DATABASE: str = "aura"

    @property
    def MYSQL_URL(self) -> str:
        encoded_pwd = quote_plus(self.MYSQL_PASSWORD)
        return (
            f"mysql+asyncmy://{self.MYSQL_USER}:{encoded_pwd}"
            f"@{self.MYSQL_HOST}:{self.MYSQL_PORT}/{self.MYSQL_DATABASE}?charset=utf8mb4"
        )

    # ==================== Milvus 配置 ====================
    MILVUS_URI: str = "http://localhost:19530"
    MILVUS_TOKEN: str = "root:Milvus"  # 本地默认认证
    MILVUS_DB_NAME: str = "default"  # 默认数据库
    MILVUS_DIMENSION: int = 2560  # 默认向量维度

    # ==================== RAG 配置 ====================
    DOCUMENT_ROOT: str = "../Aura-backend-server/docs"
    SPLIT_CHUNK_SIZE: int = 512  # 每块最大字符数
    SPLIT_CHUNK_OVERLAP: int = 64  # 相邻两块重叠字符数
    EMBEDDING_MODEL: str #暂时只支持ollama本地部署的模型

    # ==================== Pydantic 配置加载规则 ====================
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )


# 创建全局配置单例
settings = Settings()
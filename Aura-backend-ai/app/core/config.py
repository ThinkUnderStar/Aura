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
    MILVUS_DB_NAME: str = "aura"  # 默认数据库
    MILVUS_DIMENSION: int = 2560  # 默认向量维度

    # ==================== PostgreSql 配置 ====================
    POSTGRES_URL: str = "postgresql://postgres:密码@localhost:5432/aura"

    # ==================== RAG 配置 ====================
    DOCUMENT_ROOT: str = "../Aura-backend-server/docs"
    SPLIT_CHUNK_SIZE: int = 512  # 每块最大字符数
    SPLIT_CHUNK_OVERLAP: int = 64  # 相邻两块重叠字符数
    EMBEDDING_MODEL: str = "qwen3-embedding:4b" #嵌入式模型
    MULTI_QUERY_MODEL: str = "llama3.2:3b" #多重查询模型
    RERANKER_MODEL: str = "BAAI/bge-reranker-v2-m3" #精排序模型
    EXTRACTOR_MODEL: str = "qwen2.5:3b" #摘要模型

    #====================用户交互 LLM========================
    CHAT_MODEL_NAME: str #OpenAI系列的模型名
    CHAT_MODEL_API_KEY: str #对应模型的API密钥
    CHAT_MODEL_BASE_URL: str #对应模型的API地址
    CHAT_MODEL_TEMPERATURE: float #模型对应的随机性控制参数

    # ==================== ComfyUI 配置 ====================
    COMFYUI_HOST: str = "http://localhost:8188"
    COMFYUI_WORKFLOW: str = "workflow.json"
    COMFYUI_SAVE_PATH: str ="../Aura-backend-server/docs/temp_images"

    # ==================== tavily 配置 ======================
    TAVILY_API_KEY: str

    # ==================== Pydantic 配置加载规则 ====================
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

# 创建全局配置单例
settings = Settings()
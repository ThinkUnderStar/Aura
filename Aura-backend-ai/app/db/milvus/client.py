from pymilvus import AsyncMilvusClient

from app.core.config import settings

milvus_client = AsyncMilvusClient(
    uri=settings.MILVUS_URI,
    token=settings.MILVUS_TOKEN,
    db_name=settings.MILVUS_DB_NAME
)

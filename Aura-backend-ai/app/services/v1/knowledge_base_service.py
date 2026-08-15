from pymilvus import FieldSchema, DataType, CollectionSchema
from pymilvus.milvus_client import IndexParams

from app.core.config import settings
from app.db.milvus.client import milvus_client
from app.models.response import Result


async def create_knowledge_base_service(kb_name: str) -> Result[None]:
    """
    创建知识库
    :param kb_name: milvus collection name
    :return: 创建的结果
    """
    try:
        if await milvus_client.has_collection(kb_name):
           return Result.success(msg=f"知识库{kb_name}已存在")

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=4096),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=settings.MILVUS_DIMENSION),
            FieldSchema(name="document_id", dtype=DataType.INT64),
            FieldSchema(name="document_name", dtype=DataType.VARCHAR, max_length=255),
            FieldSchema(name="metadata", dtype=DataType.VARCHAR, max_length=2048),  # JSON 字符串
        ]
        schema = CollectionSchema(fields, description="知识库文档向量集合")

        await milvus_client.create_collection(
            collection_name=kb_name,
            schema=schema
        )

        # 创建索引
        index_params = IndexParams()
        index_params.add_index(
            field_name="vector",
            index_type="IVF_FLAT",
            metric_type="COSINE",
            params={"nlist": 128},
        )

        await milvus_client.create_index(
            collection_name=kb_name,
            index_params=index_params
        )

        return Result.success(msg=f"知识库{kb_name}创建成功")
    except Exception as e:
        return Result.error(msg="创建知识库失败")

async def delete_knowledge_base_service(kb_name: str) -> Result[None]:
    """
    删除知识库
    :param kb_name: milvus collection name
    :return: 删除的结果
    """
    try:
        if not await milvus_client.has_collection(kb_name):
           return Result.success(msg=f"知识库{kb_name}不存在")

        await milvus_client.drop_collection(collection_name=kb_name)

        return Result.success(msg=f"知识库{kb_name}删除成功")
    except Exception as e:
        return Result.error(msg=f"删除知识库{kb_name}失败")
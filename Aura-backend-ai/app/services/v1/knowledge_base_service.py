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

        await milvus_client.create_collection(
            collection_name=kb_name,
            dimension=settings.MILVUS_DIMENSION,
            primary_field_name="id",
            vector_field_name="vector",
            metric_type="COSINE",
            auto_id=True,
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
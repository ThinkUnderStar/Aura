from typing import List

from app.db.milvus.client import milvus_client


async def vector_store(embed_documents: List[dict], kb_name: str) -> None:
    """
    将向量化后的坐标存入向量知识库
    :param kb_name: 向量知识库名称
    :param embed_documents: 向量化后的坐标
    """
    await milvus_client.insert(collection_name=kb_name,data=embed_documents)
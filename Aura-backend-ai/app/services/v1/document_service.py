import logging
from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.milvus.client import milvus_client
from app.db.mysql.entities import KnowledgeBaseEntity, DocumentEntity
from app.models.response import Result
from app.services.rag.document_processor import document_processor
from app.services.rag.embedding import embedding_docs
from app.services.rag.vector_store import vector_store


async def upload_document_service(kb_id: int,doc_id: int ,db: AsyncSession) -> Result[None]:
    """
    上传文档到指定知识库
    :param db: mysql数据库会话
    :param kb_id:  知识库id
    :param doc_id:  文档id
    :return: 上传结果
    """
    try:
        query_kb = select(KnowledgeBaseEntity).where(KnowledgeBaseEntity.id == kb_id)
        kb:Optional[KnowledgeBaseEntity] = (await db.execute(query_kb)).scalar_one_or_none()
        query_doc = select(DocumentEntity).where(DocumentEntity.id == doc_id)
        doc:Optional[DocumentEntity] = (await db.execute(query_doc)).scalar_one_or_none()

        kb_name = f"aura_kb_{kb_id}_team" if kb.is_team == 1 else f"aura_kb_{kb_id}_personal"
        file_path = f"{settings.DOCUMENT_ROOT}{doc.file_path}"

        #上传文档指向量知识库中
        documents = await document_processor(file_path)
        embed_documents = await embedding_docs(documents, doc)
        await vector_store(embed_documents, kb_name)

        return Result.success(msg="文档上传知识库成功")

    except Exception as e:
        logging.exception(f"文档上传知识库失败 kb_id={kb_id} doc_id={doc_id}: {e}")
        return Result.error(msg=f"上传知识库失败: {str(e)}", code=500)

async def delete_document_service(kb_id: int,doc_id: int,db: AsyncSession) -> Result[None]:
    """
    删除知识库中的文档
    :param db: mysql数据库会话
    :param kb_id:  知识库id
    :param doc_id:  文档id
    :return: 删除结果
    """
    try:
        query_kb = select(KnowledgeBaseEntity).where(KnowledgeBaseEntity.id == kb_id)
        kb:Optional[KnowledgeBaseEntity] = (await db.execute(query_kb)).scalar_one_or_none()
        kb_name = f"aura_kb_{kb_id}_team" if kb.is_team == 1 else f"aura_kb_{kb_id}_personal"

        await milvus_client.delete(
            collection_name=kb_name,
            filter=f"document_id=={doc_id}"
        )

        return Result.success(msg="从知识库中删除该文档成功")

    except Exception as e:
        return Result.error(msg=f"从知识库删除该文档失败: {str(e)}", code=500)

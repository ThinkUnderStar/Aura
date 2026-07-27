from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.mysql.entities import KnowledgeBaseEntity, DocumentEntity
from app.models.response import Result


async def upload_document(kb_id: int,doc_id: int ,db: AsyncSession) -> Result[None]:
    """
    上传文档到指定知识库
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



    except Exception as e:
        return Result.error(msg="上传知识库失败")
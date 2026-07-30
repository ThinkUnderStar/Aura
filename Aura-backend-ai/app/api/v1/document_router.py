from fastapi import APIRouter, Depends
from fastapi import Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.db.mysql.session import get_db
from app.models.response import Result
from app.services.v1.document_service import upload_document_service, delete_document_service

doc_router = APIRouter(prefix="/document", tags=["Milvus"])

@doc_router.post("/upload")
async def upload_document(
        kb_id: int = Query(...,description="上传的知识库id"),
        doc_id: int = Query(...,description="上传的文档id"),
        db: AsyncSession = Depends(get_db)
) -> Result[None]:
    """
    上传文档到指定知识库
    :param db: mysql数据库会话
    :param kb_id:  知识库id
    :param doc_id:  文档id
    :return: 上传结果
    """
    return await upload_document_service(kb_id,doc_id,db)

@doc_router.delete("/delete")
async def delete_document(
        kb_id: int = Query(...,description="知识库id"),
        doc_id: int = Query(...,description="删除的文档id"),
        db: AsyncSession = Depends(get_db)
) -> Result[None]:
    """
    删除知识库中的文档
    :param db: mysql数据库会话
    :param kb_id:  知识库id
    :param doc_id:  文档id
    :return: 删除结果
    """
    return await delete_document_service(kb_id,doc_id,db)
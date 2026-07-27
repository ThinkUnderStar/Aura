from fastapi import APIRouter, Depends
from fastapi.params import Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.db.mysql.session import get_db
from app.models.response import Result

doc_router = APIRouter(prefix="/document", tags=["Milvus"])

@doc_router.post("/upload")
async def upload_document(
        kb_id: int = Query(...,description="上传的知识库id"),
        doc_id: int = Query(...,description="上传的文档id"),
        db: AsyncSession = Depends(get_db)
) -> Result[None]:
    """
    上传文档到指定知识库
    :param kb_id:  知识库id
    :param doc_id:  文档id
    :return: 上传结果
    """
    pass

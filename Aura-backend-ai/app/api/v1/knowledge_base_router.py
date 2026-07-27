from fastapi import APIRouter
from fastapi.params import Path
from app.models.response import Result
from app.services.v1.knowledge_base_service import create_knowledge_base_service, delete_knowledge_base_service

kb_router = APIRouter(prefix="/kb", tags=["Milvus"])

@kb_router.post("/create/{kb_name}")
async def create_knowledge_base(kb_name: str = Path(...,description="要创建知识库对应的collection名")) -> Result[None]:
    """
    创建知识库
    :param kb_name: milvus collection name
    :return: 创建的结果
    """
    return await create_knowledge_base_service(kb_name)

@kb_router.delete("/delete/{kb_name}")
async def delete_knowledge_base(kb_name: str = Path(...,description="要删除知识库对应的collection名")) -> Result[None]:
    """
    删除知识库
    :param kb_name: milvus collection name
    :return: 删除的结果
    """
    return await delete_knowledge_base_service(kb_name)
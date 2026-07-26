from fastapi import APIRouter
from fastapi.params import Path

from app.db.milvus.client import milvus_client

kb_router = APIRouter(prefix="/kb", tags=["Milvus"])

@kb_router.post("/create")
async def create_knowledge_base(kb_id: int = Path(...,description="知识库ID")):
    pass
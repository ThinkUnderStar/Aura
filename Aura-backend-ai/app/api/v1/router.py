from fastapi import APIRouter

from app.api.v1.document_router import doc_router
from app.api.v1.knowledge_base_router import kb_router

api_v1_router = APIRouter()

# 挂在所有路由
api_v1_router.include_router(kb_router)
api_v1_router.include_router(doc_router)


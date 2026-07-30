from fastapi import APIRouter
from fastapi import Body

from app.models.request import PromptDto
from app.models.response import Result
from app.services.v1.avatar_service import generate_avatar_service

avatar_router = APIRouter(prefix="/avatar",tags=["comfyUI"])

@avatar_router.post("/generate")
async def generate_avatar(prompt: PromptDto = Body(...,description="生成头像的提示词")) -> Result[str]:
    """
    AI生成头像
    :param prompt: 生成头像的提示词
    :return: 生成结果和头像文件名
    """
    return await generate_avatar_service(prompt)



import random
import uuid
from datetime import datetime

from app.core.comfy_ui import comfy_ui_client
from app.core.config import settings
from app.models.request import PromptDto
from app.models.response import Result


async def generate_avatar_service(prompt: PromptDto) -> Result[str]:
    """
    AI生成头像
    :param prompt: 生成头像的提示词
    :return: 生成结果和头像文件名
    """
    try:
        #连接ComfyUI服务
        await comfy_ui_client.connect()

        #填入提示词
        await comfy_ui_client.set_data(
            key='CLIPTextEncode',
            text=prompt.prompt
        )

        #填入随机种子
        await comfy_ui_client.set_data(
            key='KSampler',
            seed=random.randint(1, 10 ** 9)
        )

        #生成图片
        results = await comfy_ui_client.generate(["SaveImage"])
        if not results:
            raise RuntimeError("ComfyUI 未返回图片")

        #获取图像信息
        image = list(results.values())[0]

        #设置图像保存路径
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        unique_id = uuid.uuid4().hex[:8]
        filename = f"{timestamp}_{unique_id}.png"

        #保存图片
        save_path = settings.COMFYUI_SAVE_PATH + "/" + filename
        image.save(save_path, format="PNG")

        return Result.success(data=filename)

    except Exception as e:
        return Result.error(msg=f"生成头像失败: {str(e)}", code=500)

    finally:
        #关闭与ComfyUI服务的连接
        await comfy_ui_client.close()
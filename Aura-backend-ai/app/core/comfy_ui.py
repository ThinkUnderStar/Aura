from comfyuiclient import ComfyUIClientAsync

from app.core.config import settings

comfy_ui_client = ComfyUIClientAsync(
    server=settings.COMFYUI_HOST,
    prompt_file="./configs/workflows/"+settings.COMFYUI_WORKFLOW
)

from fastapi import APIRouter, Path, Body
from starlette.responses import StreamingResponse

from app.models.request import ChatDto
from app.services.v1.chat_service import chat_with_agent_service

chat_router = APIRouter(prefix="/chat",tags=["agent"])

@chat_router.post("/send/{agent_id}")
async def chat_with_agent(
        agent_id: int = Path(...,description="用户选择对话的agent的ID"),
        chat_dto: ChatDto = Body(...,description="用户发给该agent的对话，和是否开启联网搜索"),
) -> StreamingResponse:
    """
    与指定 Agent 进行流式对话（SSE 实时推送）

    该端点接收用户消息，调用 Python 端的 LangGraph Agent 进行流式推理，
    并实时将生成的文本片段通过 Server-Sent Events (SSE) 推送给前端。
    支持打字机效果、中断确认、错误处理等。

    Args:
        agent_id: Agent ID，同时也是会话 ID，用于标识唯一的对话线程。
        chat_dto: 包含用户消息和可选的配置项（如是否启用联网搜索等）。

    Returns:
        StreamingResponse: 使用 `text/event-stream` 格式的 SSE 流式响应，
        前端可通过 EventSource API 监听并实时渲染。

    Raises:
        HTTPException:
            - 404: Agent 不存在或已删除。
            - 403: 用户无权操作该 Agent（权限校验）。
            - 429: 请求频率超限（限流）。
            - 500: 服务内部错误（如 LangGraph 调用失败）。

    Notes:
        - 该接口需登录访问（SaToken 拦截）。
        - 前端应监听以下事件类型：
            - `text`: 文本片段，直接追加显示。
            - `interrupt`: 需要用户确认（如保存记忆），展示对话框。
            - `done`: 对话结束，关闭连接。
            - `error`: 错误信息，展示提示。
        - 中断确认后，前端需调用 `/resume` 接口继续对话。
    """
    event_stream = chat_with_agent_service(agent_id,chat_dto)
    return StreamingResponse(
        event_stream,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )




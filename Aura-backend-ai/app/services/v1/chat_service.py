import json
from typing import AsyncGenerator

from langchain_core.messages import HumanMessage

from app.models.request import ChatDto
from app.services.agent.graph import graph, aura_agent


async def chat_with_agent_service(agent_id: int,chat_dto: ChatDto) -> AsyncGenerator[str,None]:
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
    config = {
        "user_id": "aura-user-"+str(chat_dto.user_id),
        "thread_id": "aura-thread-"+str(agent_id),
        "agent_id": agent_id,
        "knowledge_bases": chat_dto.knowledge_bases
    }

    # 创建异步生成器
    astream = aura_agent.astream_events(
        input=HumanMessage(content=chat_dto.human_content),
        config=config,
        version="v3",
    )

    async for event in astream:
        if event["event"] == "on_chat_model_stream":
            content = event["data"]["chunk"]["content"]

            if content != "":
                yield f"data: {content}\n\n"

        if event["event"] == "interrupt":
            value = event["data"]["value"]
            value_json = json.dumps(value)

            yield f"event: interrupt\ndata: {value_json}\n\n"

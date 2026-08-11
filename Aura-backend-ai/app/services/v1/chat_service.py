import json
from typing import AsyncGenerator

from langchain_core.messages import HumanMessage
from langchain_core.runnables import RunnableConfig
from langgraph.types import Command

from app.db.postgresql.connect import checkpoint
from app.models.request import ChatDto, ToolAllowDto
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
    snapshot = await checkpoint.aget(RunnableConfig(
        configurable={"thread_id": "aura-thread-"+str(agent_id)}
    ))

    if snapshot:
        checkpoint_id = snapshot.config["configurable"]["checkpoint_id"]
    else:
        # 处理没有找到对应 checkpoint 的情况
        checkpoint_id = None

    config = {
        "user_id": "aura-user-"+str(chat_dto.user_id),
        "thread_id": "aura-thread-"+str(agent_id),
        "agent_id": agent_id,
        "knowledge_bases": chat_dto.knowledge_bases,
        "from_checkpoint_id": checkpoint_id,
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


async def tool_allow_service(
        tool_allow_dto: ToolAllowDto
) -> AsyncGenerator[str, None]:
    """
    处理工具调用中断的恢复逻辑，返回异步生成器。

    该函数在用户确认或拒绝工具调用中断时被调用，它会：
    1. 根据 `user_id` 和 `agent_id` 构建用于恢复图执行的 `config` 配置。
    2. 根据 `choice` 和 `edition` 构建 `resume_value` 字典。
    3. 调用 `agent.ainvoke(Command(resume=resume_value), config)` 恢复中断的图。
    4. 使用 `astream_events` 流式获取图执行过程中的事件，并通过 `yield` 逐块产出原始数据。

    该生成器产生的数据**不包含 SSE 格式包装**（如 `data:` 前缀或 `\n\n` 分隔），
    而是直接产出图执行过程中产生的原始内容块（如文本片段、中断事件 JSON 等）。
    由上层路由函数负责将其包装为符合 SSE 协议的 `StreamingResponse`。

    Args:
        tool_allow_dto (ToolAllowDto): 包含以下字段的 DTO：
            - `user_id` (int): 用户 ID，用于构建 `user_id` 配置。
            - `agent_id` (int): Agent ID，同时也是 `thread_id` 的一部分，用于状态恢复。
            - `choice` (str): 用户的选择，可选值：
                - `"approve"`：同意工具调用。
                - `"reject"`：拒绝工具调用。
                - `"edit"`：编辑后同意，此时 `edition` 字段必须提供。
            - `edition` (str, 可选)：当 `choice` 为 `"edit"` 时，用户编辑后的新内容。

    Yields:
        str: 图执行过程中产出的原始数据块（文本片段或事件 JSON 字符串），
             由上层路由函数包装为 SSE 格式后发送给前端。

    Raises:
        ValueError: 当 `choice` 为 `"edit"` 但 `edition` 为空或 None 时。
        HTTPException: 当用户无权操作该 Agent 或 Agent 不存在时（由上层路由处理）。
        Exception: LangGraph 执行过程中的其他异常。

    Note:
        - 恢复执行时，`config` 中只需包含 `user_id`、`thread_id`（格式为 `"aura-thread-{agent_id}"`）和 `agent_id`。
        - 该函数假设 `tool_allow_dto` 中 `choice` 字段已被前端正确校验，且 `edition` 在必要时已提供。
        - 该生成器产生的内容块与 `chat_with_agent_service` 的输出格式一致，
          可直接交由同一个 SSE 包装函数处理。
        - 该函数返回的是异步生成器，由 FastAPI 的 `StreamingResponse` 驱动消费。
    """
    config = {
        "user_id": "aura-user-" + str(tool_allow_dto.user_id),
        "thread_id": "aura-thread-" + str(tool_allow_dto.agent_id),
        "agent_id": tool_allow_dto.agent_id,
    }

    astream = aura_agent.astream_events(
        Command(
            resume = {
                "choice": tool_allow_dto.choice,
                "edit": tool_allow_dto.edition
            },
        ),
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


from fastapi import APIRouter, Path, Body
from starlette.responses import StreamingResponse

from app.models.request import ChatDto, ToolAllowDto, UpdateMessageDto
from app.models.response import Result
from app.services.v1.chat_service import chat_with_agent_service, tool_allow_service, clear_session_message_service, \
    update_message_service

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


@chat_router.post("/tool_allow")
async def tool_allow(
        tool_allow_dto: ToolAllowDto = Body(..., description="工具调用中断续接参数")
) -> StreamingResponse:
    """
    用户确认或拒绝工具调用中断，继续执行 LangGraph 图。

    当 Agent 在对话过程中触发 `interrupt`（例如询问是否保存用户级记忆），
    前端会展示确认对话框，用户做出选择后，调用此接口将选择结果传回后端，
    使中断的图从暂停处恢复执行，并继续流式返回 AI 响应。

    **核心流程**：
    1. 接收用户确认信息（`choice`）及可选的编辑内容（`edition`）。
    2. 构建 `Command(resume=resume_value)`，其中 `resume_value` 包含 `choice` 和 `edition`。
    3. 使用 `agent.ainvoke(Command(...), config)` 恢复图执行。
    4. 将图执行产生的 SSE 流透传给前端。

    **用户选择类型**：
    - `"approve"`：同意工具调用，继续执行。
    - `"reject"`：拒绝工具调用，图将处理拒绝逻辑。
    - `"edit"`：用户编辑了内容，需同时传入 `edition` 字段（编辑后的新内容）。

    Args:
        tool_allow_dto (ToolAllowDto): 包含用户 ID、Agent ID、用户选择及可选编辑内容。

    Returns:
        StreamingResponse: 以 `text/event-stream` 格式返回恢复后的对话流，
        前端通过 EventSource API 监听并实时渲染。

    Raises:
        HTTPException:
            - 404: Agent 不存在或已删除。
            - 403: 用户无权操作该 Agent。
            - 400: 请求体缺失必填字段（如 `choice` 或 `choice` 为 `"edit"` 时缺少 `edition`）。
            - 500: LangGraph 恢复执行失败（如状态异常、超时等）。

    Note:
        - 该接口需登录访问（SaToken 拦截）。
        - 恢复执行时，`config` 中只需提供 `user_id`、`thread_id`（即 agent_id 字符串）和 `agent_id`。
        - 中断恢复后，后续的 SSE 事件类型与正常对话一致（`text`、`interrupt`、`done` 等）。
        - 如果 `choice` 为 `"edit"`，`edition` 必须为非空字符串，否则抛出异常。
        - 该接口是幂等的：多次相同请求不会产生副作用（图状态由 checkpoint 管理）。
    """
    event_stream = tool_allow_service(tool_allow_dto)
    return StreamingResponse(
        event_stream,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )

@chat_router.delete("/clear/{agent_id}")
async def clear_session_message(
        agent_id: int = Path(...,description="该agent对应的ID")
) -> Result[None]:
    """
    清空指定 Agent 的所有会话记忆（**不可恢复**）。

    该接口会彻底删除该 Agent 在以下存储中的所有对话相关数据：
    - **MySQL**：`messages` 表中该 Agent 的所有消息记录（包括 Human、AI 和工具确认消息）
    - **PostgreSQL**：`checkpoints` 表中该 Agent 的所有状态快照（通过 LangGraph checkpointer）
    - **Milvus**：该 Agent 对应的向量集合 `aura_agent_{agent_id}_session_memory`（若存在则删除）

    执行后，该 Agent 将恢复到初始状态，所有历史对话、图执行状态和向量记忆均无法恢复。
    建议前端在调用前展示二次确认弹窗，防止误操作。

    Args:
        agent_id (int): 要清空记忆的 Agent ID（路径参数）

    Returns:
        Result[None]: 统一封装的成功响应（data 为 None）
            - 成功时返回 `{"code": 200, "message": "Agent 记忆已清除", "data": None}`
            - 失败时返回对应的错误码和消息

    Raises:
        HTTPException:
            - 404: Agent 不存在或无关联数据（但即使无数据，删除操作也是幂等的，不会报错）
            - 500: 数据库操作失败、Milvus 删除失败或服务内部异常

    Note:
        - **幂等性**：该接口是幂等的，重复调用不会产生副作用（已删除的数据再次删除视为成功）。
        - **跨存储一致性**：MySQL、PostgreSQL 和 Milvus 的删除操作不会在同一个事务中，
          但整体删除逻辑是“尽力而为”，部分失败会记录日志并返回错误。
        - **性能**：如果该 Agent 的消息量或向量数据量较大，删除操作可能需要几秒到几十秒，
          建议客户端设置合理的超时时间。
        - **与用户级记忆的关系**：该接口只删除 Agent 级别的会话记忆，不会影响用户级记忆（store 中按 `user_id` 隔离的数据）。
        - **依赖服务**：该接口依赖 PostgreSQL checkpointer 和 Milvus 服务，请确保相关服务正常运行。
    """
    return await clear_session_message_service(agent_id)

@chat_router.put("/update")
async def update_message(
        update_message_dto: UpdateMessageDto = Body(..., description="修改该message要用的信息")
) -> StreamingResponse:
    """
    修改指定消息的内容，并从该消息处重新生成后续对话（SSE 流式响应）。

    **功能说明**：
    此接口用于用户编辑某条历史消息（例如修正错别字、调整问题描述）。
    用户将完整的消息对象（包含消息 ID、内容、`from_checkpoint_id` 等）以及新内容一起传回，
    系统会从该消息对应的 checkpoint 恢复状态，用新内容替换原消息，然后重新执行 LangGraph 图，
    流式返回新的 AI 响应。

    **执行流程**：
    1. 从 `update_message_dto.message` 中获取原消息信息（包括 `message_id`、`from_checkpoint_id`、`agent_id` 等）。
    2. Java 端负责删除该消息之后的所有 MySQL 消息记录（不删除该消息本身）。
    3. Java 端用 `update_message_dto.human_content` 更新该消息的内容。
    4. Java 端构建请求体，调用本接口：
       - 如果 `from_checkpoint_id` 不为 `None`，则从该 checkpoint 恢复图状态。
       - 如果 `from_checkpoint_id` 为 `None`，则先清空该 Agent 的所有记忆（PostgreSQL checkpoints + Milvus 集合），然后以全新状态开始。
    5. Python 端将新内容作为 `HumanMessage` 输入，调用 `astream_events` 生成流式事件。
    6. 将生成的 SSE 流透传给前端。

    **限流策略**：
    - 敏感操作，限流更严格：
      - 突发容量：`5`
      - 平均速率：`0.1` 次/秒（即每 10 秒最多 1 次）

    Args:
        update_message_dto (UpdateMessageDto): 包含以下字段的请求体：
            - `message` (MessageEntity): 原消息完整对象（包含 `id`、`agent_id`、`from_checkpoint_id`、`content` 等）。
            - `human_content` (str): 修改后的新消息内容。
            - `enable_web_search` (int): 是否开启联网搜索（1-开启，0-关闭）。
            - `knowledge_bases` (List[KnowledgeBaseDto]): 知识库列表。

    Returns:
        StreamingResponse: SSE 流（`text/event-stream`），事件类型：
        - `data: <text>`：文本片段
        - `event: interrupt\n data: {...}`：中断确认
        - `event: done`：对话结束

    Raises:
        HTTPException:
            - 400: 请求体缺少必填字段（如 `message` 或 `human_content` 为空）
            - 404: 消息不存在或不属于该 Agent
            - 403: 用户无权修改该消息（需登录且为消息所属用户）
            - 429: 请求频率超限
            - 500: Python 端执行失败或内部异常

    Note:
        - 该操作会**不可逆地删除**该消息之后的所有消息，请前端在调用前务必让用户二次确认。
        - `from_checkpoint_id` 为 `None` 时，会清空该 Agent 的所有记忆（包括 checkpoints 和 Milvus 集合），然后从头开始新对话。
        - 修改成功后，该消息之后的旧对话将无法恢复，新对话从该消息处重新开始。
        - 本接口需登录（SaToken 拦截），未登录返回 401。
        - 修改后，`relevant_user_memories` 节点不会重新执行（因为图从 `llm_node` 恢复），但 `SystemMessage` 中的知识库和记忆信息已在首次对话时注入，不会丢失。
    """
    event_stream = update_message_service(update_message_dto)
    return StreamingResponse(
        event_stream,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )




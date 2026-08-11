from fastapi import APIRouter, Path, Body
from starlette.responses import StreamingResponse

from app.models.request import ChatDto, ToolAllowDto
from app.services.v1.chat_service import chat_with_agent_service, tool_allow_service

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

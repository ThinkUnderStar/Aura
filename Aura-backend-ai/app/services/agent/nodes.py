from langchain_core.messages import ToolMessage
from langgraph.constants import END
from langgraph.types import Command

from app.core.llm import chat_llm
from app.services.agent.graph import State
from app.services.agent.tools import search_knowledge_base


async def llm_node(state:State) -> State:
    """
    调用用户交互大模型
    :param state: 各个节点相互通信的通信类对象
    :return: State
    """
    response = await chat_llm.ainvoke(state.messages)

    if response.tool_calls:
        return Command(
            update={"messages": response},
            goto="run_tool"
        )
    else:
        return Command(
            update={"messages": response},
            goto=END
        )

async def run_tool(state:State) -> State:
    """
       执行工具调用并更新状态。

       该节点负责处理当前状态中待执行的工具调用（tool_calls），
       并行执行所有工具，并将结果封装为 ToolMessage 追加到消息历史中。
       同时，此节点也会从长期记忆（store）中读取用户偏好等上下文信息，
       并将调用过程中产生的错误或日志信息记录到状态中，供后续节点使用。

       Args:
           state (State): 当前图状态，应包含待执行的工具调用列表（如 messages 中的 tool_calls）。

       Returns:
           State: 更新后的状态，主要变更包括：
               - messages: 追加了每个工具执行结果对应的 ToolMessage。
               - tool_results: 结构化存储的工具执行结果列表（可选）。
               - error: 如果执行过程中发生异常，记录错误信息（可选）。

       Example:
           典型的调用流程：
           1. 模型生成包含 tool_calls 的 AIMessage。
           2. 路由到 run_tool 节点。
           3. 该节点并行执行所有工具调用。
           4. 返回包含 ToolMessage 列表的更新状态。
           5. 后续节点（如模型节点）可基于工具结果继续生成回答。
       """
    messages = []
    tool_calls = state.messages[-1].tool_calls

    for tool_call in tool_calls:
        if tool_call["name"] == "search_knowledge_base":
            result = await search_knowledge_base.ainvoke(tool_call["args"])
        else:
            result = "未查询到该工具"

        tool_message = ToolMessage(
            content=result,
            tool_call_id=tool_call["id"]
        )

        messages.append(tool_message)

    return {"messages": messages}



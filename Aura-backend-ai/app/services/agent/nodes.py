from typing import List

from langchain_core.messages import ToolMessage
from langchain_core.runnables import RunnableConfig
from langgraph.config import get_store
from langgraph.constants import END
from langgraph.types import Command, interrupt
from app.services.agent.graph import State
from app.services.agent.llm import chat_llm_with_tools
from app.services.agent.prompts import SYSTEM_PROMPT_TEMPLATE
from app.services.agent.tools import search_knowledge_base, save_user_memory, get_user_memory, search_user_memory, \
    delete_user_memory


async def llm_node(state:State) -> State:
    """
    调用用户交互大模型
    :param state: 各个节点相互通信的通信类对象
    :return: State
    """
    response = await chat_llm_with_tools.ainvoke(state.messages)

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

async def run_tool(state:State,config:RunnableConfig) -> State:
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
    store = get_store()
    messages = []
    tool_calls = state.messages[-1].tool_calls

    for tool_call in tool_calls:
        #增强检索指定知识库
        if tool_call["name"] == "search_knowledge_base":
            result = await search_knowledge_base.ainvoke(tool_call["args"])

        #添加用户级记忆（需用户自己确认）
        elif tool_call["name"] == "save_user_memory":

            user_choice = interrupt({
                "question": f"是否同意将:\n {tool_call["args"]["thing"]} \n添加进用户级记忆？",
                "options": ["approve", "reject", "edit"]
            })

            if user_choice["choice"] == "approve":

                result = await save_user_memory.ainvoke(tool_call["args"])
            elif user_choice["choice"] == "reject":

                result = "用户拒绝将该内容添加进用户级记忆"
            elif user_choice["choice"] == "edit":

                result = await save_user_memory.ainvoke(user_choice["edit"])
            else:

                result = "用户的选择异常，不符合规范"

        #获取用户的所有用户级记忆
        elif tool_call["name"] == "get_user_memory":
            result = await get_user_memory.ainvoke(tool_call["args"])

        #搜索该用户的相关用户级记忆
        elif tool_call["name"] == "search_user_memory":
            result = await search_user_memory.ainvoke(tool_call["args"])

        #删除用户指定的用户级记忆
        elif tool_call["name"] == "delete_user_memory":
            question = "是否同意将以下用户级记忆内容从用户级记忆中删除:\n"
            # 获取当前会话或用户的唯一标识符
            user_id = config.get("configurable", {}).get("user_id", "default_user_id")
            if user_id == "default_user_id":
                result =  "未指定用户ID"
            else:
                user_memory_space = ("users_memory", user_id)

                keys: List[str] = tool_call["args"]["keys"]
                if not keys:
                    result = "未传入要删除用户级记忆的key"
                else:
                    for key in keys:
                        content = ""
                        item = await store.aget(namespace=user_memory_space, key=key)
                        if item is None:
                            content = "该key并不对应任何一个用户记忆"
                        else:
                            content = item.value["data"]
                        question = question + content +"\n"

                    user_choice = interrupt({
                        "question": question,
                        "options": ["approve", "reject"]
                    })

                    if user_choice["choice"] == "reject":
                        result = "用户拒绝删除这些用户级记忆"

                    elif user_choice["choice"] == "approve":
                        result = await delete_user_memory.ainvoke(tool_call["args"])

                    else:
                        result = "用户的选择异常，不符合规范"

        #过滤对不存在工具的调用
        else:
            result = "未查询到该工具"

        tool_message = ToolMessage(
            content=result,
            tool_call_id=tool_call["id"]
        )

        messages.append(tool_message)

    return {"messages": messages}

async def relevant_user_memories(state:State,config:RunnableConfig) -> State:
    """
    从用户级记忆中检索与当前问题最相关的记忆，并注入到消息状态中。

    该节点在每次 LLM 调用之前执行，作为图中的一个前置节点。
    它从当前会话的最后一条 HumanMessage 中提取问题文本，使用语义检索从用户的长期记忆库中
    查找最相关的记忆片段，然后将这些记忆格式化为 SystemMessage，放置到消息列表的最前面，
    供后续的 LLM 节点参考。

    该节点是“动态记忆注入”的核心实现，确保 LLM 在每次回答时都能感知到与当前问题
    最相关的用户历史偏好或事实，同时避免将无关记忆全部塞入上下文。

    Args:
        state (State): 当前图状态，应包含 `messages` 列表，其中至少有一条 HumanMessage。
        config (RunnableConfig): 运行时配置，必须包含 `configurable.user_id` 或 `thread_id`，
            用于确定当前用户的记忆命名空间。

    Returns:
        dict: 更新后的状态字典，主要修改 `messages` 字段：
            - 如果存在 SystemMessage，将其替换为包含相关记忆的新 SystemMessage。
            - 否则，在列表最前插入新 SystemMessage。

    Example:
        假设用户消息为 "我喜欢简洁的回答"，该节点会检索到之前存储的
        “用户偏好简洁风格”等记忆，并注入到 system prompt 中，
        使得后续 LLM 回答时自动采用简洁风格。

    注意：
        - 该节点依赖 `store` 支持向量语义搜索（如 AsyncPostgresStore）。
        - 如果存储不支持或检索失败，会返回空记忆提示，不影响主流程。
        - 仅检索与当前问题最相关的 5 条记忆，以控制上下文长度。
    """
    question = state.messages[-1].content
    store = get_store()
    memories = ""

    user_id = config.get("configurable", {}).get("user_id", "default_user_id")
    if user_id == "default_user_id":
        memories = "未指定用户ID"

    else:
        user_memory_space = ("users_memory",user_id)
        results = await store.asearch(user_memory_space,query=str(question),limit=5)

        if not results:
            memories = "无相关用户级记忆"
        else:
            for result in results:
                memories = memories + result.value["data"] + "\n"

    system_prompt = await SYSTEM_PROMPT_TEMPLATE.ainvoke({"memories": memories})
    system_message = state.messages[0]
    system_message.content = system_prompt
    return {"messages": [system_message]}

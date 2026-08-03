from langgraph.constants import END
from langgraph.types import Command

from app.core.llm import chat_llm
from app.services.agent.graph import State


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


from typing import List, Annotated
from langchain_core.messages import BaseMessage
from langchain_core.runnables import RunnableConfig
from langgraph.constants import START, END
from langgraph.graph import StateGraph, add_messages
from pydantic.v1 import BaseModel
from app.db.postgresql.connect import checkpoint, store
from app.services.agent.nodes import llm_node, run_tool, relevant_user_memories, summarization_node, \
    save_human_session_memory, save_ai_session_memory, sensitive_content_handler


#创建节点间的通讯类
class State(BaseModel):
    messages: Annotated[List[BaseMessage],add_messages]

#创建分支函数
def has_sensitive_words(state: State,config: RunnableConfig) -> str:
    """
    判断用户的问题是否设计敏感话题
    :param config: 自动注入，获取传入的config
    :param state: 通信类
    :return: 前往的节点
    """
    is_sensitive = config.get("configurable", {}).get("is_sensitive", 0)
    if is_sensitive == 1:
        return "sensitive_content_handler"
    else:
        return "summarization_node"


#创建图
graph = StateGraph(State)

#添加节点
graph.add_node("relevant_user_memories",relevant_user_memories)
graph.add_node("save_human_session_memory",save_human_session_memory)
graph.add_node("sensitive_content_handler",sensitive_content_handler)
graph.add_node("summarization_node",summarization_node)
graph.add_node("llm_node",llm_node)
graph.add_node("run_tool",run_tool)
graph.add_node("save_ai_session_memory",save_ai_session_memory)

#添加边
graph.add_edge(START,"relevant_user_memories")
graph.add_edge("relevant_user_memories","save_human_session_memory")
graph.add_conditional_edges("save_human_session_memory",has_sensitive_words)
graph.add_edge("summarization_node","llm_node")
graph.add_edge("run_tool","summarization_node")
graph.add_edge("sensitive_content_handler","save_ai_session_memory")
graph.add_edge("save_ai_session_memory",END)

#编译
aura_agent = graph.compile(
    checkpointer=checkpoint,
    store=store,
)


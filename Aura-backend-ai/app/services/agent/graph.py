from typing import List, Annotated
from langchain_core.messages import BaseMessage
from langgraph.constants import START, END
from langgraph.graph import StateGraph, add_messages
from pydantic.v1 import BaseModel
from app.db.postgresql.connect import checkpoint, store
from app.services.agent.nodes import llm_node, run_tool, relevant_user_memories, summarization_node, \
    save_human_session_memory, save_ai_session_memory


#创建节点间的通讯类
class State(BaseModel):
    messages: Annotated[List[BaseMessage],add_messages]

#创建图
graph = StateGraph(State)

#添加节点
graph.add_node("relevant_user_memories",relevant_user_memories)
graph.add_node("save_human_session_memory",save_human_session_memory)
graph.add_node("summarization_node",summarization_node)
graph.add_node("llm_node",llm_node)
graph.add_node("run_tool",run_tool)
graph.add_node("save_ai_session_memory",save_ai_session_memory)

#添加边
graph.add_edge(START,"relevant_user_memories")
graph.add_edge("relevant_user_memories","save_human_session_memory")
graph.add_edge("save_human_session_memory","summarization_node")
graph.add_edge("summarization_node","llm_node")
graph.add_edge("run_tool","summarization_node")
graph.add_edge("save_ai_session_memory",END)

#编译
aura_agent = graph.compile(
    checkpointer=checkpoint,
    store=store,
)


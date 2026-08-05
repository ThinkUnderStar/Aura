from typing import List, Annotated

from langchain_core.messages import BaseMessage
from langgraph.constants import START
from langgraph.graph import StateGraph, add_messages
from pydantic.v1 import BaseModel

from app.db.postgresql.connect import checkpoint, store
from app.services.agent.nodes import llm_node, run_tool


#创建节点间的通讯类
class State(BaseModel):
    messages: Annotated[List[BaseMessage],add_messages]

#创建图
graph = StateGraph(State)

#添加节点
graph.add_node(llm_node,"llm_node")
graph.add_node(run_tool,"run_tool")

#添加边
graph.add_edge(START,"llm_node")
graph.add_edge("run_tool","llm_node")

#编译
agent = graph.compile(
    checkpointer=checkpoint,
    store=store,
)


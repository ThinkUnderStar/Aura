from typing import List

from langchain_core.messages import BaseMessage
from langgraph.graph import StateGraph
from pydantic.v1 import BaseModel


#创建节点间的通讯类
class State(BaseModel):
    messages: List[BaseMessage]


graph = StateGraph(State)

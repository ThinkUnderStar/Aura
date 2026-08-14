#创建节点间的通讯类
from typing import Annotated, List

from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages
from pydantic import BaseModel


class State(BaseModel):
    messages: Annotated[List[BaseMessage],add_messages]
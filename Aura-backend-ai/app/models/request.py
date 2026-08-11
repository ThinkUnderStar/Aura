from typing import List

from pydantic import BaseModel


class PromptDto(BaseModel):
    prompt: str

class KnowledgeBaseDto(BaseModel):
    collection_name: str
    description: str

class ChatDto(BaseModel):
    user_id: int
    human_content: str
    # 1-开启联网搜索 0-关闭联网搜搜
    enable_web_search: int
    knowledge_bases: List[KnowledgeBaseDto]

class ToolAllowDto(BaseModel):
    user_id: int
    agent_id: int
    choice: str
    edition: str



from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel

class MessageSchema(BaseModel):
    """消息数据传输对象（Pydantic 模型）"""

    id: int
    agent_id: int
    role: str
    content: Optional[str] = None
    from_checkpoint_id: Optional[str] = None
    action: Optional[str] = None
    edited_content: Optional[str] = None
    version: int
    create_time: datetime

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
    is_sensitive: int

class ToolAllowDto(BaseModel):
    user_id: int
    agent_id: int
    choice: str
    edition: str
    enable_web_search: int

class UpdateMessageDto(ChatDto):
    message: MessageSchema



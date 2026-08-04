from typing import List

from langchain_core.tools import tool

@tool
async def search_knowledge_base(question:str,kb_ids:List[int]) -> str:
    pass
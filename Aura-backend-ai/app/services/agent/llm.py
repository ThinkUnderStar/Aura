from app.core.llm import chat_llm
from app.services.agent.tools import search_knowledge_base, save_user_memory, get_user_memory, search_user_memory, \
    delete_user_memory

chat_llm_with_tools = chat_llm.bind_tools([
    search_knowledge_base,
    save_user_memory,
    get_user_memory,
    search_user_memory,
    delete_user_memory,
])

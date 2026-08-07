from tavily import AsyncTavilyClient
from app.core.config import settings

tavily_client = AsyncTavilyClient(api_key=settings.TAVILY_API_KEY)
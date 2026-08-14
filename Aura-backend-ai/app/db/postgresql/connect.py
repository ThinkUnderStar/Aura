from contextlib import asynccontextmanager
from typing import AsyncContextManager

from fastapi import FastAPI
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.graph.state import CompiledStateGraph
from langgraph.store.postgres import AsyncPostgresStore

from app.core.config import settings
from app.services.agent.graph import graph

checkpoint: AsyncPostgresSaver  = None
store: AsyncPostgresStore  = None
aura_agent: CompiledStateGraph = None  # 新增

@asynccontextmanager
async def postgresql_connect(app: FastAPI) -> AsyncContextManager:
    global checkpoint
    global store
    global aura_agent

    async with (
        AsyncPostgresSaver.from_conn_string(settings.POSTGRES_URL) as saver,
        AsyncPostgresStore.from_conn_string(settings.POSTGRES_URL) as s
    ):
        await saver.setup()
        await s.setup()

        checkpoint = saver
        store = s

        # 编译
        aura_agent = graph.compile(
            checkpointer=checkpoint,
            store=store,
        )

        yield #卡住连接状态，服务关闭时继续进行并终止连接


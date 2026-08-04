from contextlib import asynccontextmanager
from typing import AsyncContextManager

from fastapi import FastAPI
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.store.postgres import AsyncPostgresStore

from app.core.config import settings

checkpoint = None
store = None

@asynccontextmanager
async def postgresql_connect(app: FastAPI) -> AsyncContextManager:
    global checkpoint
    global store

    async with (
        AsyncPostgresSaver.from_conn_string(settings.POSTGRES_URL) as saver,
        AsyncPostgresStore.from_conn_string(settings.POSTGRES_URL) as s
    ):
        await saver.setup()
        await s.setup()

        checkpoint = saver
        store = s

        yield #卡住连接状态，服务关闭时继续进行并终止连接

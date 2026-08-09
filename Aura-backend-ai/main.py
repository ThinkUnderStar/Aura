from fastapi import FastAPI
import app.core.logging
from app.api.v1.router import api_v1_router
from app.db.mysql.session import Base, async_engine
from app.db.postgresql.connect import postgresql_connect

app = FastAPI(lifespan=postgresql_connect)

#项目开始时初始化
@app.on_event("startup")
async def init_database():
    async with async_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("✅ 数据库表检查完成")

#挂载v1的接口路由
app.include_router(api_v1_router, prefix="/api/v1")
from fastapi import APIRouter, Query, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.mysql.session import get_db
from app.models.response import Result
from app.services.v1 import agent_service

agent_router = APIRouter(prefix="/agent",tags=["Agent"])

@agent_router.delete("/delete")
async def delete_user_agents_all_memory(
        user_id: int = Query(...,description="用户ID"),
        db: AsyncSession = Depends(get_db)
) -> Result[str]:
    """
    删除指定用户下所有 Agent 的持久化记忆（**不可恢复**）。

    该接口会遍历该用户下关联的所有 Agent，并分别清理以下三部分数据：
    1. **清理 PostgreSQL 检查点**：删除该用户所有 Agent 在 `checkpoints` 表中对应的对话状态快照。
    2. **清理 PostgreSQL 用户级记忆**：删除 `store` 表中命名空间为 `("users_memory", user_id)` 的用户级全局记忆。
    3. **清理 Milvus 向量集合**：删除 Milvus 中名为 `aura_agent_{agent_id}_session_memory` 的会话历史向量集合。

    此操作会彻底清除该用户的历史交互状态与记忆，一经执行**无法撤销**。

    Args:
        user_id (int): 需要清理记忆的目标用户 ID，通过 URL 查询参数传递。
        db (AsyncSession): 用于执行数据库操作的异步会话对象，通过依赖注入获取。

    Returns:
        Result[str]: 返回统一封装的 Result 响应对象。
            - 因为当前类型签名为 `Result[str]`，所以 `data` 字段会返回字符串类型。
            - 实际业务场景中，您可以选择返回纯文本成功消息（例如 `"用户 123 的所有 Agent 记忆已清除"`），
              或者返回经过 `json.dumps()` 序列化后的统计结果字符串。
            - 如果实际需要返回结构化统计字典（如 `{"agent_ids": [], "deleted_checkpoints": 3}`），
              建议将签名修正为 `Result[dict]` 或 `Result[Dict[str, Any]]`。

    Raises:
        HTTPException:
            - 404: 当指定的 `user_id` 不存在或没有关联任何 Agent 时。
            - 500: 当数据库（PostgreSQL）删除或向量数据库（Milvus）删除操作失败时。

    Note:
        - 建议在调用本接口前，通过前端页面展示**二次确认**弹窗，防止误操作。
        - 接口内部应当使用数据库事务（`await db.begin()` / `await db.commit()`），
          确保检查点删除与用户级存储删除的**原子性**。
        - 在删除 Milvus 集合时，应先判断集合是否存在，避免抛出预期外的异常。
    """
    return await agent_service.delete_user_agents_all_memory(user_id,db)

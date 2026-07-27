from __future__ import annotations
from typing import TypeVar, Generic, Optional, List
from pydantic import BaseModel

T = TypeVar("T")

class Result(BaseModel, Generic[T]):
    """
    统一响应格式（对标 Java 的 Result<T>）
    所有接口必须返回此格式
    """
    code: int = 200
    msg: str = "操作成功"
    data: Optional[T] = None

    @staticmethod
    def success(data: Optional[T] = None, msg: str = "操作成功") -> Result[T]:
        """成功响应（带数据）"""
        return Result(code=200, msg=msg, data=data)

    @staticmethod
    def success_msg(msg: str = "操作成功") -> Result[T]:
        """成功响应（无数据）"""
        return Result(code=200, msg=msg, data=None)

    @staticmethod
    def error(msg: str, code: int = 500, data: Optional[T] = None) -> Result[T]:
        """错误响应"""
        return Result(code=code, msg=msg, data=data)

    @staticmethod
    def unauthorized(msg: str = "未登录") -> Result[T]:
        """401 未登录"""
        return Result(code=401, msg=msg, data=None)

    @staticmethod
    def forbidden(msg: str = "无权限") -> Result[T]:
        """403 无权限"""
        return Result(code=403, msg=msg, data=None)

    @staticmethod
    def not_found(msg: str = "资源不存在") -> Result[T]:
        """404 资源不存在"""
        return Result(code=404, msg=msg, data=None)

    @staticmethod
    def conflict(msg: str = "数据冲突") -> Result[T]:
        """409 数据冲突"""
        return Result(code=409, msg=msg, data=None)


# ==================== 分页响应 ====================

class Page(BaseModel, Generic[T]):
    """分页统一返回格式"""
    records: List[T]
    total: int
    size: int
    current: int

    @staticmethod
    def of(records: List[T], total: int, size: int, current: int) -> Page[T]:
        """构建分页对象"""
        return Page(records=records, total=total, size=size, current=current)
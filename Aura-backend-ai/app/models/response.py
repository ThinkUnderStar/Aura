from typing import TypeVar, Generic, Optional

from pydantic import BaseModel

T = TypeVar("T")

class Result(BaseModel,Generic[T]):
    code: int
    msg: str
    data: Optional[T] = None
from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    success: bool = True
    code: str = "00000"
    message: str = "OK"
    data: T


class HealthDTO(BaseModel):
    status: str
    service: str

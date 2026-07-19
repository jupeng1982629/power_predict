from typing import TypeVar

from app.schemas.common import ApiResponse

T = TypeVar("T")


def ok(data: T) -> ApiResponse[T]:
    return ApiResponse[T](data=data)

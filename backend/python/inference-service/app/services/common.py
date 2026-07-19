from datetime import UTC, date, datetime
from typing import TypeVar

from app.schemas.common import ApiResponse

T = TypeVar("T")


def ok(data: T) -> ApiResponse[T]:
    return ApiResponse[T](data=data)


def to_date_text(value: date | None) -> str | None:
    return value.isoformat() if value else None


def to_datetime_text(value: datetime | None) -> str | None:
    return value.astimezone(UTC).isoformat() if value else None

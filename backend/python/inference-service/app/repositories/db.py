from typing import Any

from psycopg import connect
from psycopg.rows import dict_row

from app.core.config import database_url


def fetch_all(sql: str, params: tuple[Any, ...] = ()) -> list[dict[str, Any]]:
    with connect(database_url(), row_factory=dict_row) as conn:
        with conn.cursor() as cursor:
            cursor.execute(sql, params)
            return [dict(row) for row in cursor.fetchall()]


def fetch_one(sql: str, params: tuple[Any, ...] = ()) -> dict[str, Any] | None:
    with connect(database_url(), row_factory=dict_row) as conn:
        with conn.cursor() as cursor:
            cursor.execute(sql, params)
            row = cursor.fetchone()
            return dict(row) if row else None


def open_connection():
    return connect(database_url())

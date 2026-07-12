from __future__ import annotations

from typing import Any


def query_value(value: Any) -> Any:
    """Unwrap an Enum member to its wire value for use as a query param.

    httpx's query-param encoder calls ``str()`` on non-primitive values, which for a
    ``(str, Enum)`` member yields ``"ClassName.MEMBER"`` rather than the member's value
    (this only differs from JSON body encoding, which uses the member's str content directly).
    Passing a plain string through unchanged keeps this backward-compatible with callers who
    don't use the enum types.
    """
    return getattr(value, "value", value)

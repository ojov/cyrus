from __future__ import annotations

from typing import Any, TypeVar
from urllib.parse import quote as _quote

from pydantic import BaseModel
from pydantic import ValidationError as _PydanticValidationError

from cyrus._exceptions import ServerError

T = TypeVar("T", bound=BaseModel)


def query_value(value: Any) -> Any:
    """Unwrap an Enum member to its wire value for use as a query param.

    httpx's query-param encoder calls ``str()`` on non-primitive values, which for a
    ``(str, Enum)`` member yields ``"ClassName.MEMBER"`` rather than the member's value
    (this only differs from JSON body encoding, which uses the member's str content directly).
    Passing a plain string through unchanged keeps this backward-compatible with callers who
    don't use the enum types.
    """
    return getattr(value, "value", value)


def build_params(**kwargs: Any) -> dict[str, Any]:
    """Build a query-param dict, dropping any None values."""
    return {k: v for k, v in kwargs.items() if v is not None}


def path_segment(value: str) -> str:
    """Percent-encode a value for safe use as a single URL path segment."""
    return _quote(str(value), safe="")


def parse_model(model_cls: type[T], data: Any) -> T:
    """Validate a server response against a model, wrapping schema drift into ServerError.

    A raw pydantic ValidationError here means the server sent a shape this SDK version
    doesn't recognize — surfaced as a CyrusAPIError subclass so callers catching cyrus's own
    exception hierarchy (not pydantic's, which shares the name ValidationError) still catch it.
    """
    try:
        return model_cls.model_validate(data)
    except _PydanticValidationError as exc:
        raise ServerError(
            code="99",
            message="Received an unexpected response shape from the server.",
        ) from exc

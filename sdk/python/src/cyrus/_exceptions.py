from __future__ import annotations


class CyrusAPIError(Exception):
    """Base exception for all Cyrus API errors."""

    def __init__(self, code: str, message: str, status_code: int | None = None) -> None:
        self.code = code
        self.message = message
        self.status_code = status_code
        super().__init__(message)


class NotFoundError(CyrusAPIError):
    """Resource not found (code 82)."""


class UnauthorizedError(CyrusAPIError):
    """Invalid or missing API key (code 40)."""


class ValidationError(CyrusAPIError):
    """Request validation failed (code 70/71). Includes field-level errors."""

    def __init__(
        self,
        code: str,
        message: str,
        field_errors: list[dict[str, str]] | None = None,
        status_code: int | None = None,
    ) -> None:
        self.field_errors = field_errors or []
        super().__init__(code=code, message=message, status_code=status_code)


class ConflictError(CyrusAPIError):
    """Resource state conflict (code 81, 409, etc.)."""


class ProviderError(CyrusAPIError):
    """Upstream provider (Nomba) error (code 90)."""


class ServerError(CyrusAPIError):
    """Internal server error (code 99, 5xx)."""

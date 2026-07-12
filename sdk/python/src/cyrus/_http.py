from __future__ import annotations

import time
from typing import Any

import httpx

from cyrus._exceptions import (
    ConflictError,
    CyrusAPIError,
    NotFoundError,
    ProviderError,
    ServerError,
    UnauthorizedError,
    ValidationError,
)

_DEFAULT_BASE_URL = "https://api.trycyrus.app"
_MAX_RETRIES = 3
_RETRY_BACKOFF = 0.5  # seconds, doubled each attempt


class HttpClient:
    """Thin wrapper around httpx that handles auth, envelope parsing, and retries."""

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str = _DEFAULT_BASE_URL,
        timeout: float = 30.0,
        max_retries: int = _MAX_RETRIES,
    ) -> None:
        self._client = httpx.Client(
            base_url=base_url,
            headers={"Authorization": f"Bearer {api_key}"},
            timeout=timeout,
        )
        self._max_retries = max_retries

    def get(self, path: str, *, params: dict[str, Any] | None = None) -> Any:
        return self._request("GET", path, params=params)

    def post(self, path: str, *, json: dict[str, Any] | None = None) -> Any:
        return self._request("POST", path, json=json)

    def patch(self, path: str, *, json: dict[str, Any] | None = None) -> Any:
        return self._request("PATCH", path, json=json)

    def delete(self, path: str) -> Any:
        return self._request("DELETE", path)

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        last_exc: Exception | None = None
        for attempt in range(self._max_retries):
            try:
                resp = self._client.request(method, path, **kwargs)
                return self._handle_response(resp)
            except (httpx.TimeoutException, httpx.ConnectError, httpx.RemoteProtocolError) as exc:
                last_exc = exc
                if attempt < self._max_retries - 1:
                    time.sleep(_RETRY_BACKOFF * (2 ** attempt))
        raise CyrusAPIError(
            code="99",
            message=f"Connection failed after {self._max_retries} attempts: {last_exc}",
            status_code=None,
        )

    @staticmethod
    def _handle_response(resp: httpx.Response) -> Any:
        try:
            body: dict[str, Any] = resp.json()
        except ValueError:
            # The api-key chain rejects missing/invalid keys before the request ever reaches a
            # controller, so there's no CyrusApiResponse envelope to parse — just a bare status.
            if resp.status_code in (401, 403):
                raise UnauthorizedError(
                    code="40",
                    message="Invalid or missing API key",
                    status_code=resp.status_code,
                ) from None
            raise CyrusAPIError(
                code="99",
                message=f"Unexpected non-JSON response (HTTP {resp.status_code})",
                status_code=resp.status_code,
            ) from None

        if resp.status_code >= 500:
            msg = body.get("message", "Server error")
            code = body.get("code", "99")
            raise ServerError(code=code, message=msg, status_code=resp.status_code)

        if body.get("status") is False or body.get("status") == "false":
            code = body.get("code", "99")
            msg = body.get("message", "Unknown error")
            data = body.get("data") or {}

            # Checked first: ResponseCode.INVALID_REQUEST ("71") is a generic business-rule-violation
            # bucket the backend reuses across many exception types, several of which are genuine
            # state conflicts returned with HTTP 409 (e.g. closing an already-closed customer,
            # insufficient wallet funds). A 409 always means ConflictError regardless of which
            # internal code produced it — checked before the code-based branches below so those
            # conflicts aren't misclassified as ValidationError. Code "70" (INVALID_INPUT) is never
            # emitted with 409 — it's exclusively bean-validation failures at 400 — so this reordering
            # doesn't affect real field-validation errors.
            if resp.status_code == 409 or code == "81":
                raise ConflictError(code=code, message=msg, status_code=resp.status_code)
            if code in ("70", "71"):
                raise ValidationError(
                    code=code,
                    message=msg,
                    field_errors=data.get("fieldErrors") or [],
                    status_code=resp.status_code,
                )
            if code == "82":
                raise NotFoundError(code=code, message=msg, status_code=resp.status_code)
            if code == "40":
                raise UnauthorizedError(code=code, message=msg, status_code=resp.status_code)
            if code == "90":
                raise ProviderError(code=code, message=msg, status_code=resp.status_code)
            raise CyrusAPIError(code=code, message=msg, status_code=resp.status_code)

        return body.get("data")

    def close(self) -> None:
        self._client.close()

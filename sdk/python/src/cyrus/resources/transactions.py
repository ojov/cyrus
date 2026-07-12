from __future__ import annotations

from typing import Any

from cyrus._enums import MatchStatus, TransactionStatus, TransactionType
from cyrus._http import HttpClient
from cyrus._pagination import Page, PageIterator
from cyrus._utils import query_value
from cyrus.models.responses import Transaction


class Transactions:
    """Transaction endpoints."""

    def __init__(self, http: HttpClient) -> None:
        self._http = http

    def list(
        self,
        *,
        customer_reference: str | None = None,
        type: TransactionType | None = None,
        status: TransactionStatus | None = None,
        match_status: MatchStatus | None = None,
        from_date: str | None = None,
        to_date: str | None = None,
        page: int = 0,
        size: int = 20,
    ) -> Page[Transaction]:
        params: dict[str, Any] = {}
        if customer_reference is not None:
            params["customerReference"] = customer_reference
        if type is not None:
            params["type"] = query_value(type)
        if status is not None:
            params["status"] = query_value(status)
        if match_status is not None:
            params["matchStatus"] = query_value(match_status)
        if from_date is not None:
            params["from"] = from_date
        if to_date is not None:
            params["to"] = to_date
        params["page"] = page
        params["size"] = size
        data = self._http.get("/v1/transactions", params=params)
        return Page(data, Transaction)

    def iter_all(
        self,
        *,
        customer_reference: str | None = None,
        type: TransactionType | None = None,
        status: TransactionStatus | None = None,
        match_status: MatchStatus | None = None,
        from_date: str | None = None,
        to_date: str | None = None,
        page_size: int = 20,
    ) -> PageIterator[Transaction]:
        params: dict[str, Any] = {}
        if customer_reference is not None:
            params["customerReference"] = customer_reference
        if type is not None:
            params["type"] = query_value(type)
        if status is not None:
            params["status"] = query_value(status)
        if match_status is not None:
            params["matchStatus"] = query_value(match_status)
        if from_date is not None:
            params["from"] = from_date
        if to_date is not None:
            params["to"] = to_date
        return PageIterator(
            self._http, "/v1/transactions", Transaction, params=params, page_size=page_size
        )

    def get(self, reference: str) -> Transaction:
        data = self._http.get(f"/v1/transactions/{reference}")
        return Transaction.model_validate(data)

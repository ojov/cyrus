from __future__ import annotations

from typing import Any

from cyrus._enums import MatchStatus, TransactionStatus, TransactionType
from cyrus._http import HttpClient
from cyrus._pagination import Page, PageIterator
from cyrus._utils import build_params, parse_model, path_segment
from cyrus.models.responses import Transaction


class Transactions:
    """Transaction endpoints."""

    def __init__(self, http: HttpClient) -> None:
        self._http = http

    @staticmethod
    def _filter_params(
        *,
        customer_reference: str | None,
        type: TransactionType | None,
        status: TransactionStatus | None,
        match_status: MatchStatus | None,
        from_date: str | None,
        to_date: str | None,
    ) -> dict[str, Any]:
        return build_params(
            customerReference=customer_reference,
            type=type,
            status=status,
            matchStatus=match_status,
            **{"from": from_date, "to": to_date},
        )

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
        params = self._filter_params(
            customer_reference=customer_reference,
            type=type,
            status=status,
            match_status=match_status,
            from_date=from_date,
            to_date=to_date,
        )
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
        params = self._filter_params(
            customer_reference=customer_reference,
            type=type,
            status=status,
            match_status=match_status,
            from_date=from_date,
            to_date=to_date,
        )
        return PageIterator(
            self._http, "/v1/transactions", Transaction, params=params, page_size=page_size
        )

    def get(self, reference: str) -> Transaction:
        data = self._http.get(f"/v1/transactions/{path_segment(reference)}")
        return parse_model(Transaction, data)

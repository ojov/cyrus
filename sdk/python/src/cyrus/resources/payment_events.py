from __future__ import annotations

from typing import Any

from cyrus._enums import PaymentEventStatus
from cyrus._http import HttpClient
from cyrus._pagination import Page, PageIterator
from cyrus._utils import build_params, parse_model, path_segment
from cyrus.models.requests import ReattributePaymentEvent
from cyrus.models.responses import PaymentEventDetail, PaymentEventListItem, ReattributionResult


class PaymentEvents:
    """Payment event (inbound webhook) endpoints."""

    def __init__(self, http: HttpClient) -> None:
        self._http = http

    @staticmethod
    def _filter_params(*, status: PaymentEventStatus | None) -> dict[str, Any]:
        return build_params(status=status)

    def list(
        self,
        *,
        status: PaymentEventStatus | None = None,
        page: int = 0,
        size: int = 20,
    ) -> Page[PaymentEventListItem]:
        params = self._filter_params(status=status)
        params["page"] = page
        params["size"] = size
        data = self._http.get("/v1/payment-events", params=params)
        return Page(data, PaymentEventListItem)

    def iter_all(
        self,
        *,
        status: PaymentEventStatus | None = None,
        page_size: int = 20,
    ) -> PageIterator[PaymentEventListItem]:
        params = self._filter_params(status=status)
        return PageIterator(
            self._http,
            "/v1/payment-events",
            PaymentEventListItem,
            params=params,
            page_size=page_size,
        )

    def get(self, event_id: str) -> PaymentEventDetail:
        data = self._http.get(f"/v1/payment-events/{path_segment(event_id)}")
        return parse_model(PaymentEventDetail, data)

    def replay(self, event_id: str) -> None:
        self._http.post(f"/v1/payment-events/{path_segment(event_id)}/replay")

    def reattribute(self, event_id: str, *, customer_reference: str) -> ReattributionResult:
        body = ReattributePaymentEvent(customerReference=customer_reference).model_dump(by_alias=True)
        data = self._http.post(f"/v1/payment-events/{path_segment(event_id)}/reattribute", json=body)
        return parse_model(ReattributionResult, data)

from __future__ import annotations

from typing import Any

from cyrus._enums import CustomerStatus, KycTier, MatchStatus
from cyrus._http import HttpClient
from cyrus._pagination import Page, PageIterator
from cyrus._utils import query_value
from cyrus.models.requests import (
    CreateCustomer,
    UpdateCustomer,
    UpdateCustomerStatus,
    UpdateKycTier,
)
from cyrus.models.responses import Customer, CustomerListItem, CustomerStatement


class Customers:
    """Customer management endpoints."""

    def __init__(self, http: HttpClient) -> None:
        self._http = http

    def create(
        self,
        *,
        reference: str,
        first_name: str,
        last_name: str | None = None,
        email: str | None = None,
        phone_number: str | None = None,
        bvn: str | None = None,
    ) -> Customer:
        body = CreateCustomer(
            reference=reference,
            firstName=first_name,
            lastName=last_name,
            email=email,
            phoneNumber=phone_number,
            bvn=bvn,
        ).model_dump(exclude_none=True, by_alias=True)
        data = self._http.post("/v1/customers", json=body)
        return Customer.model_validate(data)

    def get(self, reference: str) -> Customer:
        data = self._http.get(f"/v1/customers/{reference}")
        return Customer.model_validate(data)

    def list(self, *, page: int = 0, size: int = 20) -> Page[CustomerListItem]:
        params: dict[str, Any] = {}
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        data = self._http.get("/v1/customers", params=params or None)
        return Page(data, CustomerListItem)

    def iter_all(self, *, page_size: int = 20) -> PageIterator[CustomerListItem]:
        return PageIterator(
            self._http, "/v1/customers", CustomerListItem, page_size=page_size
        )

    def update(
        self,
        reference: str,
        *,
        first_name: str | None = None,
        last_name: str | None = None,
        email: str | None = None,
        phone_number: str | None = None,
    ) -> Customer:
        body = UpdateCustomer(
            firstName=first_name,
            lastName=last_name,
            email=email,
            phoneNumber=phone_number,
        ).model_dump(exclude_none=True, by_alias=True)
        data = self._http.patch(f"/v1/customers/{reference}", json=body)
        return Customer.model_validate(data)

    def set_kyc_tier(self, reference: str, *, tier: KycTier) -> Customer:
        body = UpdateKycTier(tier=tier).model_dump(by_alias=True)
        data = self._http.post(f"/v1/customers/{reference}/kyc-tier", json=body)
        return Customer.model_validate(data)

    def set_status(self, reference: str, *, status: CustomerStatus) -> Customer:
        body = UpdateCustomerStatus(status=status).model_dump(by_alias=True)
        data = self._http.patch(f"/v1/customers/{reference}/status", json=body)
        return Customer.model_validate(data)

    def suspend(self, reference: str) -> Customer:
        return self.set_status(reference, status=CustomerStatus.SUSPENDED)

    def reactivate(self, reference: str) -> Customer:
        return self.set_status(reference, status=CustomerStatus.ACTIVE)

    def close(self, reference: str) -> Customer:
        return self.set_status(reference, status=CustomerStatus.CLOSED)

    def statement( self,  reference: str,  *,  from_date: str | None = None,
        to_date: str | None = None, match_status: MatchStatus | None = None,
        page: int = 0, size: int = 20,) -> CustomerStatement:
        params: dict[str, Any] = {}
        if from_date is not None:
            params["from"] = from_date
        if to_date is not None:
            params["to"] = to_date
        if match_status is not None:
            params["matchStatus"] = query_value(match_status)
        params["page"] = page
        params["size"] = size
        data = self._http.get(f"/v1/customers/{reference}/statement", params=params)
        return CustomerStatement.model_validate(data)

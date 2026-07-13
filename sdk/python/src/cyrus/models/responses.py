from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Generic, TypeVar
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from cyrus._enums import (
    CustomerStatus,
    KycTier,
    MatchStatus,
    PaymentEventStatus,
    PaymentEventType,
    ReconciliationFailureReason,
    TransactionStatus,
    TransactionType,
)

T = TypeVar("T")


class PageResponse(BaseModel, Generic[T]):
    """Spring Data Page shape — used as a Pydantic field type for nested paginated responses."""

    model_config = ConfigDict(populate_by_name=True)

    content: list[T]
    number: int
    size: int
    total_elements: int = Field(alias="totalElements")
    total_pages: int = Field(alias="totalPages")
    number_of_elements: int = Field(default=0, alias="numberOfElements")
    first: bool
    last: bool
    empty: bool


class VirtualAccountSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: UUID
    account_number: str = Field(alias="accountNumber")
    account_name: str = Field(alias="accountName")
    bank_name: str = Field(alias="bankName")
    currency: str
    status: str


class Customer(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: UUID
    reference: str
    first_name: str = Field(alias="firstName")
    last_name: str | None = Field(default=None, alias="lastName")
    email: str | None = None
    phone_number: str | None = Field(default=None, alias="phoneNumber")
    status: CustomerStatus
    kyc_tier: KycTier = Field(alias="kycTier")
    virtual_account: VirtualAccountSummary = Field(alias="virtualAccount")
    created_at: datetime = Field(alias="createdAt")


class CustomerListItem(Customer):
    model_config = ConfigDict(populate_by_name=True)

    lifetime_kobo: Decimal = Field(alias="lifetimeKobo")


class StatementSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    lifetime_kobo: Decimal = Field(alias="lifetimeKobo")
    transaction_count: int = Field(alias="transactionCount")
    pending_count: int = Field(alias="pendingCount")
    pending_kobo: Decimal = Field(alias="pendingKobo")
    manual_review_count: int = Field(alias="manualReviewCount")
    discrepancy_count: int = Field(alias="discrepancyCount")
    last_transaction_at: datetime | None = Field(default=None, alias="lastTransactionAt")


class StatementRow(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    date: datetime
    payer: str | None = None
    ref: str
    match_status: MatchStatus = Field(alias="matchStatus")
    amount_kobo: Decimal = Field(alias="amountKobo")


class Transaction(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    reference: str
    type: TransactionType
    customer_reference: str = Field(alias="customerReference")
    date: datetime
    payer: str | None = None
    provider_transaction_id: str | None = Field(default=None, alias="providerTransactionId")
    status: TransactionStatus
    match_status: MatchStatus = Field(alias="matchStatus")
    amount_kobo: Decimal = Field(alias="amountKobo")
    fee_kobo: Decimal = Field(alias="feeKobo")


class PaymentEventListItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: UUID
    request_id: str = Field(alias="requestId")
    event_type: PaymentEventType = Field(alias="eventType")
    status: PaymentEventStatus
    failure_reason: ReconciliationFailureReason | None = Field(default=None, alias="failureReason")
    status_details: str | None = Field(default=None, alias="statusDetails")
    amount: Decimal | None = None
    account_number: str | None = Field(default=None, alias="accountNumber")
    customer_reference: str | None = Field(default=None, alias="customerReference")
    created_at: datetime = Field(alias="createdAt")


class PaymentEventDetail(PaymentEventListItem):
    model_config = ConfigDict(populate_by_name=True)

    payload: str | None = None


class ReattributionResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    transaction_id: UUID = Field(alias="transactionId")
    customer_reference: str = Field(alias="customerReference")


class CustomerStatement(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    customer: Customer
    summary: StatementSummary
    transactions: PageResponse[StatementRow]

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from cyrus._enums import CustomerStatus, KycTier


class CreateCustomer(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    reference: str
    first_name: str = Field(alias="firstName")
    last_name: str | None = Field(default=None, alias="lastName")
    email: str | None = None
    phone_number: str | None = Field(default=None, alias="phoneNumber")
    bvn: str | None = None


class UpdateCustomer(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    first_name: str | None = Field(default=None, alias="firstName")
    last_name: str | None = Field(default=None, alias="lastName")
    email: str | None = None
    phone_number: str | None = Field(default=None, alias="phoneNumber")


class UpdateCustomerStatus(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: CustomerStatus


class UpdateKycTier(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    tier: KycTier


class ReattributePaymentEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    customer_reference: str = Field(alias="customerReference")

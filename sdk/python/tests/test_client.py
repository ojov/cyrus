from __future__ import annotations

import json

import httpx
import respx

from cyrus import (
    ConflictError,
    CyrusAPIError,
    CyrusClient,
    MatchStatus,
    ServerError,
    TransactionStatus,
    TransactionType,
    UnauthorizedError,
)

API_KEY = "cyrus_test_xxx"
BASE_URL = "https://api.trycyrus.app"


def _ok(data=None):
    return {"code": "00", "description": "SUCCESS", "message": "ok", "status": True, "data": data}


def _err(code="99", message="error", status_code=400, data=None):
    resp = httpx.Response(
        status_code=status_code,
        json={"code": code, "description": "ERROR", "message": message, "status": False, "data": data},
    )
    return resp


def _customer_payload(reference="cust_123", **overrides):
    payload = {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "reference": reference,
        "firstName": "Ada",
        "lastName": None,
        "email": None,
        "phoneNumber": None,
        "status": "ACTIVE",
        "kycTier": "TIER_1",
        "virtualAccount": {
            "id": "550e8400-e29b-41d4-a716-446655440001",
            "accountNumber": "1234567890",
            "accountName": "Ada",
            "bankName": "Wema Bank",
            "currency": "NGN",
            "status": "ACTIVE",
        },
        "createdAt": "2025-01-01T00:00:00Z",
    }
    payload.update(overrides)
    return payload


@respx.mock
def test_create_customer():
    respx.post(f"{BASE_URL}/v1/customers").mock(
        return_value=httpx.Response(200, json=_ok({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "reference": "cust_123",
            "firstName": "Ada",
            "lastName": "Lovelace",
            "email": "ada@example.com",
            "phoneNumber": None,
            "status": "ACTIVE",
            "kycTier": "TIER_1",
            "virtualAccount": {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "accountNumber": "1234567890",
                "accountName": "Ada Lovelace",
                "bankName": "Wema Bank",
                "currency": "NGN",
                "status": "ACTIVE",
            },
            "createdAt": "2025-01-01T00:00:00Z",
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    customer = client.customers.create(
        reference="cust_123",
        first_name="Ada",
        last_name="Lovelace",
        email="ada@example.com",
    )

    assert customer.reference == "cust_123"
    assert customer.first_name == "Ada"
    assert customer.virtual_account.bank_name == "Wema Bank"
    assert customer.virtual_account.account_number == "1234567890"


@respx.mock
def test_get_customer():
    respx.get(f"{BASE_URL}/v1/customers/cust_123").mock(
        return_value=httpx.Response(200, json=_ok({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "reference": "cust_123",
            "firstName": "Ada",
            "lastName": None,
            "email": None,
            "phoneNumber": None,
            "status": "ACTIVE",
            "kycTier": "TIER_1",
            "virtualAccount": {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "accountNumber": "1234567890",
                "accountName": "Ada",
                "bankName": "Wema Bank",
                "currency": "NGN",
                "status": "ACTIVE",
            },
            "createdAt": "2025-01-01T00:00:00Z",
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    customer = client.customers.get("cust_123")
    assert customer.reference == "cust_123"
    assert customer.last_name is None


@respx.mock
def test_list_transactions_filters():
    respx.get(f"{BASE_URL}/v1/transactions").mock(
        return_value=httpx.Response(200, json=_ok({
            "content": [
                {
                    "reference": "txn_001",
                    "type": "CUSTOMER_PAYMENT",
                    "customerReference": "cust_123",
                    "date": "2025-01-01T10:00:00Z",
                    "payer": "John",
                    "providerTransactionId": "prov_001",
                    "status": "SUCCESSFUL",
                    "matchStatus": "MATCHED",
                    "amountKobo": "15000.0000",
                    "feeKobo": "225.0000",
                }
            ],
            "totalElements": 1,
            "totalPages": 1,
            "number": 0,
            "size": 20,
            "numberOfElements": 1,
            "first": True,
            "last": True,
            "empty": False,
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    page = client.transactions.list(customer_reference="cust_123", status="SUCCESSFUL")

    assert len(page) == 1
    assert page[0].reference == "txn_001"
    assert page[0].amount_kobo == 15000
    assert page.meta.total_elements == 1


@respx.mock
def test_not_found_raises():
    respx.get(f"{BASE_URL}/v1/customers/nope").mock(
        return_value=_err(code="82", message="Customer not found", status_code=404)
    )

    from cyrus import NotFoundError

    client = CyrusClient(api_key=API_KEY)
    try:
        client.customers.get("nope")
        assert False, "should have raised"
    except NotFoundError as e:
        assert e.code == "82"
        assert "not found" in e.message.lower()


@respx.mock
def test_validation_error_fields():
    respx.post(f"{BASE_URL}/v1/customers").mock(
        return_value=_err(
            code="70",
            message="One or more fields are invalid",
            status_code=400,
            data={"fieldErrors": [{"field": "email", "message": "must be a valid email"}]},
        )
    )

    from cyrus import ValidationError

    client = CyrusClient(api_key=API_KEY)
    try:
        client.customers.create(reference="x", first_name="X", email="bad")
        assert False, "should have raised"
    except ValidationError as e:
        assert len(e.field_errors) == 1
        assert e.field_errors[0]["field"] == "email"


@respx.mock
def test_iter_all_pagination():
    page1 = {
        "content": [
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "reference": f"cust_{i}",
                "firstName": f"User {i}",
                "status": "ACTIVE",
                "kycTier": "TIER_1",
                "virtualAccount": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "accountNumber": "1234567890",
                    "accountName": f"User {i}",
                    "bankName": "Wema Bank",
                    "currency": "NGN",
                    "status": "ACTIVE",
                },
                "lifetimeKobo": 1000,
                "createdAt": "2025-01-01T00:00:00Z",
            }
            for i in range(2)
        ],
        "totalElements": 3,
        "totalPages": 2,
        "number": 0,
        "size": 2,
        "numberOfElements": 2,
        "first": True,
        "last": False,
        "empty": False,
    }
    page2 = {
        "content": [
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "reference": "cust_2",
                "firstName": "User 2",
                "status": "ACTIVE",
                "kycTier": "TIER_1",
                "virtualAccount": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "accountNumber": "1234567890",
                    "accountName": "User 2",
                    "bankName": "Wema Bank",
                    "currency": "NGN",
                    "status": "ACTIVE",
                },
                "lifetimeKobo": 500,
                "createdAt": "2025-01-01T00:00:00Z",
            }
        ],
        "totalElements": 3,
        "totalPages": 2,
        "number": 1,
        "size": 2,
        "numberOfElements": 1,
        "first": False,
        "last": True,
        "empty": False,
    }

    route = respx.get(f"{BASE_URL}/v1/customers")
    route.side_effect = [
        httpx.Response(200, json=_ok(page1)),
        httpx.Response(200, json=_ok(page2)),
    ]

    client = CyrusClient(api_key=API_KEY)
    items = list(client.customers.iter_all(page_size=2))
    assert len(items) == 3
    assert items[0].reference == "cust_0"
    assert items[2].reference == "cust_2"


@respx.mock
def test_context_manager():
    respx.get(f"{BASE_URL}/v1/customers/cust_123").mock(
        return_value=httpx.Response(200, json=_ok({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "reference": "cust_123",
            "firstName": "Ada",
            "status": "ACTIVE",
            "kycTier": "TIER_1",
            "virtualAccount": {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "accountNumber": "1234567890",
                "accountName": "Ada",
                "bankName": "Wema Bank",
                "currency": "NGN",
                "status": "ACTIVE",
            },
            "createdAt": "2025-01-01T00:00:00Z",
        }))
    )

    with CyrusClient(api_key=API_KEY) as client:
        customer = client.customers.get("cust_123")
        assert customer.reference == "cust_123"


@respx.mock
def test_create_customer_sends_camel_case_body():
    route = respx.post(f"{BASE_URL}/v1/customers").mock(
        return_value=httpx.Response(200, json=_ok({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "reference": "cust_123",
            "firstName": "Ada",
            "lastName": "Lovelace",
            "email": "ada@example.com",
            "phoneNumber": "08012345678",
            "status": "ACTIVE",
            "kycTier": "TIER_1",
            "virtualAccount": {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "accountNumber": "1234567890",
                "accountName": "Ada Lovelace",
                "bankName": "Wema Bank",
                "currency": "NGN",
                "status": "ACTIVE",
            },
            "createdAt": "2025-01-01T00:00:00Z",
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    client.customers.create(
        reference="cust_123",
        first_name="Ada",
        last_name="Lovelace",
        email="ada@example.com",
        phone_number="08012345678",
    )

    sent = json.loads(route.calls.last.request.content)
    assert sent == {
        "reference": "cust_123",
        "firstName": "Ada",
        "lastName": "Lovelace",
        "email": "ada@example.com",
        "phoneNumber": "08012345678",
    }


@respx.mock
def test_update_customer_sends_camel_case_body():
    route = respx.patch(f"{BASE_URL}/v1/customers/cust_123").mock(
        return_value=httpx.Response(200, json=_ok({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "reference": "cust_123",
            "firstName": "Grace",
            "status": "ACTIVE",
            "kycTier": "TIER_1",
            "virtualAccount": {
                "id": "550e8400-e29b-41d4-a716-446655440001",
                "accountNumber": "1234567890",
                "accountName": "Grace",
                "bankName": "Wema Bank",
                "currency": "NGN",
                "status": "ACTIVE",
            },
            "createdAt": "2025-01-01T00:00:00Z",
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    client.customers.update("cust_123", first_name="Grace", phone_number="0801")

    sent = json.loads(route.calls.last.request.content)
    assert sent == {"firstName": "Grace", "phoneNumber": "0801"}


@respx.mock
def test_reattribute_sends_camel_case_body():
    route = respx.post(f"{BASE_URL}/v1/payment-events/evt_1/reattribute").mock(
        return_value=httpx.Response(200, json=_ok({
            "transactionId": "550e8400-e29b-41d4-a716-446655440000",
            "customerReference": "cust_123",
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    result = client.payment_events.reattribute("evt_1", customer_reference="cust_123")

    sent = json.loads(route.calls.last.request.content)
    assert sent == {"customerReference": "cust_123"}
    assert result.customer_reference == "cust_123"


@respx.mock
def test_transactions_list_enum_filters_serialize_to_plain_values():
    route = respx.get(f"{BASE_URL}/v1/transactions").mock(
        return_value=httpx.Response(200, json=_ok({
            "content": [],
            "totalElements": 0,
            "totalPages": 0,
            "number": 0,
            "size": 20,
            "numberOfElements": 0,
            "first": True,
            "last": True,
            "empty": True,
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    client.transactions.list(
        type=TransactionType.PAYOUT,
        status=TransactionStatus.SUCCESSFUL,
        match_status=MatchStatus.DISCREPANCY,
    )

    sent_params = dict(route.calls.last.request.url.params)
    assert sent_params["type"] == "PAYOUT"
    assert sent_params["status"] == "SUCCESSFUL"
    assert sent_params["matchStatus"] == "DISCREPANCY"


@respx.mock
def test_missing_api_key_non_json_response_raises_unauthorized_error():
    respx.get(f"{BASE_URL}/v1/customers/cust_123").mock(
        return_value=httpx.Response(401, text="")
    )

    client = CyrusClient(api_key="bad_key")
    try:
        client.customers.get("cust_123")
        assert False, "should have raised"
    except UnauthorizedError as e:
        assert e.status_code == 401


@respx.mock
def test_409_state_conflict_with_invalid_request_code_raises_conflict_error():
    # Mirrors InvalidCustomerStateException et al.: ResponseCode.INVALID_REQUEST ("71") returned
    # with HTTP 409 for a genuine state conflict, not a field-validation failure.
    respx.patch(f"{BASE_URL}/v1/customers/cust_123/status").mock(
        return_value=_err(code="71", message="Customer is already CLOSED", status_code=409)
    )

    client = CyrusClient(api_key=API_KEY)
    try:
        client.customers.set_status("cust_123", status="CLOSED")
        assert False, "should have raised"
    except ConflictError as e:
        assert e.code == "71"
        assert e.status_code == 409


@respx.mock
def test_money_fields_preserve_sub_kobo_precision():
    raw_body = (
        b'{"code":"00","description":"SUCCESS","message":"ok","status":true,"data":'
        b'{"reference":"txn_1","type":"CUSTOMER_PAYMENT","customerReference":"cust_1",'
        b'"date":"2025-01-01T10:00:00Z","payer":null,"providerTransactionId":null,'
        b'"status":"SUCCESSFUL","matchStatus":"MATCHED",'
        b'"amountKobo":123456789012345.6789,"feeKobo":225.015}}'
    )
    respx.get(f"{BASE_URL}/v1/transactions/txn_1").mock(
        return_value=httpx.Response(200, content=raw_body)
    )

    client = CyrusClient(api_key=API_KEY)
    txn = client.transactions.get("txn_1")
    assert str(txn.amount_kobo) == "123456789012345.6789"
    assert str(txn.fee_kobo) == "225.015"


@respx.mock
def test_post_is_not_retried_on_connection_error():
    route = respx.post(f"{BASE_URL}/v1/customers")
    route.side_effect = httpx.RemoteProtocolError("connection reset")

    client = CyrusClient(api_key=API_KEY, max_retries=3)
    try:
        client.customers.create(reference="cust_1", first_name="Ada")
        assert False, "should have raised"
    except CyrusAPIError:
        pass
    assert route.call_count == 1


@respx.mock
def test_get_is_retried_on_connection_error():
    route = respx.get(f"{BASE_URL}/v1/customers/cust_1")
    route.side_effect = [
        httpx.RemoteProtocolError("connection reset"),
        httpx.Response(200, json=_ok(_customer_payload(reference="cust_1"))),
    ]

    client = CyrusClient(api_key=API_KEY, max_retries=2)
    customer = client.customers.get("cust_1")
    assert customer.reference == "cust_1"
    assert route.call_count == 2


@respx.mock
def test_max_retries_zero_still_makes_one_attempt():
    route = respx.get(f"{BASE_URL}/v1/customers/cust_1").mock(
        return_value=httpx.Response(200, json=_ok(_customer_payload(reference="cust_1")))
    )

    client = CyrusClient(api_key=API_KEY, max_retries=0)
    customer = client.customers.get("cust_1")
    assert customer.reference == "cust_1"
    assert route.call_count == 1


@respx.mock
def test_reference_with_slash_is_percent_encoded_in_path():
    route = respx.get(f"{BASE_URL}/v1/customers/abc%2Fstatement").mock(
        return_value=httpx.Response(200, json=_ok(_customer_payload(reference="abc/statement")))
    )

    client = CyrusClient(api_key=API_KEY)
    customer = client.customers.get("abc/statement")
    assert route.called
    assert customer.reference == "abc/statement"


@respx.mock
def test_malformed_response_raises_server_error_not_pydantic_error():
    respx.get(f"{BASE_URL}/v1/customers/cust_1").mock(
        return_value=httpx.Response(200, json=_ok({"reference": "cust_1"}))
    )

    client = CyrusClient(api_key=API_KEY)
    try:
        client.customers.get("cust_1")
        assert False, "should have raised"
    except ServerError as e:
        assert e.code == "99"


def test_using_client_after_close_raises_cyrus_error():
    client = CyrusClient(api_key=API_KEY)
    client.close()
    try:
        client.customers.get("cust_1")
        assert False, "should have raised"
    except CyrusAPIError:
        pass


def test_page_is_generic_and_subscriptable():
    from cyrus._pagination import Page

    Page[int]  # must not raise TypeError


@respx.mock
def test_page_meta_content_is_typed_not_raw_dicts():
    respx.get(f"{BASE_URL}/v1/customers").mock(
        return_value=httpx.Response(200, json=_ok({
            "content": [{**_customer_payload(reference="cust_1"), "lifetimeKobo": 1000}],
            "totalElements": 1,
            "totalPages": 1,
            "number": 0,
            "size": 20,
            "numberOfElements": 1,
            "first": True,
            "last": True,
            "empty": False,
        }))
    )

    client = CyrusClient(api_key=API_KEY)
    page = client.customers.list()
    assert page.meta.content[0].reference == "cust_1"
    assert page.items[0].reference == "cust_1"


@respx.mock
def test_iter_all_can_be_iterated_twice():
    page1 = {
        "content": [
            {**_customer_payload(reference=f"cust_{i}"), "lifetimeKobo": 1000} for i in range(2)
        ],
        "totalElements": 3,
        "totalPages": 2,
        "number": 0,
        "size": 2,
        "numberOfElements": 2,
        "first": True,
        "last": False,
        "empty": False,
    }
    page2 = {
        "content": [{**_customer_payload(reference="cust_2"), "lifetimeKobo": 500}],
        "totalElements": 3,
        "totalPages": 2,
        "number": 1,
        "size": 2,
        "numberOfElements": 1,
        "first": False,
        "last": True,
        "empty": False,
    }

    route = respx.get(f"{BASE_URL}/v1/customers")
    route.side_effect = [
        httpx.Response(200, json=_ok(page1)),
        httpx.Response(200, json=_ok(page2)),
        httpx.Response(200, json=_ok(page1)),
        httpx.Response(200, json=_ok(page2)),
    ]

    client = CyrusClient(api_key=API_KEY)
    it = client.customers.iter_all(page_size=2)
    first_pass = list(it)
    second_pass = list(it)
    assert len(first_pass) == 3
    assert len(second_pass) == 3

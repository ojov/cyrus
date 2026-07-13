from __future__ import annotations

from cyrus._http import HttpClient
from cyrus.resources.customers import Customers
from cyrus.resources.payment_events import PaymentEvents
from cyrus.resources.transactions import Transactions


class CyrusClient:
    """Client for the Cyrus Payments API.

    Usage::

        from cyrus import CyrusClient

        client = CyrusClient(api_key="cyrus_live_xxx")
        customer = client.customers.create(reference="cust_123", first_name="Ada")
    """

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str = "https://api.trycyrus.app",
        timeout: float = 30.0,
        max_retries: int = 3,
    ) -> None:
        self._http = HttpClient(
            api_key,
            base_url=base_url,
            timeout=timeout,
            max_retries=max_retries,
        )
        self.customers = Customers(self._http)
        self.transactions = Transactions(self._http)
        self.payment_events = PaymentEvents(self._http)

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> CyrusClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

from __future__ import annotations

from typing import Any, Generic, Iterator, TypeVar

from cyrus._http import HttpClient
from cyrus._utils import parse_model
from cyrus.models.responses import PageResponse

T = TypeVar("T")


class Page(Generic[T]):
    """A single page of results from a paginated endpoint."""

    def __init__(self, data: dict[str, Any], item_type: type[T]) -> None:
        self._page: PageResponse[T] = parse_model(PageResponse[item_type], data)
        self.items: list[T] = self._page.content

    @property
    def meta(self) -> PageResponse[T]:
        return self._page

    @property
    def total_elements(self) -> int:
        return self._page.total_elements

    @property
    def total_pages(self) -> int:
        return self._page.total_pages

    @property
    def page_number(self) -> int:
        return self._page.number

    @property
    def is_first(self) -> bool:
        return self._page.first

    @property
    def is_last(self) -> bool:
        return self._page.last

    @property
    def is_empty(self) -> bool:
        return self._page.empty

    def __iter__(self) -> Iterator[T]:
        return iter(self.items)

    def __getitem__(self, index: int) -> T:
        return self.items[index]

    def __len__(self) -> int:
        return len(self.items)


class PageIterator(Generic[T]):
    """Auto-paginating iterator that yields items across all pages."""

    def __init__(
        self,
        client: HttpClient,
        path: str,
        item_type: type[T],
        *,
        params: dict[str, Any] | None = None,
        page_size: int = 20,
    ) -> None:
        self._client = client
        self._path = path
        self._item_type = item_type
        self._params = params or {}
        self._page_size = page_size

    def __iter__(self) -> Iterator[T]:
        # Page cursor is local to each __iter__ call (not instance state), so iterating the same
        # PageIterator object more than once always restarts from page 0 instead of silently
        # resuming from wherever the previous traversal left off.
        current_page = 0
        while True:
            params = {**self._params, "page": current_page, "size": self._page_size}
            data = self._client.get(self._path, params=params)
            page = Page(data, self._item_type)
            yield from page.items
            if page.is_last or page.is_empty:
                break
            current_page += 1

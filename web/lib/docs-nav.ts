export type DocPage = { href: string; title: string; group?: string };

/** Ordered docs navigation — drives the sidebar, the crumb, and the prev/next pager. */
export const DOC_PAGES: DocPage[] = [
  { href: "/", title: "Getting Started" },
  { href: "/environments", title: "Environments" },
  { href: "/reference/authentication", title: "Authentication", group: "API Reference" },
  { href: "/reference/virtual-accounts", title: "Virtual Accounts", group: "API Reference" },
  { href: "/reference/payments", title: "Payments", group: "API Reference" },
  { href: "/reference/transactions", title: "Transactions", group: "API Reference" },
  { href: "/reference/webhooks", title: "Webhooks", group: "API Reference" },
  { href: "/reference/errors", title: "Errors", group: "API Reference" },
  { href: "/api-keys", title: "API Keys" },
  { href: "/webhook-testing", title: "Webhook Testing" },
  { href: "/changelog", title: "Changelog" },
  { href: "/sdks", title: "SDKs" },
];

export function crumbFor(pathname: string): string {
  const page = DOC_PAGES.find((p) => p.href === pathname);
  if (!page) return "Documentation";
  return page.group ? `${page.group} · ${page.title}` : page.title;
}

export function pagerFor(pathname: string): { prev?: DocPage; next?: DocPage } {
  const i = DOC_PAGES.findIndex((p) => p.href === pathname);
  if (i === -1) return {};
  return { prev: DOC_PAGES[i - 1], next: DOC_PAGES[i + 1] };
}

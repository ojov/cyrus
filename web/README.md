# Cyrus — Web

Next.js 16 frontend for [Cyrus](../README.md) — public developer docs and the merchant ops dashboard,
both served from this one app. Talks to the Java backend at the repo root over HTTP; has no backend
logic of its own.

## Tech stack

- **Next.js 16 (App Router), React 19, TypeScript, Tailwind v4**
- Package manager is **pnpm** — this project does not use npm/yarn
- Dependency-free UI: inline SVG icons, hand-rolled `cn`/`naira`/`statusClass` helpers, no shadcn/react-query/sonner

## Getting started

```bash
pnpm install
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000). By default the app points at the backend on
`http://localhost:8080`; override with:

```bash
NEXT_PUBLIC_API_URL=https://api.trycyrus.app pnpm dev
```

Other commands: `pnpm build`, `pnpm lint`.

> **pnpm 11.0.9 gotcha:** it wrongly errors on the ignored native builds (`sharp`, `unrs-resolver`),
> which cascades into `build`/`lint` via its pre-run deps check. Already fixed via
> `pnpm-workspace.yaml → verifyDepsBeforeRun: false` — don't remove that setting.

## Structure

Two route groups, one app:

- **`app/(docs)/`** — public developer docs, no auth required. Getting Started, API Reference
  (authentication, virtual accounts, payments, transactions, payouts, webhooks, errors), API Keys,
  Webhook Testing, Changelog, SDKs. Nav order lives in `lib/docs-nav.ts`. A permanent link to the
  live Scalar API reference (`${NEXT_PUBLIC_API_URL}/docs`) is the actual source of truth for exact
  request/response shapes — these pages are a narrative walkthrough, not a full schema reference.
- **`app/(dashboard)/`** — the merchant ops dashboard, login-gated, served at **`/ops`** (not
  `/dashboard` — see `AGENTS.md` for why). Overview, Customers, Transactions, Reconciliation, API
  Keys, Settings.
- **`app/(auth)/`** — `/login`, `/register`.

## Conventions & design

Full conventions (design tokens, money-as-kobo rendering rules, data-fetching patterns, React 19
lint rules, etc.) live in [`AGENTS.md`](./AGENTS.md) — read that before making non-trivial changes,
it's kept current as the codebase evolves. Design spec + interactive prototype are linked from
[`DESIGN.md`](./DESIGN.md).

## Repo layout note

This is a separate Next.js app inside the [Cyrus](../README.md) monorepo — the Java Spring Boot
backend lives at the repo root and has its own build/test commands (`./mvnw ...`), which don't apply
here.

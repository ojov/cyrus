# Cyrus web — agent guide

Next.js 16 frontend for Cyrus. Rebuilt from scratch (2026-07-05) to faithfully match the design
prototype. Separate from the Java backend at the repo root.

## Stack & commands (pnpm only — never npm)
- **Next 16 App Router, React 19, Tailwind v4, TypeScript.** Package manager is **pnpm**.
- `pnpm dev` · `pnpm build` · `pnpm lint`. Point at the backend with `NEXT_PUBLIC_API_URL`
  (defaults to `http://localhost:8080`; prod is `https://api.trycyrus.app`).
- **pnpm 11.0.9 gotcha:** it wrongly errors on the ignored native builds (`sharp`, `unrs-resolver`)
  and that cascades into `build`/`lint` via its pre-run deps check. Fixed by
  `pnpm-workspace.yaml → verifyDepsBeforeRun: false`. Those two builds are optional (prebuilt platform
  binaries), so skipping their scripts is harmless. **Do not remove that setting.**

## Structure (route groups)
- `app/(docs)/` — **public developer docs** (no auth). `/` = Getting Started;
  `/reference/{authentication,virtual-accounts,payments,payouts,transactions,webhooks,errors}`; `/api-keys`;
  `/webhook-testing`; `/changelog`; `/sdks`. Nav order lives in `lib/docs-nav.ts` (drives sidebar +
  crumb + prev/next pager). Callable reference pages use `<Example>` — a static request/response panel,
  not an interactive console (there's no real "try it" call-through; don't reintroduce fake interactivity).
  A permanent link to the live Scalar API reference (`${NEXT_PUBLIC_API_URL}/docs`) lives in the sidebar
  as the actual source of truth for exact shapes.
- `app/(dashboard)/` — **login-gated ops, served at `/ops`** (`AuthGuard` → `/login`). Overview · Customers ·
  Customer detail (statement) · Transactions · Reconciliation · API keys · Settings. **Not `/dashboard`** —
  the inner folder is named `ops/` (the `(dashboard)` route group itself doesn't affect the URL, only the
  segment folder inside it does). Renamed from `/dashboard` after discovering `trycyrus.app` has an
  edge-level redirect of `/dashboard` → `/login` that isn't in this repo, `next.config.ts`, `vercel.json`,
  Vercel's redirects/firewall/deployment-protection/domain config (all checked and empty/unavailable on
  the current plan), or any deployed function (confirmed via `vercel logs` — those requests show no
  function-invocation marker at all, meaning it's intercepted purely at Vercel's edge before the app ever
  runs). Reproduced on every tested deployment behind the domain regardless of content, so it's tied to
  the domain/account itself, not a stale build. Never resolved the actual cause; renaming the route was
  the fast unblock. If revisiting: file a Vercel support ticket with this history before trying to reuse
  `/dashboard` again.
- `app/(auth)/` — `/login`, `/register` (hit the real backend, save a session cookie + localStorage).

## Conventions
- **Design tokens** in `app/globals.css`: purple oklch palette, dark mode via the **`.dark` class**
  (`@custom-variant dark`), and **system-ui fonts defined as real `:root` vars** (`--font-sans`,
  `--font-mono`). Custom classes use `var(--font-mono)`, so the fonts MUST stay real `:root` vars —
  Tailwind's `@theme inline` does not emit them at runtime. Primitives: `.code-block` + `.tok-*`
  (syntax colors), `.db`/`.db-good|warn|crit|info` (status badges), `.callout`, `.method`/`.path`,
  `.doctable`, `.steps`, `.doc-prose`.
- **Dependency-free UI:** inline SVG icons in `components/icons.tsx` (no lucide); `cn`, `naira`,
  `statusClass` in `lib/utils.ts` (no clsx/tailwind-merge). No shadcn, react-query, or sonner.
- **Money is kobo** (`BigDecimal` scale 4 on the backend — JSON numbers may carry sub-kobo decimals
  like `1500.015`); render with `naira()` at the display edge only.
- **Data:** `lib/api.ts` is real backend for everything now — auth, stats, api-keys, wallet,
  beneficiaries (incl. the bank picker), payouts, webhook config + delivery history, customers/list/
  statement, and transactions (merchant-wide + per-customer). There is no `lib/mock.ts` any more —
  the Customers list page was the last thing on mock data (a backend list-customers endpoint didn't
  exist yet); `GET /v1/merchants/me/customers` now backs it for real.
- **React 19 lint:** no synchronous `setState` inside an effect body — defer with
  `Promise.resolve(...).then(setX)` (see `dashboard/page.tsx`, `dashboard/api-keys/page.tsx`).
- Theme is applied + toggled by `components/theme-toggle.tsx` (class-driven icon, no theme state).

## Design source of truth
`web/DESIGN.md` (spec) + the interactive prototype:
https://claude.ai/code/artifact/7331b1f1-5050-4c02-8500-a9cf73d03099

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
- `app/(docs)/` — **public developer docs** (no auth). `/` = Getting Started; `/environments`;
  `/reference/{authentication,virtual-accounts,payments,transactions,webhooks,errors}`; `/api-keys`;
  `/webhook-testing`; `/changelog`; `/sdks`. Nav order lives in `lib/docs-nav.ts` (drives sidebar +
  crumb + prev/next pager). Callable reference pages use `<TryIt>`.
- `app/(dashboard)/` — **login-gated ops** (`AuthGuard` → `/login`). Overview · Customers ·
  Customer detail (statement) · Transactions · Reconciliation · API keys · Settings.
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
- **Money is kobo** (`BigInteger` on the backend); render with `naira()` at the display edge only.
- **Data:** `lib/api.ts` = real backend (auth + `/merchants/me/stats`, api-keys, go-live).
  `lib/mock.ts` = **mock** Customers/Transactions/Reconciliation (those endpoints do not exist yet —
  clearly `TODO(backend)`).
- **React 19 lint:** no synchronous `setState` inside an effect body — defer with
  `Promise.resolve(...).then(setX)` (see `dashboard/page.tsx`, `dashboard/api-keys/page.tsx`).
- Theme is applied + toggled by `components/theme-toggle.tsx` (class-driven icon, no theme state).

## Design source of truth
`web/DESIGN.md` (spec) + the interactive prototype:
https://claude.ai/code/artifact/7331b1f1-5050-4c02-8500-a9cf73d03099

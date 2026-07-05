import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Server-side gate for /dashboard/** — runs before any rendering, so an unauthenticated
 * request never reaches the dashboard shell (no flash of protected UI, no wasted data
 * fetches). AuthGuard stays as a client-side fallback for a session that goes stale
 * without a full navigation (e.g. logout in another tab), but this is the primary gate.
 */
export function proxy(request: NextRequest) {
  const token = request.cookies.get("cyrus_token")?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

// Next 16.2.10 still reads the matcher from an export named `config` (verified against
// node_modules/next/dist/build/analysis/get-page-static-info.js) — only the function name
// changed from `middleware` to `proxy`.
export const config = {
  matcher: ["/dashboard/:path*"],
};

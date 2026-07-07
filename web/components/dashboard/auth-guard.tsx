"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { dashboardApi } from "@/lib/api";

/**
 * Redirects to /login when there is no valid session. The session is an httpOnly cookie this app
 * cannot read directly, so validity is checked the only way that's possible: an authenticated
 * request actually succeeding.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();

  useEffect(() => {
    dashboardApi.stats().catch(() => router.replace("/login"));
  }, [router]);

  return <>{children}</>;
}

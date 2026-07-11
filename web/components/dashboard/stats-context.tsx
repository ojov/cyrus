"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { dashboardApi, type DashboardStats } from "@/lib/api";

type Stats = DashboardStats["data"] | null;

type StatsContextValue = { stats: Stats; loading: boolean; refreshStats: () => Promise<void> };

const StatsContext = createContext<StatsContextValue>({
  stats: null,
  loading: true,
  refreshStats: async () => {},
});

const POLL_INTERVAL_MS = 60 * 1000; // 60 seconds — a fallback, not the primary refresh mechanism

/**
 * Fetches /v1/merchants/me/stats on mount and shares it via context — every consumer (env badge,
 * Overview, Settings) reads the same cached value instead of each firing its own request.
 *
 * Revalidates on tab focus and network reconnect (the SWR/TanStack Query convention — these two
 * events cover the vast majority of "the data behind this tab went stale" cases: you switched
 * away and came back, or your connection dropped and recovered), with a 60s interval poll as a
 * fallback for a tab left open and focused for a long stretch. Confirmed live: without any of
 * this, the Overview page showed a stale wallet balance and reconciliation rate until a manual
 * reload, even after a payment had fully processed and reconciled server-side.
 */
export function StatsProvider({ children }: { children: ReactNode }) {
  const [stats, setStats] = useState<Stats>(null);
  const [loading, setLoading] = useState(true);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchStats = useCallback(async () => {
    try {
      const res = await dashboardApi.stats();
      setStats(res.data);
    } catch {
      setStats(null);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(fetchStats).then(() => setLoading(false));

    intervalRef.current = setInterval(fetchStats, POLL_INTERVAL_MS);

    const onVisible = () => {
      if (document.visibilityState === "visible") fetchStats();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);
    window.addEventListener("online", fetchStats);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
      window.removeEventListener("online", fetchStats);
    };
  }, [fetchStats]);

  return <StatsContext.Provider value={{ stats, loading, refreshStats: fetchStats }}>{children}</StatsContext.Provider>;
}

export function useDashboardStats() {
  return useContext(StatsContext);
}

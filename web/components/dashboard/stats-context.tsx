"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { dashboardApi, type DashboardStats } from "@/lib/api";

type Stats = DashboardStats["data"] | null;

type StatsContextValue = { stats: Stats; loading: boolean; refreshStats: () => Promise<void> };

const StatsContext = createContext<StatsContextValue>({
  stats: null,
  loading: true,
  refreshStats: async () => {},
});

/**
 * Fetches /v1/merchants/me/stats once per dashboard session and shares it via context.
 * Without this, every consumer (env badge, Overview, Settings) fetched independently —
 * landing on any page fired 2-3 identical requests at once.
 */
export function StatsProvider({ children }: { children: ReactNode }) {
  const [stats, setStats] = useState<Stats>(null);
  const [loading, setLoading] = useState(true);

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
  }, [fetchStats]);

  return <StatsContext.Provider value={{ stats, loading, refreshStats: fetchStats }}>{children}</StatsContext.Provider>;
}

export function useDashboardStats() {
  return useContext(StatsContext);
}

"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { dashboardApi, type DashboardStats } from "@/lib/api";

type Stats = DashboardStats["data"] | null;

const StatsContext = createContext<{ stats: Stats; loading: boolean }>({ stats: null, loading: true });

/**
 * Fetches /v1/merchants/me/stats once per dashboard session and shares it via context.
 * Without this, every consumer (env badge, Overview, Settings) fetched independently —
 * landing on any page fired 2-3 identical requests at once.
 */
export function StatsProvider({ children }: { children: ReactNode }) {
  const [stats, setStats] = useState<Stats>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    dashboardApi
      .stats()
      .then((res) => setStats(res.data))
      .catch(() => setStats(null))
      .finally(() => setLoading(false));
  }, []);

  return <StatsContext.Provider value={{ stats, loading }}>{children}</StatsContext.Provider>;
}

export function useDashboardStats() {
  return useContext(StatsContext);
}

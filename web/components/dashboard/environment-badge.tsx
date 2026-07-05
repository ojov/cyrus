"use client";

import { useEffect, useState } from "react";
import { dashboardApi, DashboardStats } from "@/lib/api";

export default function EnvironmentBadge() {
  const [live, setLive] = useState(false);

  useEffect(() => {
    Promise.resolve().then(() =>
      dashboardApi.stats().then((r: DashboardStats) => setLive(r.data.liveModeActive)).catch(() => {})
    );
  }, []);

  const label = live ? "LIVE" : "TEST";
  const cls = live ? "db db-good" : "db db-info";
  return <span className={cls}>{label}</span>;
}

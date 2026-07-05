"use client";

import { useDashboardStats } from "@/components/dashboard/stats-context";

export default function EnvironmentBadge() {
  const { stats } = useDashboardStats();
  const live = stats?.liveModeActive ?? false;

  const label = live ? "LIVE" : "TEST";
  const cls = live ? "db db-good" : "db db-info";
  return <span className={cls}>{label}</span>;
}

"use client";

import Link from "next/link";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import { naira } from "@/lib/utils";

export default function WalletChip() {
  const { stats } = useDashboardStats();

  return (
    <Link
      href="/ops/wallet"
      className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs font-medium transition hover:bg-accent"
    >
      <span className="text-muted-foreground">Wallet</span>
      <span className="font-mono tabular-nums">{stats ? naira(stats.walletBalance) : "—"}</span>
    </Link>
  );
}

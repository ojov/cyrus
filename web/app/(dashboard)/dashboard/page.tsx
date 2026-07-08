"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getSession, type MerchantSession } from "@/lib/auth";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import { naira } from "@/lib/utils";
import { IconArrowRight, IconUsers, IconCard, IconWallet, IconCheckCircle } from "@/components/icons";

const CHART_WIDTH = 244;
const CHART_BASELINE_Y = 60;
const CHART_TOP_MARGIN = 10;
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

// `new Date("2026-07-02").toLocaleDateString(...)` reinterprets a UTC-midnight date through the
// viewer's local timezone, which can shift the displayed weekday by a day — parse Y/M/D directly
// and ask for the UTC day-of-week instead, matching the calendar date the server actually meant.
function weekdayLabel(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  return WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
}

export default function OverviewPage() {
  const [session, setSession] = useState<MerchantSession | null>(null);
  const { stats } = useDashboardStats();

  useEffect(() => {
    // Read client-only session after mount (setState in callbacks, not synchronously).
    Promise.resolve(getSession()).then(setSession);
  }, []);

  const r = stats?.reconciliation;
  const total = r ? r.matched + r.discrepancy + r.manualReview + r.pending + r.orphaned : 0;
  const needsAttention = r ? r.orphaned + r.manualReview : 0;
  const reconciliationRate = r && total > 0 ? `${((r.matched / total) * 100).toFixed(1)}%` : "—";
  const pct = (n: number) => (total > 0 ? (n / total) * 100 : 0);

  const cards = [
    { label: "Customers", value: stats ? stats.customers.toLocaleString() : "—", sub: "identities", Icon: IconUsers },
    { label: "Virtual accounts", value: stats ? stats.virtualAccounts.toLocaleString() : "—", sub: "provisioned", Icon: IconCard },
    { label: "Wallet balance", value: stats ? naira(stats.walletBalance) : "—", sub: "available now", Icon: IconWallet },
    { label: "Reconciliation rate", value: reconciliationRate, sub: "all time", Icon: IconCheckCircle },
  ];

  const inflow = stats?.inflowLast7Days ?? [];
  const maxInflow = Math.max(1, ...inflow.map((d) => d.amountKobo));
  const points = inflow.map((d, i) => {
    const x = inflow.length > 1 ? (i / (inflow.length - 1)) * (CHART_WIDTH - 12) + 6 : 6;
    const y = CHART_BASELINE_Y - (d.amountKobo / maxInflow) * (CHART_BASELINE_Y - CHART_TOP_MARGIN);
    return { x, y, ...d };
  });
  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  const areaPath =
    points.length > 0
      ? `${linePath} L${points[points.length - 1].x.toFixed(1)},${CHART_BASELINE_Y} L${points[0].x.toFixed(1)},${CHART_BASELINE_Y} Z`
      : "";
  const last = points[points.length - 1];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{session ? `Welcome back, ${session.businessName}` : "Overview"}</h1>
        <p className="mt-1 text-sm text-muted-foreground">Health of your virtual-account infrastructure at a glance.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-xl border border-border bg-card p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{c.label}</span>
              <c.Icon className="size-4 text-muted-foreground" />
            </div>
            <div className="mt-2 text-2xl font-bold tabular-nums">{c.value}</div>
            <div className="mt-0.5 text-xs text-muted-foreground">{c.sub}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="mb-3.5 flex items-center justify-between">
            <b className="text-sm">Reconciliation health</b>
            <span className="text-xs text-muted-foreground">{total} transaction{total === 1 ? "" : "s"}</span>
          </div>
          {r && total > 0 ? (
            <>
              <div className="mb-3 flex h-2 overflow-hidden rounded-full bg-muted">
                <div className="bg-green-500" style={{ width: `${pct(r.matched)}%` }} />
                <div className="bg-amber-500" style={{ width: `${pct(r.discrepancy + r.pending)}%` }} />
                <div className="bg-red-500" style={{ width: `${pct(r.orphaned + r.manualReview)}%` }} />
              </div>
              <div className="flex flex-wrap gap-4 text-xs">
                <span className="db db-good dot">{r.matched} matched</span>
                <span className="db db-warn dot">{r.discrepancy + r.pending} discrepancy / pending</span>
                <span className="db db-crit dot">{r.orphaned + r.manualReview} orphaned / manual review</span>
              </div>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">{stats ? "No transactions yet." : "Loading…"}</p>
          )}
          <div className="mt-4 flex items-center justify-between border-t border-border pt-4">
            <span className="text-xs text-muted-foreground">{needsAttention} item{needsAttention === 1 ? "" : "s"} need attention</span>
            <Link
              href="/dashboard/reconciliation"
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium transition hover:bg-accent"
            >
              Review exceptions <IconArrowRight className="size-3.5" />
            </Link>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <div className="mb-1.5 flex items-center justify-between">
            <b className="text-sm">Inflow — last 7 days</b>
            <span className="text-xs text-muted-foreground">{last ? naira(last.amountKobo) : "₦"} today</span>
          </div>
          {points.length > 0 ? (
            <svg viewBox={`0 0 ${CHART_WIDTH} 72`} width="100%" height="96" preserveAspectRatio="none" role="img" aria-label="Inflow over the last 7 days">
              <defs>
                <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stopColor="var(--primary)" stopOpacity="0.28" />
                  <stop offset="1" stopColor="var(--primary)" stopOpacity="0" />
                </linearGradient>
              </defs>
              <line x1="0" y1={CHART_BASELINE_Y} x2={CHART_WIDTH} y2={CHART_BASELINE_Y} stroke="var(--border)" strokeWidth="1" />
              <path d={areaPath} fill="url(#spark)" />
              <path d={linePath} fill="none" stroke="var(--primary)" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
              {last && <circle cx={last.x} cy={last.y} r="3.6" fill="var(--primary)" />}
            </svg>
          ) : (
            <div className="flex h-24 items-center justify-center text-sm text-muted-foreground">
              {stats ? "No inflow yet." : "Loading…"}
            </div>
          )}
          <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
            {inflow.length > 0 && (
              <>
                <span>{weekdayLabel(inflow[0].date)}</span>
                <span>{weekdayLabel(inflow[inflow.length - 1].date)}</span>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

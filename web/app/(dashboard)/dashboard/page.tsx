"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getSession, type MerchantSession } from "@/lib/auth";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import { OVERVIEW } from "@/lib/mock";
import { naira } from "@/lib/utils";
import { IconArrowRight, IconUsers, IconCard, IconWallet, IconCheckCircle } from "@/components/icons";

export default function OverviewPage() {
  const [session, setSession] = useState<MerchantSession | null>(null);
  const { stats } = useDashboardStats();

  useEffect(() => {
    // Read client-only session after mount (setState in callbacks, not synchronously).
    Promise.resolve(getSession()).then(setSession);
  }, []);

  const cards = [
    { label: "Customers", value: stats ? stats.customers.toLocaleString() : "—", sub: "identities", Icon: IconUsers },
    { label: "Virtual accounts", value: stats ? stats.virtualAccounts.toLocaleString() : "—", sub: "provisioned", Icon: IconCard },
    { label: "Wallet balance", value: stats ? naira(stats.walletBalance) : "—", sub: "available now", Icon: IconWallet },
    { label: "Reconciliation rate", value: OVERVIEW.reconciliationRate, sub: "last 24h", Icon: IconCheckCircle },
  ];

  const r = OVERVIEW.recon;
  const pct = (n: number) => (n / r.total) * 100;

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
            <span className="text-xs text-muted-foreground">last 24 hours · {r.total} transfers</span>
          </div>
          <div className="mb-3 flex h-2 overflow-hidden rounded-full bg-muted">
            <div className="bg-green-500" style={{ width: `${pct(r.matched)}%` }} />
            <div className="bg-amber-500" style={{ width: `${pct(r.partial)}%` }} />
            <div className="bg-red-500" style={{ width: `${pct(r.orphaned)}%` }} />
          </div>
          <div className="flex flex-wrap gap-4 text-xs">
            <span className="db db-good dot">{r.matched} matched</span>
            <span className="db db-warn dot">{r.partial} partial / unmatched</span>
            <span className="db db-crit dot">{r.orphaned} orphaned</span>
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-border pt-4">
            <span className="text-xs text-muted-foreground">{r.orphaned} items need attention</span>
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
            <span className="text-xs text-muted-foreground">₦</span>
          </div>
          <svg viewBox="0 0 244 72" width="100%" height="96" preserveAspectRatio="none" role="img" aria-label="Weekly inflow trending up">
            <defs>
              <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="var(--primary)" stopOpacity="0.28" />
                <stop offset="1" stopColor="var(--primary)" stopOpacity="0" />
              </linearGradient>
            </defs>
            <line x1="0" y1="60" x2="244" y2="60" stroke="var(--border)" strokeWidth="1" />
            <path d="M6,50 L44,44 L82,47 L120,33 L158,30 L198,20 L236,15 L236,60 L6,60 Z" fill="url(#spark)" />
            <path d="M6,50 L44,44 L82,47 L120,33 L158,30 L198,20 L236,15" fill="none" stroke="var(--primary)" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
            <circle cx="236" cy="15" r="3.6" fill="var(--primary)" />
          </svg>
          <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
            <span>Mon</span>
            <span>Sun</span>
          </div>
        </div>
      </div>
    </div>
  );
}

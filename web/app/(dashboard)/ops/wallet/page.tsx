"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { walletApi } from "@/lib/api";
import { naira } from "@/lib/utils";
import { IconArrowRight, IconWallet } from "@/components/icons";

export default function WalletPage() {
  const [balance, setBalance] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await walletApi.get();
      setBalance(res.data.availableBalance);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load wallet");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Wallet</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your settled balance held in Cyrus. Payments confirmed by reconciliation are credited here, net of Nomba&apos;s
          fee and Cyrus&apos;s platform fee.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <IconWallet className="size-4" />
          Available balance
        </div>
        <div className="mt-2 text-4xl font-bold tabular-nums">{loading ? "—" : naira(balance ?? 0)}</div>
        <div className="mt-5 flex gap-3 border-t border-border pt-5">
          <Link
            href="/ops/payouts"
            className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
          >
            Send a payout <IconArrowRight className="size-3.5" />
          </Link>
          <Link
            href="/ops/beneficiaries"
            className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-2 text-sm font-medium transition hover:bg-accent"
          >
            Manage beneficiaries
          </Link>
        </div>
      </div>
    </div>
  );
}

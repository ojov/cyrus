"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, platformApi, type PlatformOverview } from "@/lib/api";
import { naira } from "@/lib/utils";

export default function PlatformPage() {
  const [data, setData] = useState<PlatformOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [forbidden, setForbidden] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await platformApi.overview();
      setData(res.data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setForbidden(true);
      else setError(e instanceof Error ? e.message : "Failed to load platform overview");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  if (forbidden) {
    return (
      <div className="rounded-xl border border-border bg-card p-10 text-center text-sm text-muted-foreground">
        You don&apos;t have access to the platform overview.
      </div>
    );
  }

  const c = data?.custody;
  const covered = c?.coverageKobo != null && c.coverageKobo >= 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Platform overview</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Cyrus-wide custody, totals, and integrity — super-admin only.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      {!error && (loading || !data) ? (
        <p className="text-sm text-muted-foreground">Loading&hellip;</p>
      ) : (
        <>
          {/* Custody hero */}
          <div className="rounded-xl border border-border bg-card p-5">
            <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Custody</p>
            <div className="mt-3 grid gap-4 sm:grid-cols-3">
              <div>
                <div className="text-xs text-muted-foreground">Owed to merchants (all wallets)</div>
                <div className="mt-1 text-2xl font-bold tabular-nums">{naira(data.custody.walletLiabilitiesKobo)}</div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground">Held at Nomba (live)</div>
                <div className="mt-1 text-2xl font-bold tabular-nums">
                  {c?.nombaBalanceAvailable && c.nombaBalanceKobo != null ? naira(c.nombaBalanceKobo) : "—"}
                </div>
                {!c?.nombaBalanceAvailable && (
                  <div className="text-xs text-muted-foreground">provider balance unavailable</div>
                )}
              </div>
              <div>
                <div className="text-xs text-muted-foreground">Coverage</div>
                <div
                  className={`mt-1 text-2xl font-bold tabular-nums ${
                    c?.coverageKobo == null ? "" : covered ? "text-green-600 dark:text-green-400" : "text-destructive"
                  }`}
                >
                  {c?.coverageKobo != null ? naira(c.coverageKobo) : "—"}
                </div>
                {c?.coverageKobo != null && (
                  <div className="text-xs text-muted-foreground">{covered ? "fully covered ✓" : "shortfall"}</div>
                )}
              </div>
            </div>
          </div>

          {/* Totals */}
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {[
              { label: "Merchants", value: data.totals.merchants },
              { label: "Customers", value: data.totals.customers },
              { label: "Virtual accounts", value: data.totals.virtualAccounts },
              { label: "Transactions", value: data.totals.transactions },
            ].map((t) => (
              <div key={t.label} className="rounded-xl border border-border bg-card p-4">
                <div className="text-xs text-muted-foreground">{t.label}</div>
                <div className="mt-1 text-xl font-bold tabular-nums">{t.value.toLocaleString()}</div>
              </div>
            ))}
            <div className="rounded-xl border border-border bg-card p-4">
              <div className="text-xs text-muted-foreground">Confirmed inflow</div>
              <div className="mt-1 text-xl font-bold tabular-nums">{naira(data.totals.totalConfirmedInflowKobo)}</div>
            </div>
            <div className="rounded-xl border border-border bg-card p-4">
              <div className="text-xs text-muted-foreground">Payouts</div>
              <div className="mt-1 text-xl font-bold tabular-nums">{naira(data.totals.totalPayoutsKobo)}</div>
            </div>
          </div>

          {/* Reconciliation health */}
          <div className="rounded-xl border border-border bg-card p-5">
            <b className="text-sm">Reconciliation health (all merchants)</b>
            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
              {[
                { label: "Matched", value: data.reconciliation.matched, good: true },
                { label: "Discrepancy", value: data.reconciliation.discrepancy, crit: data.reconciliation.discrepancy > 0 },
                { label: "Manual review", value: data.reconciliation.manualReview, crit: data.reconciliation.manualReview > 0 },
                { label: "Pending", value: data.reconciliation.pending, warn: data.reconciliation.pending > 0 },
              ].map((r) => (
                <div key={r.label} className="rounded-lg border border-border px-3 py-2.5">
                  <div className="text-xs text-muted-foreground">{r.label}</div>
                  <div className="mt-0.5 text-lg font-semibold tabular-nums">{r.value.toLocaleString()}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Orphaned & stuck */}
          <div className="rounded-xl border border-border bg-card p-5">
            <b className="text-sm">Orphaned &amp; stuck</b>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <div className="rounded-lg border border-border px-4 py-3">
                <div className="text-xs text-muted-foreground">Unattributed orphan payments</div>
                <div className="mt-1 text-lg font-semibold tabular-nums">{data.orphansAndStuck.unattributedOrphans}</div>
              </div>
              <div className="rounded-lg border border-border px-4 py-3">
                <div className="text-xs text-muted-foreground">Stuck payouts (PROCESSING)</div>
                <div className="mt-1 text-lg font-semibold tabular-nums">{data.orphansAndStuck.stuckPayouts}</div>
              </div>
            </div>
            {data.orphansAndStuck.stuckPayoutDetails.length > 0 && (
              <div className="mt-3 overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                      <th className="px-3 py-2 font-medium">Reference</th>
                      <th className="px-3 py-2 font-medium">Merchant</th>
                      <th className="px-3 py-2 text-right font-medium">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.orphansAndStuck.stuckPayoutDetails.map((p) => (
                      <tr key={p.id} className="border-b border-border last:border-0">
                        <td className="px-3 py-2 font-mono text-xs">{p.reference}</td>
                        <td className="px-3 py-2">{p.merchantName ?? "—"}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{naira(p.amountKobo)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Ledger integrity */}
          <div className="rounded-xl border border-border bg-card p-5">
            <div className="flex items-center justify-between">
              <b className="text-sm">Ledger integrity</b>
              <span className={`db dot ${data.ledgerIntegrity.allReconciled ? "db-good" : "db-crit"}`}>
                {data.ledgerIntegrity.allReconciled ? "All reconciled" : `${data.ledgerIntegrity.mismatchCount} mismatch`}
              </span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {data.ledgerIntegrity.walletsChecked.toLocaleString()} wallet(s) checked — every wallet&apos;s balance
              must equal the signed sum of its ledger entries.
            </p>
            {data.ledgerIntegrity.mismatches.length > 0 && (
              <div className="mt-3 overflow-x-auto rounded-lg border border-destructive/40">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-destructive/10 text-left text-[11px] uppercase tracking-wide text-destructive">
                      <th className="px-3 py-2 font-medium">Merchant</th>
                      <th className="px-3 py-2 text-right font-medium">Balance</th>
                      <th className="px-3 py-2 text-right font-medium">Ledger sum</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.ledgerIntegrity.mismatches.map((m) => (
                      <tr key={m.walletId} className="border-b border-border last:border-0">
                        <td className="px-3 py-2">{m.merchantName ?? "—"}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{naira(m.balanceKobo)}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{naira(m.ledgerSumKobo)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

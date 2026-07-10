"use client";

import { useCallback, useEffect, useState } from "react";
import { beneficiaryApi, payoutApi, type BeneficiaryItem, type PayoutItem } from "@/lib/api";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import { naira, statusClass } from "@/lib/utils";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 text-sm outline-none focus:border-primary";

export default function PayoutsPage() {
  const { refreshStats } = useDashboardStats();
  const [payouts, setPayouts] = useState<PayoutItem[]>([]);
  const [beneficiaries, setBeneficiaries] = useState<BeneficiaryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [beneficiaryId, setBeneficiaryId] = useState("");
  const [amountNaira, setAmountNaira] = useState("");
  const [narration, setNarration] = useState("");

  const load = useCallback(async () => {
    try {
      const [payoutRes, beneficiaryRes] = await Promise.all([payoutApi.list(), beneficiaryApi.list()]);
      setPayouts(payoutRes.data?.content ?? []);
      setBeneficiaries(beneficiaryRes.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load payouts");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  async function create() {
    const amount = Number(amountNaira);
    if (!beneficiaryId) {
      setError("Choose a beneficiary.");
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setError("Enter a valid amount.");
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await payoutApi.create({ beneficiaryId, amount: Math.round(amount * 100), narration: narration.trim() || undefined });
      setAmountNaira("");
      setNarration("");
      await Promise.all([load(), refreshStats()]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Payout failed");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Payouts</h1>
        <p className="mt-1 text-sm text-muted-foreground">Withdraw your wallet balance to a registered beneficiary.</p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-4">
        <b className="text-sm">Send a payout</b>
        {beneficiaries.length === 0 && !loading ? (
          <p className="mt-2 text-sm text-muted-foreground">
            No beneficiaries yet — add one on the Beneficiaries page first.
          </p>
        ) : (
          <div className="mt-3 space-y-3">
            <select className={field} value={beneficiaryId} onChange={(e) => setBeneficiaryId(e.target.value)}>
              <option value="">Choose a beneficiary…</option>
              {beneficiaries.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.nickname} · {b.accountNumber}
                </option>
              ))}
            </select>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <input
                  className={field}
                  placeholder="Amount (₦)"
                  inputMode="decimal"
                  value={amountNaira}
                  onChange={(e) => setAmountNaira(e.target.value)}
                />
                <p className="mt-1 text-xs text-muted-foreground">₦30 flat fee per payout</p>
              </div>
              <input className={field} placeholder="Narration (optional)" value={narration} onChange={(e) => setNarration(e.target.value)} />
            </div>
            <button
              type="button"
              onClick={create}
              disabled={creating}
              className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
            >
              {creating ? "Sending…" : "Send payout"}
            </button>
          </div>
        )}
      </div>

      <div className="rounded-xl border border-border bg-card">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold">Payout history</div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">Loading…</p>
        ) : payouts.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">No payouts yet.</p>
        ) : (
          <div className="divide-y divide-border">
            {payouts.map((p) => (
              <div key={p.id} className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <code className="truncate font-mono text-sm">{p.reference}</code>
                    <span className={`db dot ${statusClass(p.status)}`}>{p.status}</span>
                  </div>
                  {p.failureReason && <p className="mt-0.5 text-xs text-destructive">{p.failureReason}</p>}
                  <p className="mt-0.5 text-xs text-muted-foreground">{new Date(p.createdAt).toLocaleString()}</p>
                </div>
                <div className="shrink-0 text-right font-mono text-sm tabular-nums">{naira(p.amount)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

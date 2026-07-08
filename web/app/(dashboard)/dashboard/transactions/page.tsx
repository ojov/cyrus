"use client";

import { useCallback, useEffect, useState } from "react";
import { transactionApi, type TransactionPage } from "@/lib/api";
import { naira, statusClass } from "@/lib/utils";

const MATCH_STATUSES = ["MATCHED", "UNMATCHED", "DISCREPANCY", "ORPHANED", "MANUAL_REVIEW"];
const TYPES = ["CUSTOMER_PAYMENT", "PAYOUT", "REVERSAL", "ADJUSTMENT"];
const PAGE_SIZE = 20;

export default function TransactionsPage() {
  const [data, setData] = useState<TransactionPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const [customerRefInput, setCustomerRefInput] = useState("");
  const [typeInput, setTypeInput] = useState("");
  const [matchStatusInput, setMatchStatusInput] = useState("");

  const [customerRef, setCustomerRef] = useState("");
  const [type, setType] = useState("");
  const [matchStatus, setMatchStatus] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await transactionApi.list({
        customerReference: customerRef || undefined,
        type: type || undefined,
        matchStatus: matchStatus || undefined,
        page,
        size: PAGE_SIZE,
      });
      setData(res.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
  }, [customerRef, type, matchStatus, page]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  function applyFilters(e: React.FormEvent) {
    e.preventDefault();
    setCustomerRef(customerRefInput);
    setType(typeInput);
    setMatchStatus(matchStatusInput);
    setPage(0);
  }

  function clearFilters() {
    setCustomerRefInput("");
    setTypeInput("");
    setMatchStatusInput("");
    setCustomerRef("");
    setType("");
    setMatchStatus("");
    setPage(0);
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Transactions</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Every inbound transfer and outbound payout across all your customers.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <form onSubmit={applyFilters} className="flex flex-wrap items-center gap-2">
        <input
          value={customerRefInput}
          onChange={(e) => setCustomerRefInput(e.target.value)}
          placeholder="Customer reference"
          className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs font-mono outline-none focus:border-primary"
        />
        <select
          value={typeInput}
          onChange={(e) => setTypeInput(e.target.value)}
          className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
        >
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <select
          value={matchStatusInput}
          onChange={(e) => setMatchStatusInput(e.target.value)}
          className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
        >
          <option value="">All match statuses</option>
          {MATCH_STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <button
          type="submit"
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition hover:brightness-105"
        >
          Apply
        </button>
        {(customerRefInput || typeInput || matchStatusInput) && (
          <button
            type="button"
            onClick={clearFilters}
            className="rounded-md border border-border px-3 py-1.5 text-xs font-medium transition hover:bg-accent"
          >
            Clear
          </button>
        )}
        <span className="ml-auto text-xs text-muted-foreground">Amounts stored in kobo · shown in naira</span>
      </form>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Received</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">Payer</th>
              <th className="px-4 py-3 font-medium">Reference</th>
              <th className="px-4 py-3 font-medium">Match</th>
              <th className="px-4 py-3 text-right font-medium">Amount</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">Loading…</td>
              </tr>
            ) : !data || data.content.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">No transactions found.</td>
              </tr>
            ) : (
              data.content.map((t) => (
                <tr key={t.reference} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 text-muted-foreground">{new Date(t.date).toLocaleString()}</td>
                  <td className="px-4 py-3">{t.type}</td>
                  <td className="px-4 py-3">
                    {t.customerReference ?? <span className="text-muted-foreground">— unattributed</span>}
                  </td>
                  <td className="px-4 py-3">{t.payer ?? "—"}</td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{t.reference}</td>
                  <td className="px-4 py-3"><span className={`db dot ${statusClass(t.matchStatus)}`}>{t.matchStatus}</span></td>
                  <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(t.amountKobo)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Page {data.number + 1} of {data.totalPages} · {data.totalElements} total
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={data.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { customerApi, type CustomerDetail, type StatementSummary, type StatementPage } from "@/lib/api";
import { naira, statusClass } from "@/lib/utils";
import { IconArrowLeft } from "@/components/icons";

const MATCH_STATUSES = ["MATCHED", "UNMATCHED", "DISCREPANCY", "ORPHANED", "MANUAL_REVIEW"];
const PAGE_SIZE = 20;

export default function CustomerDetailPage() {
  // The route param is the merchant's own customer reference — Cyrus has no internal customer
  // list endpoint yet, so there's no separate opaque id to route by.
  const params = useParams<{ id: string }>();
  const reference = decodeURIComponent(params.id);

  const [customer, setCustomer] = useState<CustomerDetail | null>(null);
  const [summary, setSummary] = useState<StatementSummary | null>(null);
  const [transactions, setTransactions] = useState<StatementPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  // Draft state bound to the filter inputs — kept separate from the applied state below so
  // picking a date doesn't refetch until "Apply" is actually clicked.
  const [fromInput, setFromInput] = useState("");
  const [toInput, setToInput] = useState("");
  const [matchStatusInput, setMatchStatusInput] = useState("");

  // Applied state — only this feeds `load`'s dependency array / the actual API call.
  const [appliedFrom, setAppliedFrom] = useState("");
  const [appliedTo, setAppliedTo] = useState("");
  const [appliedMatchStatus, setAppliedMatchStatus] = useState("");
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await customerApi.getStatement(reference, {
        // Start of the selected day (UTC) — "from" only needs a lower bound.
        from: appliedFrom ? new Date(`${appliedFrom}T00:00:00.000Z`).toISOString() : undefined,
        // End of the selected day (UTC), not midnight — otherwise "to: 5 Jul" would exclude
        // almost all of the 5th itself, since the backend filter is inclusive (`receivedAt <= to`).
        to: appliedTo ? new Date(`${appliedTo}T23:59:59.999Z`).toISOString() : undefined,
        matchStatus: appliedMatchStatus || undefined,
        page,
        size: PAGE_SIZE,
      });
      setCustomer(res.data.customer);
      setSummary(res.data.summary);
      setTransactions(res.data.transactions);
    } catch (e) {
      if (e instanceof Error && e.message.includes("404")) {
        setNotFound(true);
      } else {
        setError(e instanceof Error ? e.message : "Failed to load customer");
      }
    } finally {
      setLoading(false);
    }
  }, [reference, appliedFrom, appliedTo, appliedMatchStatus, page]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  function applyFilters(e: React.FormEvent) {
    e.preventDefault();
    setAppliedFrom(fromInput);
    setAppliedTo(toInput);
    setAppliedMatchStatus(matchStatusInput);
    setPage(0);
  }

  function clearFilters() {
    setFromInput("");
    setToInput("");
    setMatchStatusInput("");
    setAppliedFrom("");
    setAppliedTo("");
    setAppliedMatchStatus("");
    setPage(0);
  }

  if (notFound) {
    return (
      <div className="space-y-4">
        <Link
          href="/ops/customers"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-primary"
        >
          <IconArrowLeft className="size-4" /> All customers
        </Link>
        <div className="rounded-xl border border-border bg-card p-10 text-center text-sm text-muted-foreground">
          No customer found for reference <span className="font-mono">{reference}</span>.
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/ops/customers"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-primary"
      >
        <IconArrowLeft className="size-4" /> All customers
      </Link>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div>
        <h1 className="text-2xl font-semibold">
          {customer ? `${customer.firstName} ${customer.lastName ?? ""}`.trim() : loading ? "Loading…" : reference}
        </h1>
        <p className="mt-1 font-mono text-sm text-muted-foreground">reference {reference}</p>
      </div>

      {customer && (
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="rounded-xl border border-border bg-card p-5">
            <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Identity</p>
            <dl className="mt-3 grid grid-cols-[90px_1fr] gap-x-3 gap-y-2.5 text-sm">
              <dt className="text-muted-foreground">Status</dt>
              <dd><span className={`db dot ${statusClass(customer.status)}`}>{customer.status}</span></dd>
              <dt className="text-muted-foreground">KYC tier</dt>
              <dd><span className={`db ${statusClass(customer.kycTier)}`}>{customer.kycTier}</span></dd>
              <dt className="text-muted-foreground">Email</dt>
              <dd className="text-muted-foreground">{customer.email ?? "—"}</dd>
              <dt className="text-muted-foreground">Created</dt>
              <dd className="text-muted-foreground">{new Date(customer.createdAt).toLocaleDateString()}</dd>
            </dl>
          </div>

          <div className="rounded-xl border border-border bg-card p-5">
            <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Virtual account</p>
            <dl className="mt-3 grid grid-cols-[90px_1fr] gap-x-3 gap-y-2.5 text-sm">
              <dt className="text-muted-foreground">Number</dt>
              <dd className="font-mono">{customer.virtualAccount.accountNumber}</dd>
              <dt className="text-muted-foreground">Name</dt>
              <dd className="text-muted-foreground">{customer.virtualAccount.accountName ?? "—"}</dd>
              <dt className="text-muted-foreground">Bank</dt>
              <dd className="text-muted-foreground">{customer.virtualAccount.bankName ?? "—"}</dd>
              <dt className="text-muted-foreground">Status</dt>
              <dd><span className={`db dot ${statusClass(customer.virtualAccount.status)}`}>{customer.virtualAccount.status}</span></dd>
            </dl>
          </div>

          <div className="rounded-xl border border-border bg-card p-5">
            <span className="text-xs text-muted-foreground">Received via this account</span>
            <div className="mt-1 text-2xl font-bold tabular-nums">{naira(summary?.lifetimeKobo ?? 0)}</div>
            <div className="text-xs text-muted-foreground">
              {summary?.transactionCount ?? 0} transaction{summary?.transactionCount === 1 ? "" : "s"} · credited to your wallet
            </div>
            <div className="mt-3 border-t border-border pt-3 text-xs text-muted-foreground">
              Last payment {summary?.lastTransactionAt ? new Date(summary.lastTransactionAt).toLocaleString() : "—"}
            </div>
          </div>
        </div>
      )}

      {summary && (summary.pendingCount > 0 || summary.manualReviewCount > 0 || summary.discrepancyCount > 0) && (
        <div className="grid gap-3 sm:grid-cols-3">
          {summary.pendingCount > 0 && (
            <div className="rounded-lg border border-border bg-card px-4 py-3">
              <div className="text-xs text-muted-foreground">Pending</div>
              <div className="mt-1 text-sm font-semibold tabular-nums">
                {summary.pendingCount} · {naira(summary.pendingKobo)}
              </div>
            </div>
          )}
          {summary.discrepancyCount > 0 && (
            <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3">
              <div className="text-xs text-destructive">Discrepancy</div>
              <div className="mt-1 text-sm font-semibold tabular-nums">{summary.discrepancyCount}</div>
            </div>
          )}
          {summary.manualReviewCount > 0 && (
            <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3">
              <div className="text-xs text-destructive">Manual review</div>
              <div className="mt-1 text-sm font-semibold tabular-nums">{summary.manualReviewCount}</div>
            </div>
          )}
        </div>
      )}

      <div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <b className="text-sm">Statement</b>
          <form onSubmit={applyFilters} className="flex flex-wrap items-center gap-2">
            <input
              type="date"
              value={fromInput}
              onChange={(e) => setFromInput(e.target.value)}
              className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
              aria-label="From date"
            />
            <span className="text-xs text-muted-foreground">to</span>
            <input
              type="date"
              value={toInput}
              onChange={(e) => setToInput(e.target.value)}
              className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
              aria-label="To date"
            />
            <select
              value={matchStatusInput}
              onChange={(e) => setMatchStatusInput(e.target.value)}
              className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
              aria-label="Match status"
            >
              <option value="">All statuses</option>
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
            {(fromInput || toInput || matchStatusInput) && (
              <button
                type="button"
                onClick={clearFilters}
                className="rounded-md border border-border px-3 py-1.5 text-xs font-medium transition hover:bg-accent"
              >
                Clear
              </button>
            )}
          </form>
        </div>

        <div className="mt-2.5 overflow-x-auto rounded-xl border border-border bg-card">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Payer</th>
                <th className="px-4 py-3 font-medium">Reference</th>
                <th className="px-4 py-3 font-medium">Match</th>
                <th className="px-4 py-3 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">Loading…</td>
                </tr>
              ) : !transactions || transactions.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">No transactions found.</td>
                </tr>
              ) : (
                transactions.content.map((s) => (
                  <tr key={s.ref ?? s.date} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 text-muted-foreground">{new Date(s.date).toLocaleString()}</td>
                    <td className="px-4 py-3">{s.payer ?? "—"}</td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{s.ref ?? "—"}</td>
                    <td className="px-4 py-3"><span className={`db dot ${statusClass(s.matchStatus)}`}>{s.matchStatus}</span></td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(s.amountKobo)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {transactions && transactions.totalPages > 1 && (
          <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
            <span>
              Page {transactions.number + 1} of {transactions.totalPages} · {transactions.totalElements} total
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={transactions.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={transactions.last}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

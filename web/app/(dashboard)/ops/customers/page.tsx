"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { customerApi, type CustomerListPage } from "@/lib/api";
import { naira, statusClass } from "@/lib/utils";
import { IconArrowRight } from "@/components/icons";

const PAGE_SIZE = 20;

export default function CustomersPage() {
  const router = useRouter();
  const [reference, setReference] = useState("");

  const [data, setData] = useState<CustomerListPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await customerApi.list(page, PAGE_SIZE);
      setData(res.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load customers");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  function lookup(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = reference.trim();
    if (trimmed) router.push(`/ops/customers/${encodeURIComponent(trimmed)}`);
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Customers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Each customer is a persistent identity with a dedicated account number.
          </p>
        </div>
        <span className="db">Provisioned via API — read-only here</span>
      </div>

      <form onSubmit={lookup} className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card p-4">
        <label htmlFor="ref-lookup" className="text-sm font-medium">
          Look up a customer by reference
        </label>
        <input
          id="ref-lookup"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          placeholder="user_123"
          className="min-w-0 flex-1 rounded-md border border-border bg-muted px-3 py-2 font-mono text-sm outline-none focus:border-primary"
        />
        <button
          type="submit"
          className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          View statement <IconArrowRight className="size-3.5" />
        </button>
      </form>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">Reference</th>
              <th className="px-4 py-3 font-medium">Account number</th>
              <th className="px-4 py-3 font-medium">Tier</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 text-right font-medium">Lifetime</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">Loading…</td>
              </tr>
            ) : !data || data.content.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">
                  No customers yet — provision one via the API to see it here.
                </td>
              </tr>
            ) : (
              data.content.map((c) => (
                <tr
                  key={c.id}
                  onClick={() => router.push(`/ops/customers/${encodeURIComponent(c.reference)}`)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      router.push(`/ops/customers/${encodeURIComponent(c.reference)}`);
                    }
                  }}
                  tabIndex={0}
                  role="button"
                  aria-label={`View ${c.firstName}`}
                  className="cursor-pointer border-b border-border transition-colors last:border-0 hover:bg-accent/50 focus-visible:bg-accent/50 focus-visible:outline-none"
                >
                  <td className="px-4 py-3">
                    <div className="font-medium">{c.firstName}{c.lastName ? ` ${c.lastName}` : ""}</div>
                    <div className="font-mono text-xs text-muted-foreground">{c.email ?? "—"}</div>
                  </td>
                  <td className="px-4 py-3 font-mono text-muted-foreground">{c.reference}</td>
                  <td className="px-4 py-3 font-mono">{c.virtualAccount?.accountNumber ?? "—"}</td>
                  <td className="px-4 py-3"><span className={`db ${statusClass(c.kycTier)}`}>{c.kycTier}</span></td>
                  <td className="px-4 py-3"><span className={`db dot ${statusClass(c.status)}`}>{c.status}</span></td>
                  <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(c.lifetimeKobo)}</td>
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

"use client";

import { Fragment, useCallback, useEffect, useState } from "react";
import { paymentEventApi, type PaymentEventItem } from "@/lib/api";
import { naira, statusClass } from "@/lib/utils";

const TABS = [
  { id: "attention", label: "Needs attention", status: "IGNORED" },
  { id: "reattributed", label: "Reattributed", status: "REATTRIBUTED" },
  { id: "all", label: "All", status: undefined },
] as const;

// Replaying an already-resolved event (PROCESSED/REATTRIBUTED) doesn't create a duplicate
// transaction — the backend's idempotency check catches that — but it does silently overwrite the
// event's status to PROCESSED_DUPLICATE, discarding the original success marker. Only offer Replay
// where re-running ingestion is actually the useful action.
const REPLAYABLE_STATUSES = new Set(["RECEIVED", "IGNORED", "FAILED"]);

export default function ReconciliationPage() {
  const [tab, setTab] = useState<(typeof TABS)[number]["id"]>("attention");
  const [events, setEvents] = useState<PaymentEventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // A Set (not a single id) so one row's in-flight request doesn't accidentally re-enable another
  // row's buttons — a single shared "busy" value would let a second click on a different row wipe
  // out the first row's disabled state before its request finishes.
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());
  const [reattributingId, setReattributingId] = useState<string | null>(null);
  const [reattributeRef, setReattributeRef] = useState("");

  function setBusy(id: string, busy: boolean) {
    setBusyIds((prev) => {
      const next = new Set(prev);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const status = TABS.find((t) => t.id === tab)?.status;
      const res = await paymentEventApi.list(status);
      setEvents(res.data.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load payment events");
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  async function replay(id: string) {
    setBusy(id, true);
    setError(null);
    try {
      await paymentEventApi.replay(id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to replay event");
    } finally {
      setBusy(id, false);
    }
  }

  function startReattribute(id: string) {
    setReattributingId(id);
    setReattributeRef("");
    setError(null);
  }

  async function confirmReattribute(id: string) {
    if (!reattributeRef.trim()) {
      setError("Enter the customer reference this payment belongs to.");
      return;
    }
    setBusy(id, true);
    setError(null);
    try {
      await paymentEventApi.reattribute(id, reattributeRef.trim());
      setReattributingId(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to reattribute event");
    } finally {
      setBusy(id, false);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Reconciliation &amp; exceptions</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Raw inbound payment events — orphaned/misdirected payments awaiting reattribution, and anything else
          that didn&apos;t cleanly attribute to a customer.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="inline-flex gap-1 rounded-lg border border-border bg-muted p-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            aria-pressed={tab === t.id}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
              tab === t.id ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Received</th>
              <th className="px-4 py-3 font-medium">Event</th>
              <th className="px-4 py-3 font-medium">Reason</th>
              <th className="px-4 py-3 font-medium">Account / customer</th>
              <th className="px-4 py-3 text-right font-medium">Amount</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">Loading…</td>
              </tr>
            ) : events.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">
                  {tab === "attention" ? "Nothing needs attention right now ✓" : "No events found."}
                </td>
              </tr>
            ) : (
              events.map((e) => (
                <Fragment key={e.id}>
                  <tr className="border-b border-border last:border-0">
                    <td className="px-4 py-3 text-muted-foreground">{new Date(e.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-3">
                      <span className={`db dot ${statusClass(e.status)}`}>{e.status}</span>
                      <div className="mt-0.5 text-xs text-muted-foreground">{e.eventType}</div>
                    </td>
                    <td className="px-4 py-3">
                      {e.failureReason && <span className={`db ${statusClass(e.failureReason)}`}>{e.failureReason}</span>}
                      {e.statusDetails && <div className="mt-1 max-w-xs text-xs text-muted-foreground">{e.statusDetails}</div>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-mono text-xs">{e.accountNumber ?? "—"}</div>
                      {e.customerReference && <div className="mt-0.5 text-xs text-muted-foreground">{e.customerReference}</div>}
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">
                      {e.amount != null ? naira(e.amount) : "—"}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        {e.status === "IGNORED" && (
                          <button
                            type="button"
                            onClick={() => startReattribute(e.id)}
                            disabled={busyIds.has(e.id)}
                            className="rounded-md bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
                          >
                            Reattribute
                          </button>
                        )}
                        {REPLAYABLE_STATUSES.has(e.status) && (
                          <button
                            type="button"
                            onClick={() => replay(e.id)}
                            disabled={busyIds.has(e.id)}
                            className="rounded-md border border-border px-2.5 py-1 text-xs font-medium transition hover:bg-accent disabled:opacity-60"
                          >
                            {busyIds.has(e.id) ? "…" : "Replay"}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {reattributingId === e.id && (
                    <tr className="border-b border-border bg-muted/30 last:border-0">
                      <td colSpan={6} className="px-4 py-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <label htmlFor={`reattribute-${e.id}`} className="text-xs font-medium">
                            Attribute to customer reference:
                          </label>
                          <input
                            id={`reattribute-${e.id}`}
                            value={reattributeRef}
                            onChange={(ev) => setReattributeRef(ev.target.value)}
                            placeholder="user_123"
                            className="rounded-md border border-border bg-card px-2.5 py-1.5 font-mono text-xs outline-none focus:border-primary"
                          />
                          <button
                            type="button"
                            onClick={() => confirmReattribute(e.id)}
                            disabled={busyIds.has(e.id)}
                            className="rounded-md bg-primary px-2.5 py-1 text-xs font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
                          >
                            {busyIds.has(e.id) ? "Attributing…" : "Confirm"}
                          </button>
                          <button
                            type="button"
                            onClick={() => setReattributingId(null)}
                            className="rounded-md border border-border px-2.5 py-1 text-xs font-medium transition hover:bg-accent"
                          >
                            Cancel
                          </button>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

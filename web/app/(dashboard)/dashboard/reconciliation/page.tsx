"use client";

import { useState } from "react";
import { EXCEPTIONS } from "@/lib/mock";
import { naira, statusClass } from "@/lib/utils";

const TABS = [
  { id: "attention", label: "Needs attention" },
  { id: "orphaned", label: "Orphaned" },
  { id: "resolved", label: "Resolved" },
] as const;

export default function ReconciliationPage() {
  const [tab, setTab] = useState<(typeof TABS)[number]["id"]>("attention");

  const rows =
    tab === "resolved" ? [] : tab === "orphaned" ? EXCEPTIONS.filter((e) => e.type === "ORPHANED") : EXCEPTIONS;

  const summary = [
    { label: "Matched", value: 127, cls: "text-green-600 dark:text-green-400", sub: "96.2%" },
    { label: "Partial", value: 2, cls: "text-amber-600 dark:text-amber-400", sub: "amount mismatch" },
    { label: "Orphaned", value: 2, cls: "text-red-600 dark:text-red-400", sub: "no matching account" },
    { label: "Missing webhook", value: 1, cls: "text-amber-600 dark:text-amber-400", sub: "found on requery" },
  ];

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Reconciliation &amp; exceptions</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Cyrus compares the Nomba records against internal state and surfaces anything that does not line up.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {summary.map((s) => (
          <div key={s.label} className="rounded-xl border border-border bg-card p-4">
            <div className="text-xs text-muted-foreground">{s.label}</div>
            <div className={`mt-2 text-2xl font-bold tabular-nums ${s.cls}`}>{s.value}</div>
            <div className="mt-0.5 text-xs text-muted-foreground">{s.sub}</div>
          </div>
        ))}
      </div>

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
            {t.id === "attention" && ` (${EXCEPTIONS.length})`}
          </button>
        ))}
      </div>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Detail</th>
              <th className="px-4 py-3 font-medium">Payer</th>
              <th className="px-4 py-3 font-medium">Provider ref</th>
              <th className="px-4 py-3 text-right font-medium">Amount</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">All reconciled ✓</td>
              </tr>
            ) : (
              rows.map((e) => (
                <tr key={e.ref + e.detail} className="border-b border-border last:border-0">
                  <td className="px-4 py-3"><span className={`db dot ${statusClass(e.type)}`}>{e.type}</span></td>
                  <td className="px-4 py-3">{e.detail}</td>
                  <td className="px-4 py-3">{e.payer}</td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{e.ref}</td>
                  <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(e.amountKobo)}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${
                        e.action === "Ingest"
                          ? "bg-primary text-primary-foreground hover:brightness-105"
                          : "border border-border hover:bg-accent"
                      }`}
                    >
                      {e.action}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

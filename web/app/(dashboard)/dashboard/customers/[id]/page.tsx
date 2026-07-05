"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { CUSTOMERS } from "@/lib/mock";
import { naira, statusClass } from "@/lib/utils";
import { IconArrowLeft } from "@/components/icons";

export default function CustomerDetailPage() {
  const params = useParams<{ id: string }>();
  const customer = CUSTOMERS.find((c) => c.id === params.id) ?? CUSTOMERS[0];
  const last = customer.statement[0];

  return (
    <div className="space-y-6">
      <Link
        href="/dashboard/customers"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-primary"
      >
        <IconArrowLeft className="size-4" /> All customers
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{customer.name}</h1>
          <p className="mt-1 font-mono text-sm text-muted-foreground">
            {customer.id} · external {customer.externalId}
          </p>
        </div>
        <div className="flex gap-2">
          <button type="button" className="rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-accent">
            Rename
          </button>
          <button type="button" className="rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-accent">
            {customer.status === "ACTIVE" ? "Suspend account" : "Reactivate"}
          </button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-border bg-card p-5">
          <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Identity</p>
          <dl className="mt-3 grid grid-cols-[90px_1fr] gap-x-3 gap-y-2.5 text-sm">
            <dt className="text-muted-foreground">Status</dt>
            <dd><span className={`db dot ${statusClass(customer.status)}`}>{customer.status}</span></dd>
            <dt className="text-muted-foreground">KYC tier</dt>
            <dd><span className={`db ${statusClass(customer.tier)}`}>{customer.tier}</span></dd>
            <dt className="text-muted-foreground">Email</dt>
            <dd className="text-muted-foreground">{customer.email}</dd>
            <dt className="text-muted-foreground">Created</dt>
            <dd className="text-muted-foreground">{customer.createdAt}</dd>
          </dl>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Virtual account</p>
          <dl className="mt-3 grid grid-cols-[90px_1fr] gap-x-3 gap-y-2.5 text-sm">
            <dt className="text-muted-foreground">Number</dt>
            <dd className="font-mono">{customer.accountNumber}</dd>
            <dt className="text-muted-foreground">Name</dt>
            <dd className="text-muted-foreground">{customer.name} / Acme</dd>
            <dt className="text-muted-foreground">Bank</dt>
            <dd className="text-muted-foreground">Nomba MFB</dd>
            <dt className="text-muted-foreground">Status</dt>
            <dd><span className={`db dot ${statusClass(customer.status)}`}>{customer.status}</span></dd>
          </dl>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <span className="text-xs text-muted-foreground">Lifetime received</span>
          <div className="mt-1 text-2xl font-bold tabular-nums">{naira(customer.lifetimeKobo)}</div>
          <div className="text-xs text-muted-foreground">{customer.statement.length} transfers · all reconciled</div>
          <div className="mt-3 border-t border-border pt-3 text-xs text-muted-foreground">
            Last payment {last ? `${naira(last.amountKobo)} · ${last.date}` : "—"}
          </div>
        </div>
      </div>

      <div>
        <b className="text-sm">Statement</b>
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
              {customer.statement.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">No transactions yet.</td>
                </tr>
              ) : (
                customer.statement.map((s) => (
                  <tr key={s.ref} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 text-muted-foreground">{s.date}</td>
                    <td className="px-4 py-3">{s.payer}</td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{s.ref}</td>
                    <td className="px-4 py-3"><span className={`db dot ${statusClass(s.match)}`}>{s.match}</span></td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(s.amountKobo)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

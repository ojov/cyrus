"use client";

import { useRouter } from "next/navigation";
import { CUSTOMERS } from "@/lib/mock";
import { naira, statusClass } from "@/lib/utils";

export default function CustomersPage() {
  const router = useRouter();

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

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">External ID</th>
              <th className="px-4 py-3 font-medium">Account number</th>
              <th className="px-4 py-3 font-medium">Tier</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 text-right font-medium">Lifetime</th>
            </tr>
          </thead>
          <tbody>
            {CUSTOMERS.map((c) => (
              <tr
                key={c.id}
                onClick={() => router.push(`/dashboard/customers/${c.id}`)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    router.push(`/dashboard/customers/${c.id}`);
                  }
                }}
                tabIndex={0}
                role="button"
                aria-label={`View ${c.name}`}
                className="cursor-pointer border-b border-border transition-colors last:border-0 hover:bg-accent/50 focus-visible:bg-accent/50 focus-visible:outline-none"
              >
                <td className="px-4 py-3">
                  <div className="font-medium">{c.name}</div>
                  <div className="font-mono text-xs text-muted-foreground">{c.id}</div>
                </td>
                <td className="px-4 py-3 font-mono text-muted-foreground">{c.externalId}</td>
                <td className="px-4 py-3 font-mono">{c.accountNumber}</td>
                <td className="px-4 py-3"><span className={`db ${statusClass(c.tier)}`}>{c.tier}</span></td>
                <td className="px-4 py-3"><span className={`db dot ${statusClass(c.status)}`}>{c.status}</span></td>
                <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(c.lifetimeKobo)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

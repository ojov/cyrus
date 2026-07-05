"use client";

import { TRANSACTIONS } from "@/lib/mock";
import { naira, statusClass } from "@/lib/utils";

export default function TransactionsPage() {
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Transactions</h1>
        <p className="mt-1 text-sm text-muted-foreground">Every inbound transfer, attributed to a customer identity.</p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="db db-good dot">SUCCESSFUL</span>
        <span className="db">All accounts</span>
        <span className="ml-auto text-xs text-muted-foreground">Amounts stored in kobo · shown in naira</span>
      </div>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-4 py-3 font-medium">Received</th>
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">Payer</th>
              <th className="px-4 py-3 font-medium">Provider ref</th>
              <th className="px-4 py-3 font-medium">Match</th>
              <th className="px-4 py-3 text-right font-medium">Amount</th>
            </tr>
          </thead>
          <tbody>
            {TRANSACTIONS.map((t) => (
              <tr key={t.ref} className="border-b border-border last:border-0">
                <td className="px-4 py-3 text-muted-foreground">{t.date}</td>
                <td className="px-4 py-3">{t.customer ?? <span className="text-muted-foreground">— unattributed</span>}</td>
                <td className="px-4 py-3">{t.payer}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{t.ref}</td>
                <td className="px-4 py-3"><span className={`db dot ${statusClass(t.match)}`}>{t.match}</span></td>
                <td className="px-4 py-3 text-right font-medium tabular-nums">{naira(t.amountKobo)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

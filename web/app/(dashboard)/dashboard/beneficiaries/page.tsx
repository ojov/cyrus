"use client";

import { useCallback, useEffect, useState } from "react";
import { beneficiaryApi, type BeneficiaryItem } from "@/lib/api";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 text-sm outline-none focus:border-primary";

export default function BeneficiariesPage() {
  const [items, setItems] = useState<BeneficiaryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ nickname: "", accountNumber: "", bankCode: "", bankName: "" });

  const load = useCallback(async () => {
    try {
      const res = await beneficiaryApi.list();
      setItems(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load beneficiaries");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  function set(key: keyof typeof form) {
    return (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));
  }

  async function create() {
    if (!form.nickname.trim() || !form.accountNumber.trim() || !form.bankCode.trim() || !form.bankName.trim()) {
      setError("Fill in every field.");
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await beneficiaryApi.create(form);
      setForm({ nickname: "", accountNumber: "", bankCode: "", bankName: "" });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add beneficiary");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Beneficiaries</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Bank accounts you can pay out to. The account name is verified against Nomba when you add one.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-4">
        <b className="text-sm">Add a beneficiary</b>
        <div className="mt-3 grid grid-cols-2 gap-3">
          <input className={field} placeholder="Nickname (e.g. Main GTBank)" value={form.nickname} onChange={set("nickname")} />
          <input className={field} placeholder="Account number" value={form.accountNumber} onChange={set("accountNumber")} />
          <input className={field} placeholder="Bank code (NIP)" value={form.bankCode} onChange={set("bankCode")} />
          <input className={field} placeholder="Bank name" value={form.bankName} onChange={set("bankName")} />
        </div>
        <button
          type="button"
          onClick={create}
          disabled={creating}
          className="mt-3 rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {creating ? "Adding…" : "+ Add beneficiary"}
        </button>
      </div>

      <div className="rounded-xl border border-border bg-card">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold">Your beneficiaries</div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">Loading…</p>
        ) : items.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">No beneficiaries yet. Add one above.</p>
        ) : (
          <div className="divide-y divide-border">
            {items.map((b) => (
              <div key={b.id} className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <b className="text-sm">{b.nickname}</b>
                  <span className="text-xs text-muted-foreground">{b.bankName}</span>
                </div>
                <p className="mt-0.5 font-mono text-xs text-muted-foreground">
                  {b.accountNumber} · {b.accountName}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

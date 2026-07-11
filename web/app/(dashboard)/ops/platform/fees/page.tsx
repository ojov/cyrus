"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, platformApi, type FeeConfig } from "@/lib/api";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 font-mono text-[13px] outline-none focus:border-primary";

export default function FeesPage() {
  const [config, setConfig] = useState<FeeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const [inflowPercent, setInflowPercent] = useState("");
  const [inflowMinKobo, setInflowMinKobo] = useState("");
  const [inflowMaxKobo, setInflowMaxKobo] = useState("");
  const [payoutFlatFeeKobo, setPayoutFlatFeeKobo] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await platformApi.getFees();
      const c = res.data;
      setConfig(c);
      setInflowPercent(String(c.inflowPercent));
      setInflowMinKobo(String(c.inflowMinKobo));
      setInflowMaxKobo(String(c.inflowMaxKobo));
      setPayoutFlatFeeKobo(String(c.payoutFlatFeeKobo));
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setForbidden(true);
      else setMsg({ ok: false, text: e instanceof Error ? e.message : "Failed to load fee configuration" });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  async function save() {
    setSaving(true);
    setMsg(null);
    try {
      const percent = parseFloat(inflowPercent);
      const min = parseInt(inflowMinKobo, 10);
      const max = parseInt(inflowMaxKobo, 10);
      const payout = parseInt(payoutFlatFeeKobo, 10);

      if (isNaN(percent) || percent <= 0) {
        setMsg({ ok: false, text: "Inflow percent must be a positive number." });
        return;
      }
      if (isNaN(min) || min < 0) {
        setMsg({ ok: false, text: "Inflow min must be a non-negative integer." });
        return;
      }
      if (isNaN(max) || max <= 0) {
        setMsg({ ok: false, text: "Inflow max must be a positive integer." });
        return;
      }
      if (min > max) {
        setMsg({ ok: false, text: "Inflow min must not exceed inflow max." });
        return;
      }
      if (isNaN(payout) || payout < 0) {
        setMsg({ ok: false, text: "Payout flat fee must be a non-negative integer." });
        return;
      }

      const res = await platformApi.updateFees({
        inflowPercent: percent,
        inflowMinKobo: min,
        inflowMaxKobo: max,
        payoutFlatFeeKobo: payout,
      });
      setConfig(res.data);
      setMsg({ ok: true, text: "Fee configuration updated. Changes take effect immediately." });
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "Failed to update fee configuration" });
    } finally {
      setSaving(false);
    }
  }

  if (forbidden) {
    return (
      <div className="rounded-xl border border-border bg-card p-10 text-center text-sm text-muted-foreground">
        You don&apos;t have access to fee configuration.
      </div>
    );
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Fee configuration</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Platform-wide fee settings — applies to all merchants. Super-admin only.
        </p>
      </div>

      <div className="rounded-xl border border-border bg-card p-5">
        <b className="text-sm">Inbound payments</b>
        <p className="mt-1 mb-4 text-xs text-muted-foreground">
          Merchant fee = inflowPercent of gross amount, clamped to [inflowMin, inflowMax].
          Nomba&apos;s own processing fee (1% min &#x20A6;10, max &#x20A6;150) is tracked separately; Cyrus&apos;s margin = merchant fee − Nomba fee.
        </p>
        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Inflow percent (%)</label>
              <input
                className={field}
                type="number"
                step="0.01"
                value={inflowPercent}
                onChange={(e) => setInflowPercent(e.target.value)}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Min fee (kobo)</label>
              <input
                className={field}
                type="number"
                value={inflowMinKobo}
                onChange={(e) => setInflowMinKobo(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">&#x20A6;{(Number(inflowMinKobo) / 100).toFixed(2)}</p>
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Max fee (kobo)</label>
              <input
                className={field}
                type="number"
                value={inflowMaxKobo}
                onChange={(e) => setInflowMaxKobo(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">&#x20A6;{(Number(inflowMaxKobo) / 100).toFixed(2)}</p>
            </div>
          </div>
        )}
      </div>

      <div className="rounded-xl border border-border bg-card p-5">
        <b className="text-sm">Outbound payouts</b>
        <p className="mt-1 mb-4 text-xs text-muted-foreground">
          Flat fee per transfer. Nomba&apos;s &#x20A6;20 fixed charge is deducted from the transfer itself;
          the merchant is charged this flat rate on top.
        </p>
        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <div className="max-w-xs">
            <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Payout flat fee (kobo)</label>
            <input
              className={field}
              type="number"
              value={payoutFlatFeeKobo}
              onChange={(e) => setPayoutFlatFeeKobo(e.target.value)}
            />
            <p className="mt-1 text-[11px] text-muted-foreground">&#x20A6;{(Number(payoutFlatFeeKobo) / 100).toFixed(2)}</p>
          </div>
        )}
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={save}
          disabled={saving || loading}
          className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {saving ? "Saving…" : "Save changes"}
        </button>
        {config?.updatedAt && (
          <span className="text-xs text-muted-foreground">
            Last updated: {new Date(config.updatedAt).toLocaleString()}
          </span>
        )}
      </div>
      {msg && (
        <p className={`text-sm ${msg.ok ? "text-green-600 dark:text-green-400" : "text-destructive"}`}>{msg.text}</p>
      )}
    </div>
  );
}

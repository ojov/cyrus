"use client";

import { useCallback, useEffect, useState } from "react";
import { authApi, webhookApi, type WebhookConfigItem, type WebhookDeliveryItem } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { statusClass } from "@/lib/utils";
import { CopyButton } from "@/components/ui/copy-button";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 font-mono text-[13px] outline-none focus:border-primary";

const DELIVERY_STATUSES = ["PENDING", "DELIVERED", "RETRYING", "FAILED"];

export default function SettingsPage() {
  const [config, setConfig] = useState<WebhookConfigItem | null>(null);
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [resetSent, setResetSent] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);

  const [deliveries, setDeliveries] = useState<WebhookDeliveryItem[]>([]);
  const [deliveriesLoading, setDeliveriesLoading] = useState(true);
  const [deliveryStatus, setDeliveryStatus] = useState("");

  const load = useCallback(async () => {
    try {
      const res = await webhookApi.get();
      setConfig(res.data);
      setUrl(res.data?.url ?? "");
    } catch {
      // No webhook configured yet is not an error state worth surfacing here.
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDeliveries = useCallback(async () => {
    setDeliveriesLoading(true);
    try {
      const res = await webhookApi.deliveries(deliveryStatus || undefined);
      setDeliveries(res.data.content);
    } catch {
      // Same as above — an empty/failed delivery history isn't worth a page-level error banner.
    } finally {
      setDeliveriesLoading(false);
    }
  }, [deliveryStatus]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  useEffect(() => {
    Promise.resolve().then(loadDeliveries);
  }, [loadDeliveries]);

  async function saveWebhook() {
    if (!url.trim()) {
      setMsg({ ok: false, text: "Enter a webhook URL." });
      return;
    }
    setSaving(true);
    setMsg(null);
    try {
      const res = await webhookApi.set(url.trim());
      if (res.data.secret) setRevealedSecret(res.data.secret);
      setMsg({ ok: true, text: "Webhook saved." });
      await load();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "Failed to save webhook" });
    } finally {
      setSaving(false);
    }
  }

  async function rotateSecret() {
    setRotating(true);
    setMsg(null);
    try {
      const res = await webhookApi.rotateSecret();
      if (res.data.secret) setRevealedSecret(res.data.secret);
      setMsg({ ok: true, text: "Signing secret rotated." });
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "Failed to rotate secret" });
    } finally {
      setRotating(false);
    }
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">Configure the webhook Cyrus delivers payment and payout events to.</p>
      </div>

      <div className="rounded-xl border border-border bg-card p-5">
        <div className="mb-3.5 flex items-center justify-between">
          <b className="text-sm">Webhook</b>
          {config?.hasSecret && <span className="db db-good dot">Configured</span>}
        </div>
        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <>
            <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Endpoint URL</label>
            <input
              className={`${field} mb-3`}
              placeholder="https://acme.ng/webhooks/cyrus"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
            />
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={saveWebhook}
                disabled={saving}
                className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
              >
                {saving ? "Saving…" : "Save"}
              </button>
              {config?.hasSecret && (
                <button
                  type="button"
                  onClick={rotateSecret}
                  disabled={rotating}
                  className="rounded-md border border-border px-3 py-2 text-sm font-medium transition hover:bg-accent disabled:opacity-60"
                >
                  {rotating ? "Rotating…" : "Rotate secret"}
                </button>
              )}
            </div>
            {revealedSecret && (
              <div className="mt-3 rounded-lg border border-primary/40 bg-primary/5 p-3">
                <div className="mb-1.5 flex items-center gap-2">
                  <b className="text-xs">Signing secret</b>
                  <span className="db db-warn">shown once</span>
                </div>
                <div className="flex items-center gap-2">
                  <code className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap rounded-md border border-border bg-background px-3 py-2 font-mono text-xs">
                    {revealedSecret}
                  </code>
                  <CopyButton text={revealedSecret} />
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  Store this now — Cyrus cannot show it again. Verify inbound deliveries with HMAC-SHA256 over the raw body.
                </p>
              </div>
            )}
            {msg && (
              <p className={`mt-3 text-sm ${msg.ok ? "text-green-600 dark:text-green-400" : "text-destructive"}`}>{msg.text}</p>
            )}
          </>
        )}
      </div>

      <div className="rounded-xl border border-border bg-card p-5">
        <div className="mb-3.5 flex flex-wrap items-center justify-between gap-2">
          <b className="text-sm">Delivery history</b>
          <select
            value={deliveryStatus}
            onChange={(e) => setDeliveryStatus(e.target.value)}
            className="rounded-md border border-border bg-muted px-2.5 py-1.5 text-xs outline-none focus:border-primary"
          >
            <option value="">All statuses</option>
            {DELIVERY_STATUSES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/50 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                <th className="px-3 py-2 font-medium">Sent</th>
                <th className="px-3 py-2 font-medium">Event</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Attempts</th>
                <th className="px-3 py-2 font-medium">Response</th>
              </tr>
            </thead>
            <tbody>
              {deliveriesLoading ? (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-sm text-muted-foreground">Loading…</td>
                </tr>
              ) : deliveries.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-sm text-muted-foreground">No deliveries yet.</td>
                </tr>
              ) : (
                deliveries.map((d) => (
                  <tr key={d.id} className="border-b border-border last:border-0">
                    <td className="px-3 py-2 text-muted-foreground">{new Date(d.createdAt).toLocaleString()}</td>
                    <td className="px-3 py-2 font-mono text-xs">{d.eventType}</td>
                    <td className="px-3 py-2"><span className={`db dot ${statusClass(d.status)}`}>{d.status}</span></td>
                    <td className="px-3 py-2 tabular-nums">{d.attempts}</td>
                    <td className="px-3 py-2 text-xs text-muted-foreground">
                      {d.lastResponseCode ?? "—"}
                      {d.lastError && <span className="ml-1.5 text-destructive">{d.lastError}</span>}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card p-5">
        <div className="mb-1.5 flex items-center justify-between">
          <b className="text-sm">Change password</b>
        </div>
        <p className="mb-4 text-sm text-muted-foreground">
          A reset link will be sent to your business email. It expires in 15 minutes.
        </p>
        <button
          type="button"
          onClick={async () => {
            const session = getSession();
            if (!session) {
              setResetError("Your session expired. Please sign in again.");
              return;
            }
            setResetting(true);
            setResetError(null);
            try {
              await authApi.forgotPassword(session.businessEmail);
              setResetSent(true);
            } catch (e) {
              setResetError(e instanceof Error ? e.message : "Failed to send reset link");
            } finally {
              setResetting(false);
            }
          }}
          disabled={resetting || resetSent}
          className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {resetting ? "Sending…" : resetSent ? "Email sent" : "Send reset link"}
        </button>
        {resetError && <p className="mt-2 text-sm text-destructive">{resetError}</p>}
      </div>
    </div>
  );
}

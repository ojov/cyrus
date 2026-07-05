"use client";

import { useEffect, useState } from "react";
import { authApi, dashboardApi, DashboardStats } from "@/lib/api";
import { getSession } from "@/lib/auth";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 font-mono text-[13px] outline-none focus:border-primary";

export default function SettingsPage() {
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [liveMode, setLiveMode] = useState(false);
  const [resetSent, setResetSent] = useState(false);
  const [resetting, setResetting] = useState(false);

  useEffect(() => {
    Promise.resolve().then(() =>
      dashboardApi.stats().then((r: DashboardStats) => setLiveMode(r.data.liveModeActive)).catch(() => {})
    );
  }, []);

  async function goLive() {
    if (!clientId.trim() || !clientSecret.trim()) {
      setMsg({ ok: false, text: "Enter your live client ID and secret." });
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      await dashboardApi.goLive(clientId, clientSecret);
      setLiveMode(true);
      setMsg({ ok: true, text: "Live mode activated. Go to API keys, generate a cyrus_live_ key, and copy it immediately — full keys are shown once." });
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "Go-live failed" });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">Connect your Nomba account, configure webhooks, and take Cyrus live.</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="mb-3.5 flex items-center justify-between">
            <b className="text-sm">Provider connection</b>
            <span className={`db ${liveMode ? "db-good" : "db-info"} dot`}>{liveMode ? "Nomba · live" : "Nomba · sandbox"}</span>
          </div>
          <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-2.5 text-sm">
            <dt className="text-muted-foreground">Provider</dt>
            <dd>Nomba</dd>
            <dt className="text-muted-foreground">Parent account</dt>
            <dd className="font-mono">NMB-48210</dd>
            <dt className="text-muted-foreground">Sub-accounts</dt>
            <dd className="font-mono">sub_acct_1</dd>
            <dt className="text-muted-foreground">Environment</dt>
            <dd><span className={`db ${liveMode ? "db-good" : "db-info"}`}>{liveMode ? "LIVE" : "TEST"}</span></dd>
          </dl>
          <div className="my-4 border-t border-border" />
          <div className="mb-2 flex items-center justify-between">
            <b className="text-sm">Webhook</b>
            <button type="button" className="rounded-md border border-border px-2.5 py-1 text-xs font-medium transition hover:bg-accent">
              Send test event
            </button>
          </div>
          <input className={field} defaultValue="https://acme.ng/webhooks/cyrus" />
          <p className="mt-2 text-xs text-muted-foreground">
            Signing secret · <span className="font-mono">whsec_••••4f2a</span>
          </p>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <div className="mb-1.5 flex items-center justify-between">
            <b className="text-sm">Go live</b>
            <span className={`db ${liveMode ? "db-good" : "db-warn"} dot`}>{liveMode ? "Activated" : "Not activated"}</span>
          </div>
          {liveMode ? (
            <p className="text-sm text-green-600 dark:text-green-400">Live mode is active. You can create live API keys from the API keys page.</p>
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                Add your live Nomba credentials. Parent and sub-accounts are reused — only the client keys differ.
              </p>
              <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Live client ID</label>
              <input
                className={`${field} mb-3`}
                placeholder="nomba_live_client_id"
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
              />
              <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Live client secret</label>
              <input
                className={`${field} mb-4`}
                type="password"
                placeholder="••••••••"
                value={clientSecret}
                onChange={(e) => setClientSecret(e.target.value)}
              />
              <button
                type="button"
                onClick={goLive}
                disabled={busy}
                className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
              >
                {busy ? "Activating…" : "Activate live mode"}
              </button>
              {msg && (
                <p className={`mt-3 text-sm ${msg.ok ? "text-green-600 dark:text-green-400" : "text-destructive"}`}>{msg.text}</p>
              )}
            </>
          )}
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
            setResetting(true);
            try {
              const session = getSession();
              await authApi.forgotPassword(session!.businessEmail);
              setResetSent(true);
            } catch {
              setResetSent(false);
            } finally {
              setResetting(false);
            }
          }}
          disabled={resetting || resetSent}
          className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {resetting ? "Sending…" : resetSent ? "Email sent" : "Send reset link"}
        </button>
      </div>
    </div>
  );
}

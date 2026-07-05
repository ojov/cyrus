"use client";

import { useCallback, useEffect, useState } from "react";
import { dashboardApi, type ApiKeyItem } from "@/lib/api";
import { cn, statusClass } from "@/lib/utils";

const ENVS = ["TEST", "LIVE"] as const;

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [env, setEnv] = useState<(typeof ENVS)[number]>("TEST");
  const [creating, setCreating] = useState(false);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await dashboardApi.listApiKeys();
      setKeys(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load API keys");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Defer off the synchronous effect body; load() sets state after its await.
    Promise.resolve().then(load);
  }, [load]);

  async function create() {
    setCreating(true);
    setError(null);
    try {
      const res = await dashboardApi.createApiKey(env);
      setNewKey(res.data?.apiKeys?.[0]?.apiKey ?? "");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to create key");
    } finally {
      setCreating(false);
    }
  }

  async function revoke(id: string) {
    setError(null);
    try {
      await dashboardApi.revokeApiKey(id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to revoke key");
    }
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">API keys</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Generate, copy, and revoke the keys your developers use to authenticate with the Cyrus API.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      {newKey && (
        <div className="rounded-xl border border-primary/40 bg-primary/5 p-4">
          <div className="mb-2 flex items-center gap-2">
            <b className="text-sm">Copy your key now</b>
            <span className="db db-warn">shown once</span>
          </div>
          <p className="mb-3 text-xs text-muted-foreground">
            Store this full key in your secret manager before closing this message. Cyrus cannot show it again later.
          </p>
          <div className="flex items-center gap-2">
            <code className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap rounded-md border border-border bg-background px-3 py-2 font-mono text-sm">
              {newKey}
            </code>
            <button
              type="button"
              onClick={() => navigator.clipboard.writeText(newKey)}
              className="shrink-0 rounded-md border border-border px-3 py-2 text-sm transition hover:bg-accent"
            >
              Copy
            </button>
            <button
              type="button"
              onClick={() => setNewKey(null)}
              className="shrink-0 rounded-md px-3 py-2 text-sm text-muted-foreground transition hover:text-foreground"
            >
              Done
            </button>
          </div>
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <b className="text-sm">Generate a key</b>
            <div className="text-xs text-muted-foreground">
              Copy the full key when it appears. Existing keys only show their prefix.
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className="inline-flex rounded-md border border-border p-0.5">
              {ENVS.map((e) => (
                <button
                  key={e}
                  type="button"
                  onClick={() => setEnv(e)}
                  className={cn(
                    "rounded px-3 py-1.5 text-sm transition",
                    env === e ? "bg-primary font-medium text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                  )}
                >
                  {e}
                </button>
              ))}
            </div>
            <button
              type="button"
              onClick={create}
              disabled={creating}
              className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
            >
              {creating ? "Creating…" : "+ Generate key"}
            </button>
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold">Your keys</div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">Loading…</p>
        ) : keys.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">
            No API keys yet. Generate one above and copy it immediately.
          </p>
        ) : (
          <div className="divide-y divide-border">
            {keys.map((k) => (
              <div key={k.id} className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <code className="truncate font-mono text-sm">{k.prefix}••••••••</code>
                    <span className="db">{k.environment}</span>
                    <span className={`db dot ${statusClass(k.status)}`}>{k.status}</span>
                  </div>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Created {new Date(k.createdAt).toLocaleDateString()}
                    {k.lastUsedAt ? ` · last used ${new Date(k.lastUsedAt).toLocaleDateString()}` : " · never used"}
                  </p>
                </div>
                {k.status === "ACTIVE" && (
                  <button
                    type="button"
                    onClick={() => revoke(k.id)}
                    className="shrink-0 rounded-md border border-destructive/40 px-2.5 py-1 text-xs font-medium text-destructive transition hover:bg-destructive/10"
                  >
                    Revoke
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

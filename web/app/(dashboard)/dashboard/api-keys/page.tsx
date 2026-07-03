"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Copy, Key, Plus, Trash2, ShieldCheck } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { dashboardApi, type ApiKeyItem } from "@/lib/api";

const ENVS = ["TEST", "LIVE"] as const;
type Env = (typeof ENVS)[number];

function statusVariant(status: string): "secondary" | "outline" | "destructive" {
  if (status === "ACTIVE") return "secondary";
  if (status === "REVOKED") return "destructive";
  return "outline";
}

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [env, setEnv] = useState<Env>("TEST");
  const [creating, setCreating] = useState(false);
  const [newKey, setNewKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await dashboardApi.listApiKeys();
      setKeys(res.data ?? []);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to load API keys");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function create() {
    setCreating(true);
    try {
      const res = await dashboardApi.createApiKey(env);
      setNewKey(res.data?.apiKeys?.[0]?.apiKey ?? "");
      toast.success(`${env} API key created`);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create key");
    } finally {
      setCreating(false);
    }
  }

  async function revoke(id: string) {
    try {
      await dashboardApi.revokeApiKey(id);
      toast.success("API key revoked");
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to revoke key");
    }
  }

  function copy(value: string) {
    navigator.clipboard.writeText(value);
    toast.success("Copied to clipboard");
  }

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">API Keys</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Create and revoke the keys your developers use to authenticate with the Cyrus API.
        </p>
      </div>

      {newKey && (
        <Card className="border-primary/40 bg-primary/5">
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <ShieldCheck className="size-4 text-primary" />
              Copy your new key now
            </CardTitle>
            <CardDescription>
              This is the only time the full key is shown. Store it securely.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex items-center gap-2">
            <code className="flex-1 rounded-md bg-background border border-border px-4 py-2.5 font-mono text-sm overflow-x-auto whitespace-nowrap">
              {newKey}
            </code>
            <Button variant="outline" size="icon" onClick={() => copy(newKey)} title="Copy">
              <Copy className="size-4" />
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setNewKey(null)}>
              Done
            </Button>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Create a key</CardTitle>
          <CardDescription>Live keys require live mode to be activated first.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-3">
          <div className="inline-flex rounded-md border border-border p-0.5">
            {ENVS.map((e) => (
              <button
                key={e}
                onClick={() => setEnv(e)}
                className={`px-3 py-1.5 text-sm rounded transition-colors ${
                  env === e
                    ? "bg-primary text-primary-foreground font-medium"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {e}
              </button>
            ))}
          </div>
          <Button onClick={create} disabled={creating}>
            <Plus className="size-4" />
            {creating ? "Creating…" : "Generate key"}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Key className="size-4 text-primary" />
            Your keys
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className="text-sm text-muted-foreground py-4">Loading…</p>
          ) : keys.length === 0 ? (
            <p className="text-sm text-muted-foreground py-4">No API keys yet. Generate one above.</p>
          ) : (
            <div className="divide-y divide-border">
              {keys.map((k) => (
                <div key={k.id} className="flex items-center justify-between py-3 gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <code className="font-mono text-sm text-foreground truncate">
                        {k.prefix}••••••••
                      </code>
                      <Badge variant="outline" className="text-xs">{k.environment}</Badge>
                      <Badge variant={statusVariant(k.status)} className="text-xs">{k.status}</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Created {new Date(k.createdAt).toLocaleDateString()}
                      {k.lastUsedAt
                        ? ` · last used ${new Date(k.lastUsedAt).toLocaleDateString()}`
                        : " · never used"}
                    </p>
                  </div>
                  {k.status === "ACTIVE" && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive shrink-0"
                      onClick={() => revoke(k.id)}
                    >
                      <Trash2 className="size-4" />
                      Revoke
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

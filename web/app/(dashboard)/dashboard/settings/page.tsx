"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Copy, Rocket, ShieldCheck } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { dashboardApi } from "@/lib/api";

export default function SettingsPage() {
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [loading, setLoading] = useState(false);
  const [liveKey, setLiveKey] = useState<string | null>(null);

  async function activate(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await dashboardApi.goLive(clientId, clientSecret);
      setLiveKey(res.data?.apiKeys?.[0]?.apiKey ?? "");
      toast.success("Live mode activated");
      setClientId("");
      setClientSecret("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not activate live mode");
    } finally {
      setLoading(false);
    }
  }

  function copy(value: string) {
    navigator.clipboard.writeText(value);
    toast.success("Copied to clipboard");
  }

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">Settings</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Activate live mode with your production Nomba credentials.
        </p>
      </div>

      {liveKey && (
        <Card className="border-primary/40 bg-primary/5">
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <ShieldCheck className="size-4 text-primary" />
              Your live API key
            </CardTitle>
            <CardDescription>Shown once — copy and store it securely.</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center gap-2">
            <code className="flex-1 rounded-md bg-background border border-border px-4 py-2.5 font-mono text-sm overflow-x-auto whitespace-nowrap">
              {liveKey}
            </code>
            <Button variant="outline" size="icon" onClick={() => copy(liveKey)} title="Copy">
              <Copy className="size-4" />
            </Button>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Rocket className="size-4 text-primary" />
            Go live
          </CardTitle>
          <CardDescription>
            We verify these with Nomba before issuing your live key. Parent and sub-account IDs are
            shared with test — only the client credentials change.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={activate} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="clientId">Live Nomba client ID</Label>
              <Input
                id="clientId"
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="clientSecret">Live Nomba client secret</Label>
              <Input
                id="clientSecret"
                type="password"
                value={clientSecret}
                onChange={(e) => setClientSecret(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={loading}>
              {loading ? "Verifying…" : "Activate live mode"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Copy, Eye, EyeOff, Key } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

interface StoredKeyInfo {
  apiKey: string;
  merchantId: string;
  environment?: string;
}

export default function ApiKeysPage() {
  const [keyInfo, setKeyInfo] = useState<StoredKeyInfo | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem("cyrus_api_key");
      if (raw) setKeyInfo(JSON.parse(raw));
    } catch {
      // ignore
    }
  }, []);

  function copyKey() {
    if (!keyInfo) return;
    navigator.clipboard.writeText(keyInfo.apiKey);
    toast.success("API key copied to clipboard");
  }

  const masked = keyInfo
    ? keyInfo.apiKey.slice(0, 10) + "••••••••••••••••••••" + keyInfo.apiKey.slice(-4)
    : "";

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">API Keys</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Use these keys to authenticate requests to the Cyrus API.
        </p>
      </div>

      {keyInfo ? (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base flex items-center gap-2">
                  <Key className="size-4 text-primary" />
                  {(keyInfo.environment ?? "TEST") === "LIVE" ? "Live" : "Test"} API Key
                </CardTitle>
                <CardDescription className="mt-1">
                  Generated at registration. Store it securely — this is the only time it
                  is shown in full.
                </CardDescription>
              </div>
              <Badge variant="secondary">{keyInfo.environment ?? "TEST"}</Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md bg-muted px-4 py-2.5 font-mono text-sm text-muted-foreground overflow-hidden text-ellipsis whitespace-nowrap">
                {visible ? keyInfo.apiKey : masked}
              </code>
              <Button
                variant="outline"
                size="icon"
                onClick={() => setVisible((v) => !v)}
                title={visible ? "Hide" : "Reveal"}
              >
                {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
              </Button>
              <Button variant="outline" size="icon" onClick={copyKey} title="Copy">
                <Copy className="size-4" />
              </Button>
            </div>
            <div className="rounded-md border border-border px-4 py-3">
              <p className="text-xs font-medium text-muted-foreground mb-2">Usage</p>
              <pre className="font-mono text-xs text-muted-foreground whitespace-pre-wrap">
                {`curl -X POST "${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/v1/customers" \\
  -H "Authorization: Bearer ${visible ? keyInfo.apiKey : masked}" \\
  -H "Content-Type: application/json" \\
  -d '{"reference":"user_123","firstName":"John","lastName":"Doe"}'`}
              </pre>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center gap-3">
            <div className="size-10 rounded-full bg-muted flex items-center justify-center">
              <Key className="size-5 text-muted-foreground" />
            </div>
            <div>
              <p className="text-sm font-medium text-foreground">No API key found</p>
              <p className="text-xs text-muted-foreground mt-1">
                Your API key is issued when you register. If you registered in a previous
                session, contact support to rotate your key.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      <Card className="border-primary/20 bg-primary/5">
        <CardContent className="pt-5">
          <p className="text-xs text-muted-foreground leading-relaxed">
            <span className="font-medium text-foreground">Keep your key secret.</span> Never
            commit it to version control or expose it client-side. Use environment variables
            ({" "}
            <code className="text-primary">CYRUS_API_KEY</code>
            {" "}) in your backend services.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

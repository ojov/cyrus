"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { Code } from "@/components/ui/code-block";

/** Mock API console shown on callable reference pages. Requires a test key to "send". */
export function TryIt({
  method,
  path,
  body,
  respCode = "200",
  respText = "OK",
  response,
}: {
  method: string;
  path: string;
  body?: string;
  respCode?: string;
  respText?: string;
  response: string;
}) {
  const [key, setKey] = useState("");
  const [err, setErr] = useState(false);
  const [sent, setSent] = useState(false);

  function send() {
    if (!key.trim()) {
      setErr(true);
      return;
    }
    setErr(false);
    setSent(true);
  }

  return (
    <aside className="self-start overflow-hidden rounded-xl border border-border bg-card lg:sticky lg:top-6">
      <div className="flex items-center gap-2 border-b border-border bg-muted px-3.5 py-2.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
        <span className="size-[7px] rounded-full bg-green-500" /> Try it
      </div>
      <div className="flex flex-col gap-2.5 p-3.5">
        <div className="flex flex-wrap items-center gap-2">
          <span className="method">{method}</span>
          <span className="path">{path}</span>
        </div>
        <label className="text-xs font-semibold text-muted-foreground">Your API key</label>
        <input
          value={key}
          onChange={(e) => setKey(e.target.value)}
          placeholder="cyrus_…"
          className={cn(
            "w-full rounded-lg border bg-muted px-3 py-2 font-mono text-[13px] outline-none focus:border-primary",
            err ? "border-destructive" : "border-border",
          )}
        />
        {body && (
          <>
            <label className="text-xs font-semibold text-muted-foreground">Request body</label>
            <textarea
              defaultValue={body}
              rows={5}
              className="w-full resize-y rounded-lg border border-border bg-muted px-3 py-2 font-mono text-[13px] leading-relaxed outline-none focus:border-primary"
            />
          </>
        )}
        <button
          type="button"
          onClick={send}
          className="w-full rounded-lg bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          {sent ? "Sent ✓" : "Send request"}
        </button>
        {err && <span className="text-xs text-destructive">Enter your API key to authorize the call.</span>}
        {sent && (
          <div className="mt-1 border-t border-dashed border-border pt-2.5">
            <div className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
              <span className="db db-good">{respCode}</span> {respText}
            </div>
            <Code>{response}</Code>
          </div>
        )}
      </div>
    </aside>
  );
}

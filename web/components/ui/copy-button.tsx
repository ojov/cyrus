"use client";

import { useState } from "react";
import { IconCheck, IconCopy } from "@/components/icons";
import { cn } from "@/lib/utils";

/** Icon button that copies `text` to the clipboard and briefly swaps to a checkmark to confirm it. */
export function CopyButton({ text, className }: { text: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <button
      type="button"
      onClick={copy}
      aria-label={copied ? "Copied" : "Copy to clipboard"}
      title={copied ? "Copied" : "Copy to clipboard"}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-md border border-border p-2 transition",
        copied ? "border-green-500/40 text-green-600 dark:text-green-400" : "hover:bg-accent",
        className,
      )}
    >
      {copied ? <IconCheck className="size-4" /> : <IconCopy className="size-4" />}
    </button>
  );
}

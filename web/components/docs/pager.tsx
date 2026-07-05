"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { pagerFor } from "@/lib/docs-nav";

export function Pager() {
  const pathname = usePathname();
  const { prev, next } = pagerFor(pathname);

  return (
    <div className="mt-9 flex justify-between gap-3 border-t border-border pt-4">
      {prev ? (
        <Link href={prev.href} className="max-w-[48%] rounded-lg border border-border px-3.5 py-2.5 transition hover:bg-accent">
          <span className="block text-[11px] font-semibold text-muted-foreground">← Previous</span>
          <span className="mt-0.5 block text-sm font-semibold text-primary">{prev.title}</span>
        </Link>
      ) : (
        <span />
      )}
      {next ? (
        <Link href={next.href} className="ml-auto max-w-[48%] rounded-lg border border-border px-3.5 py-2.5 text-right transition hover:bg-accent">
          <span className="block text-[11px] font-semibold text-muted-foreground">Next →</span>
          <span className="mt-0.5 block text-sm font-semibold text-primary">{next.title}</span>
        </Link>
      ) : (
        <Link href="/login" className="ml-auto max-w-[48%] rounded-lg border border-border px-3.5 py-2.5 text-right transition hover:bg-accent">
          <span className="block text-[11px] font-semibold text-muted-foreground">Next →</span>
          <span className="mt-0.5 block text-sm font-semibold text-primary">Open the dashboard</span>
        </Link>
      )}
    </div>
  );
}

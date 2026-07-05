"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { crumbFor } from "@/lib/docs-nav";
import { ThemeToggle } from "@/components/theme-toggle";

export function DocsTopbar() {
  const pathname = usePathname();
  return (
    <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-border px-6 md:px-8">
      <div className="min-w-0">
        <div className="truncate text-sm font-semibold">{crumbFor(pathname)}</div>
        <div className="text-xs text-muted-foreground">Developer documentation</div>
      </div>
      <div className="flex items-center gap-2">
        <Link
          href="/register"
          className="inline-flex items-center rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-accent"
        >
          Create account
        </Link>
        <ThemeToggle />
      </div>
    </header>
  );
}

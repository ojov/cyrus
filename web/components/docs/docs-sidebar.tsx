"use client";

import Link from "next/link";
import { Logo } from "@/components/logo";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { DOC_PAGES } from "@/lib/docs-nav";
import { IconLock, IconArrowRight } from "@/components/icons";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function DocsSidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden w-64 shrink-0 flex-col overflow-y-auto border-r border-border bg-sidebar md:flex">
      <div className="border-b border-border px-5 py-5">
        <Link href="/" className="flex items-center gap-2.5">
          <Logo className="size-8" />
          <span>
            <span className="block font-semibold leading-tight">Cyrus</span>
            <span className="block text-xs text-muted-foreground">Payment identity</span>
          </span>
        </Link>
      </div>

      <div className="px-3 pt-3">
        <div className="grid grid-cols-2 gap-0.5 rounded-lg border border-border bg-muted p-0.5">
          <span className="rounded-md bg-card px-2 py-1.5 text-center text-xs font-semibold shadow-sm">Docs</span>
          <Link
            href="/login"
            className="flex items-center justify-center gap-1 rounded-md px-2 py-1.5 text-xs font-semibold text-muted-foreground transition hover:text-foreground"
          >
            Dashboard <IconLock className="size-3" />
          </Link>
        </div>
      </div>

      <nav className="flex-1 px-3 py-4">
        {DOC_PAGES.map((page, i) => {
          const prev = DOC_PAGES[i - 1];
          const showHeader = page.group && page.group !== prev?.group;
          const showSep = i > 0 && !page.group && prev?.group;
          const active = pathname === page.href;
          return (
            <div key={page.href}>
              {showSep && <div className="my-2 border-t border-border" />}
              {showHeader && (
                <p className="mb-1 mt-3 px-2.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {page.group}
                </p>
              )}
              <Link
                href={page.href}
                className={cn(
                  "block rounded-md px-2.5 py-1.5 text-sm transition-colors",
                  page.group && "pl-4",
                  active
                    ? "bg-primary/15 font-medium text-primary"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground",
                )}
              >
                {page.title}
              </Link>
            </div>
          );
        })}
      </nav>

      <div className="border-t border-border px-3 py-3">
        <a
          href={`${API_URL}/scalar`}
          target="_blank"
          rel="noreferrer"
          className="flex items-center justify-between rounded-md bg-primary/10 px-2.5 py-2 text-xs font-semibold text-primary transition hover:bg-primary/15"
        >
          Full API Reference <IconArrowRight className="size-3.5" />
        </a>
        <p className="mt-1 px-2.5 text-[11px] text-muted-foreground">
          Generated live from the API — every request/response shape, always accurate.
        </p>
      </div>
      <div className="border-t border-border px-5 py-4 text-xs text-muted-foreground">Public docs · no login required</div>
    </aside>
  );
}

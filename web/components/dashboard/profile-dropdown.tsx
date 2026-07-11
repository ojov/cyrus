"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { logout, useSession } from "@/lib/auth";
import { IconChevronDown, IconLogOut, IconUser } from "@/components/icons";
import { cn } from "@/lib/utils";

function initials(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return "?";
  const parts = trimmed.split(/\s+/);
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
}

export default function ProfileDropdown({ variant = "header" }: { variant?: "header" | "sidebar" }) {
  const router = useRouter();
  // Reactive: null on the server and on first client paint (matches, avoiding a hydration
  // mismatch), then the real cached session — kept live if the settings page updates it.
  const session = useSession();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!open) return;
    function handle(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    }
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") close();
    }
    document.addEventListener("mousedown", handle);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handle);
      document.removeEventListener("keydown", handleKey);
    };
  }, [open, close]);

  // Deliberately not gated on `session` — the dashboard route is already auth-gated by AuthGuard
  // via the httpOnly cookie, independent of this localStorage cache, so Sign out must stay
  // reachable even if the cache is empty/stale (e.g. cleared storage with a still-valid cookie).
  const name = session?.businessName || session?.businessEmail || "";
  const ini = initials(name);
  const sidebar = variant === "sidebar";

  const styles = sidebar
    ? {
        trigger: "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors hover:bg-accent",
        avatar: "size-7 text-xs",
        label: "flex-1 text-left",
        panel: "bottom-full left-0 mb-1.5",
      }
    : {
        trigger: "flex items-center gap-1.5 rounded-md border border-border px-2 py-1 text-xs font-medium transition hover:bg-accent",
        avatar: "size-5 text-[10px]",
        label: "hidden max-w-[100px] md:inline",
        panel: "right-0 top-full mt-1.5",
      };

  async function handleLogout() {
    close();
    await logout();
    router.push("/");
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className={cn(styles.trigger, open && "bg-accent")}
      >
        <span className={cn("flex shrink-0 items-center justify-center rounded-full bg-primary/15 font-bold text-primary", styles.avatar)}>
          {ini}
        </span>
        {name && <span className={cn("truncate text-muted-foreground", styles.label)}>{name}</span>}
        <IconChevronDown className="size-3 shrink-0 text-muted-foreground" />
      </button>

      {open && (
        <div role="menu" className={cn("absolute z-50 w-56 rounded-xl border border-border bg-card p-1.5 shadow-md", styles.panel)}>
          {session && (
            <>
              <div className="px-2.5 py-2">
                <p className="truncate text-sm font-medium">{session.businessName}</p>
                <p className="truncate text-xs text-muted-foreground">{session.businessEmail}</p>
              </div>
              <div className="my-1 border-t border-border" />
            </>
          )}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              close();
              router.push("/ops/settings#profile");
            }}
            className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <IconUser className="size-4 shrink-0" />
            Profile
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={handleLogout}
            className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <IconLogOut className="size-4 shrink-0" />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

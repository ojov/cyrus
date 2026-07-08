"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { Logo } from "@/components/logo";
import { usePathname, useRouter } from "next/navigation";
import { cn } from "@/lib/utils";
import { getSession, logout } from "@/lib/auth";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import {
  IconGrid,
  IconUsers,
  IconSwap,
  IconChecklist,
  IconKey,
  IconSettings,
  IconLogOut,
  IconWallet,
  IconBank,
  IconSend,
} from "@/components/icons";

type NavItem = {
  href: string;
  label: string;
  Icon: (props: { className?: string }) => ReactNode;
  exact?: boolean;
  badge?: string;
};

const BASE_NAV: Omit<NavItem, "badge">[] = [
  { href: "/ops", label: "Overview", Icon: IconGrid, exact: true },
  { href: "/ops/customers", label: "Customers", Icon: IconUsers },
  { href: "/ops/transactions", label: "Transactions", Icon: IconSwap },
  { href: "/ops/reconciliation", label: "Reconciliation", Icon: IconChecklist },
  { href: "/ops/wallet", label: "Wallet", Icon: IconWallet },
  { href: "/ops/beneficiaries", label: "Beneficiaries", Icon: IconBank },
  { href: "/ops/payouts", label: "Payouts", Icon: IconSend },
  { href: "/ops/api-keys", label: "API keys", Icon: IconKey },
  { href: "/ops/settings", label: "Settings", Icon: IconSettings },
];

export function DashboardSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const session = getSession();
  const { stats } = useDashboardStats();

  // Reconciliation badge tracks the same real "needs attention" count (orphaned + manual review)
  // the Overview and Reconciliation pages themselves compute, so the sidebar can't drift from it.
  const needsAttention = stats ? stats.reconciliation.orphaned + stats.reconciliation.manualReview : 0;
  const nav: NavItem[] = BASE_NAV.map((item) =>
    item.href === "/ops/reconciliation" && needsAttention > 0
      ? { ...item, badge: String(needsAttention) }
      : item,
  );

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <aside className="hidden w-56 shrink-0 flex-col border-r border-border bg-sidebar md:flex">
      <div className="border-b border-border px-5 py-5">
        <Link href="/" className="flex items-center gap-2.5">
          <Logo className="size-8" />
          <span className="font-semibold">Cyrus</span>
        </Link>
        <p suppressHydrationWarning className="mt-1 truncate text-xs text-muted-foreground">
          {session?.businessName ?? ""}
        </p>
      </div>

      <nav className="flex-1 space-y-0.5 px-3 py-4">
        {nav.map((item) => {
          const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-md px-2.5 py-2 text-sm transition-colors",
                active
                  ? "bg-primary/15 font-medium text-primary"
                  : "text-muted-foreground hover:bg-accent hover:text-foreground",
              )}
            >
              <item.Icon className="size-4 shrink-0" />
              {item.label}
              {item.badge && <span className="db db-crit ml-auto px-1.5 py-0 text-[10px]">{item.badge}</span>}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-border px-3 py-4">
        <button
          type="button"
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-md px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        >
          <IconLogOut className="size-4 shrink-0" />
          Sign out
        </button>
      </div>
    </aside>
  );
}

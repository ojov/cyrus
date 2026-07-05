import type { ReactNode } from "react";
import { Logo } from "@/components/logo";
import { cn } from "@/lib/utils";

export function AuthCard({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className={cn("w-full rounded-xl border border-border bg-card p-6 shadow-sm", wide ? "max-w-md" : "max-w-sm")}>
      {children}
    </div>
  );
}

export function AuthCardHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="mb-1 flex items-center gap-2.5">
      <Logo className="size-8" />
      <div>
        <b className="block leading-tight">{title}</b>
        <span className="text-xs text-muted-foreground">{subtitle}</span>
      </div>
    </div>
  );
}

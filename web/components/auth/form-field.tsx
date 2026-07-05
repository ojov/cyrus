import type { InputHTMLAttributes, ReactNode } from "react";

export const fieldClass = "w-full rounded-lg border border-border bg-muted px-3 py-2.5 text-sm outline-none focus:border-primary";

export function Field({ label, ...props }: { label: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">{label}</label>
      <input className={fieldClass} {...props} />
    </div>
  );
}

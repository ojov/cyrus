"use client";

import { useState, type InputHTMLAttributes, type ReactNode } from "react";
import { IconEye, IconEyeOff } from "@/components/icons";

export const fieldClass = "w-full rounded-lg border border-border bg-muted px-3 py-2.5 text-sm outline-none focus:border-primary";

export function Field({ label, type, ...props }: { label: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === "password";

  return (
    <div>
      <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">{label}</label>
      {isPassword ? (
        <div className="relative">
          <input type={revealed ? "text" : "password"} className={`${fieldClass} pr-10`} {...props} />
          <button
            type="button"
            onClick={() => setRevealed((r) => !r)}
            aria-label={revealed ? "Hide password" : "Show password"}
            aria-pressed={revealed}
            tabIndex={-1}
            className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground transition hover:text-foreground"
          >
            {revealed ? <IconEyeOff className="size-4" /> : <IconEye className="size-4" />}
          </button>
        </div>
      ) : (
        <input type={type} className={fieldClass} {...props} />
      )}
    </div>
  );
}

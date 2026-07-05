"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { saveSession } from "@/lib/auth";

const field = "w-full rounded-lg border border-border bg-muted px-3 py-2.5 text-sm outline-none focus:border-primary";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resent, setResent] = useState(false);
  const [resending, setResending] = useState(false);
  const isUnverified = error?.toLowerCase().includes("not yet verified") ?? false;

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await authApi.login(email, password);
      saveSession({
        token: res.data.token,
        merchantId: res.data.merchantId,
        businessName: res.data.businessName,
        businessEmail: res.data.businessEmail,
      });
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-sm rounded-xl border border-border bg-card p-6 shadow-sm">
      <div className="mb-1 flex items-center gap-2.5">
        <span className="grid size-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">C</span>
        <div>
          <b className="block leading-tight">Sign in to Cyrus</b>
          <span className="text-xs text-muted-foreground">Operations dashboard</span>
        </div>
      </div>
      <p className="mb-5 mt-3 text-sm text-muted-foreground">
        The dashboard is for your ops team. Developers integrate with the API and do not need to sign in.
      </p>

      <form onSubmit={submit} className="space-y-3">
        <div>
          <label className="mb-1.5 block text-xs font-semibold text-muted-foreground">Business email</label>
          <input className={field} type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="dev@acme.ng" />
        </div>
        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="text-xs font-semibold text-muted-foreground">Password</label>
            <Link href="/forgot-password" className="text-xs text-muted-foreground underline underline-offset-2 hover:text-primary">Forgot?</Link>
          </div>
          <input className={field} type="password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        {isUnverified && !resent && (
          <button
            type="button"
            disabled={resending}
            onClick={async () => {
              setResending(true);
              try {
                await authApi.resendVerification(email);
                setResent(true);
              } catch {
                setError("Failed to resend. Please try again.");
              } finally {
                setResending(false);
              }
            }}
            className="text-xs text-muted-foreground underline underline-offset-2 hover:text-primary"
          >
            {resending ? "Sending…" : "Resend verification email"}
          </button>
        )}
        {resent && <p className="text-xs text-muted-foreground">Verification email resent. Please check your inbox.</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-lg bg-primary px-3 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <div className="my-4 flex items-center gap-2.5">
        <span className="h-px flex-1 bg-border" />
        <span className="text-xs text-muted-foreground">new to Cyrus?</span>
        <span className="h-px flex-1 bg-border" />
      </div>
      <Link
        href="/register"
        className="block w-full rounded-lg border border-border px-3 py-2.5 text-center text-sm font-semibold transition hover:bg-accent"
      >
        Create an account
      </Link>
      <p className="mt-3 text-center text-xs text-muted-foreground">
        After signup, generate your API key from the dashboard and copy it when it appears.
      </p>
    </div>
  );
}

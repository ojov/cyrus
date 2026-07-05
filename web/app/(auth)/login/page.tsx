"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { saveSession } from "@/lib/auth";
import { AuthCard, AuthCardHeader } from "@/components/auth/auth-card";
import { Field } from "@/components/auth/form-field";
import { FormError } from "@/components/auth/form-error";

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
    <AuthCard>
      <AuthCardHeader title="Sign in to Cyrus" subtitle="Operations dashboard" />
      <p className="mb-5 mt-3 text-sm text-muted-foreground">
        The dashboard is for your ops team. Developers integrate with the API and do not need to sign in.
      </p>

      <form onSubmit={submit} className="space-y-3">
        <Field label="Business email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="dev@acme.ng" />
        <Field
          label={
            <span className="flex items-center justify-between">
              Password
              <Link href="/forgot-password" className="font-normal normal-case text-muted-foreground underline underline-offset-2 hover:text-primary">Forgot?</Link>
            </span>
          }
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
        />
        <FormError error={error} />
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
    </AuthCard>
  );
}

"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { authApi } from "@/lib/api";
import { AuthCard, AuthCardHeader } from "@/components/auth/auth-card";
import { Field } from "@/components/auth/form-field";
import { FormError } from "@/components/auth/form-error";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <AuthCard>
        <AuthCardHeader title="Check your inbox" subtitle="Password reset email sent" />
        <p className="mb-5 mt-3 text-sm text-muted-foreground">
          If an account with that email exists, we&apos;ve sent a reset link that expires in 15 minutes.
        </p>
        <Link
          href="/login"
          className="block w-full rounded-lg border border-border px-3 py-2.5 text-center text-sm font-semibold transition hover:bg-accent"
        >
          Back to sign in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard>
      <AuthCardHeader title="Forgot password" subtitle="Enter your email to receive a reset link" />

      <form onSubmit={submit} className="mt-5 space-y-3">
        <Field label="Business email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="dev@acme.ng" />
        <FormError error={error} />
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-lg bg-primary px-3 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {busy ? "Sending…" : "Send reset link"}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted-foreground">
        Remember your password?{" "}
        <Link href="/login" className="font-semibold text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthCard>
  );
}

"use client";

import { useState, type FormEvent, use } from "react";
import Link from "next/link";
import { authApi } from "@/lib/api";
import { AuthCard, AuthCardHeader } from "@/components/auth/auth-card";
import { Field } from "@/components/auth/form-field";
import { FormError } from "@/components/auth/form-error";

export default function ResetPasswordPage(props: { searchParams: Promise<{ token?: string }> }) {
  const { token } = use(props.searchParams);
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (newPassword !== confirm) {
      setError("Passwords do not match");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(token!, newPassword);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Reset failed");
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthCard>
        <b className="block text-sm">Invalid reset link</b>
        <p className="mb-4 mt-2 text-sm text-muted-foreground">This link is missing the reset token. Please request a new one.</p>
        <Link href="/forgot-password" className="text-sm font-semibold text-primary hover:underline">Request new reset link</Link>
      </AuthCard>
    );
  }

  if (done) {
    return (
      <AuthCard>
        <AuthCardHeader title="Password reset" subtitle="Your password has been changed" />
        <p className="mb-5 mt-3 text-sm text-muted-foreground">You can now sign in with your new password.</p>
        <Link
          href="/login"
          className="block w-full rounded-lg bg-primary px-3 py-2.5 text-center text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          Sign in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard>
      <AuthCardHeader title="Set new password" subtitle="At least 8 characters" />

      <form onSubmit={submit} className="mt-5 space-y-3">
        <Field label="New password" type="password" required minLength={8} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="••••••••" />
        <Field label="Confirm password" type="password" required minLength={8} value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder="••••••••" />
        <FormError error={error} />
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-lg bg-primary px-3 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {busy ? "Resetting…" : "Reset password"}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted-foreground">
        <Link href="/login" className="font-semibold text-primary hover:underline">Back to sign in</Link>
      </p>
    </AuthCard>
  );
}

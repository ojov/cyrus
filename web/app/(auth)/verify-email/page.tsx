"use client";

import { useEffect, useState, use } from "react";
import Link from "next/link";
import { authApi } from "@/lib/api";
import { AuthCard, AuthCardHeader } from "@/components/auth/auth-card";

export default function VerifyEmailPage(props: { searchParams: Promise<{ token?: string }> }) {
  const { token } = use(props.searchParams);
  const [status, setStatus] = useState<"verifying" | "done" | "error">("verifying");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      // Defer off the effect body — setState belongs in the callback, not synchronously here.
      Promise.resolve().then(() => setStatus("error"));
      return;
    }
    authApi
      .verifyEmail(token)
      .then(() => setStatus("done"))
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Verification failed");
        setStatus("error");
      });
  }, [token]);

  if (status === "verifying") {
    return (
      <AuthCard>
        <div className="text-center">
          <b className="block text-sm">Verifying your email…</b>
          <p className="mt-2 text-sm text-muted-foreground">This will just take a moment.</p>
        </div>
      </AuthCard>
    );
  }

  if (status === "error") {
    return (
      <AuthCard>
        <b className="block text-sm">Verification failed</b>
        <p className="mb-4 mt-2 text-sm text-muted-foreground">
          {token ? (error ?? "This link is invalid or has expired.") : "This link is missing a verification token."}
        </p>
        <Link href="/login" className="text-sm font-semibold text-primary hover:underline">
          Back to sign in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard>
      <AuthCardHeader title="Email verified" subtitle="Your account is now active" />
      <p className="mb-5 mt-3 text-sm text-muted-foreground">You can now sign in to your Cyrus dashboard.</p>
      <Link
        href="/login"
        className="block w-full rounded-lg bg-primary px-3 py-2.5 text-center text-sm font-semibold text-primary-foreground transition hover:brightness-105"
      >
        Sign in
      </Link>
    </AuthCard>
  );
}

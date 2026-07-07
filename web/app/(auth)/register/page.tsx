"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { saveSession } from "@/lib/auth";
import { AuthCard, AuthCardHeader } from "@/components/auth/auth-card";
import { Field } from "@/components/auth/form-field";
import { FormError } from "@/components/auth/form-error";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ businessName: "", businessEmail: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set(key: keyof typeof form) {
    return (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));
  }

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await authApi.register(form);
      saveSession({
        merchantId: res.data.merchantId,
        businessName: res.data.businessName,
        businessEmail: res.data.businessEmail,
      });
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
      setBusy(false);
    }
  }

  return (
    <AuthCard>
      <AuthCardHeader title="Create your Cyrus account" subtitle="Start provisioning virtual accounts in minutes" />

      <form onSubmit={submit} className="space-y-3">
        <Field label="Business name" required value={form.businessName} onChange={set("businessName")} placeholder="Acme Payments" />
        <Field label="Business email" type="email" required value={form.businessEmail} onChange={set("businessEmail")} placeholder="dev@acme.ng" />
        <Field label="Password" type="password" required value={form.password} onChange={set("password")} placeholder="••••••••" />
        <FormError error={error} />
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-lg bg-primary px-3 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {busy ? "Creating account…" : "Create account"}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link href="/login" className="font-semibold text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthCard>
  );
}

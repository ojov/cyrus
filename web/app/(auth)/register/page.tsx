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
  const [form, setForm] = useState({
    businessName: "",
    businessEmail: "",
    password: "",
    nombaClientId: "",
    nombaClientSecret: "",
    nombaParentAccountId: "",
    subAccountIds: "",
  });
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
      const res = await authApi.register({
        businessName: form.businessName,
        businessEmail: form.businessEmail,
        password: form.password,
        nombaClientId: form.nombaClientId,
        nombaClientSecret: form.nombaClientSecret,
        nombaParentAccountId: form.nombaParentAccountId,
        subAccountIds: form.subAccountIds.split(",").map((s) => s.trim()).filter(Boolean),
      });
      saveSession({
        token: res.data.token,
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
    <AuthCard wide>
      <AuthCardHeader title="Create your Cyrus account" subtitle="Sign up with your Nomba sandbox keys" />
      <p className="mb-5 mt-3 text-sm text-muted-foreground">
        Use your Nomba <b className="text-foreground">sandbox (test)</b> credentials — not live. After signup, generate your{" "}
        <span className="font-mono text-primary">cyrus_test_</span> API key from the dashboard and copy it immediately.
      </p>

      <form onSubmit={submit} className="space-y-3">
        <Field label="Business name" required value={form.businessName} onChange={set("businessName")} placeholder="Acme Payments" />
        <div className="grid grid-cols-2 gap-3">
          <Field label="Business email" type="email" required value={form.businessEmail} onChange={set("businessEmail")} placeholder="dev@acme.ng" />
          <Field label="Password" type="password" required value={form.password} onChange={set("password")} placeholder="••••••••" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field
            label={<>Nomba client ID <span className="text-primary">(test)</span></>}
            required
            value={form.nombaClientId}
            onChange={set("nombaClientId")}
            placeholder="sandbox client ID"
          />
          <Field
            label={<>Nomba client secret <span className="text-primary">(test)</span></>}
            type="password"
            required
            value={form.nombaClientSecret}
            onChange={set("nombaClientSecret")}
            placeholder="sandbox secret"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Parent account ID" required value={form.nombaParentAccountId} onChange={set("nombaParentAccountId")} placeholder="NMB-48210" />
          <Field label="Sub-account IDs" value={form.subAccountIds} onChange={set("subAccountIds")} placeholder="sub_acct_1, sub_acct_2" />
        </div>
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

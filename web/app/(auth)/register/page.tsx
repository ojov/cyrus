"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { saveSession } from "@/lib/auth";

const field = "w-full rounded-lg border border-border bg-muted px-3 py-2.5 text-sm outline-none focus:border-primary";
const label = "mb-1.5 block text-xs font-semibold text-muted-foreground";

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
    <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-sm">
      <div className="mb-1 flex items-center gap-2.5">
        <span className="grid size-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">C</span>
        <div>
          <b className="block leading-tight">Create your Cyrus account</b>
          <span className="text-xs text-muted-foreground">Sign up with your Nomba sandbox keys</span>
        </div>
      </div>
      <p className="mb-5 mt-3 text-sm text-muted-foreground">
        Use your Nomba <b className="text-foreground">sandbox (test)</b> credentials — not live. After signup, generate your{" "}
        <span className="font-mono text-primary">cyrus_test_</span> API key from the dashboard and copy it immediately.
      </p>

      <form onSubmit={submit} className="space-y-3">
        <div>
          <label className={label}>Business name</label>
          <input className={field} required value={form.businessName} onChange={set("businessName")} placeholder="Acme Payments" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={label}>Business email</label>
            <input className={field} type="email" required value={form.businessEmail} onChange={set("businessEmail")} placeholder="dev@acme.ng" />
          </div>
          <div>
            <label className={label}>Password</label>
            <input className={field} type="password" required value={form.password} onChange={set("password")} placeholder="••••••••" />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={label}>
              Nomba client ID <span className="text-primary">(test)</span>
            </label>
            <input className={field} required value={form.nombaClientId} onChange={set("nombaClientId")} placeholder="sandbox client ID" />
          </div>
          <div>
            <label className={label}>
              Nomba client secret <span className="text-primary">(test)</span>
            </label>
            <input
              className={field}
              type="password"
              required
              value={form.nombaClientSecret}
              onChange={set("nombaClientSecret")}
              placeholder="sandbox secret"
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={label}>Parent account ID</label>
            <input className={field} required value={form.nombaParentAccountId} onChange={set("nombaParentAccountId")} placeholder="NMB-48210" />
          </div>
          <div>
            <label className={label}>Sub-account IDs</label>
            <input className={field} value={form.subAccountIds} onChange={set("subAccountIds")} placeholder="sub_acct_1, sub_acct_2" />
          </div>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
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
    </div>
  );
}

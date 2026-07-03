"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { authApi } from "@/lib/api";

const INIT = {
  businessName: "",
  businessEmail: "",
  password: "",
  confirmPassword: "",
  nombaClientId: "",
  nombaClientSecret: "",
  nombaParentAccountId: "",
  nombaSubAccountIds: "",
};

export default function RegisterPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState(INIT);

  function field(key: keyof typeof INIT) {
    return (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (form.password !== form.confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.register({
        businessName: form.businessName,
        businessEmail: form.businessEmail,
        password: form.password,
        nombaClientId: form.nombaClientId,
        nombaClientSecret: form.nombaClientSecret,
        nombaParentAccountId: form.nombaParentAccountId,
        subAccountIds: form.nombaSubAccountIds
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
      });
      const issuedKey = res.data.apiKey?.apiKeys?.[0];
      localStorage.setItem(
        "cyrus_api_key",
        JSON.stringify({
          apiKey: issuedKey?.apiKey ?? "",
          environment: issuedKey?.environment ?? "TEST",
          merchantId: res.data.merchantId,
        })
      );
      toast.success("Account created! Check your email to verify before logging in.");
      router.push("/login");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card className="w-full max-w-lg">
      <CardHeader className="space-y-1">
        <CardTitle className="text-2xl">Create your account</CardTitle>
        <CardDescription>
          Connect your Nomba credentials to start provisioning virtual accounts.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2 col-span-2">
              <Label htmlFor="businessName">Business name</Label>
              <Input
                id="businessName"
                placeholder="Acme Payments Ltd"
                value={form.businessName}
                onChange={field("businessName")}
                required
              />
            </div>
            <div className="space-y-2 col-span-2">
              <Label htmlFor="businessEmail">Business email</Label>
              <Input
                id="businessEmail"
                type="email"
                placeholder="dev@yourcompany.ng"
                value={form.businessEmail}
                onChange={field("businessEmail")}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={form.password}
                onChange={field("password")}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="••••••••"
                value={form.confirmPassword}
                onChange={field("confirmPassword")}
                required
              />
            </div>
          </div>

          <div className="pt-2 border-t border-border">
            <p className="text-xs font-medium text-muted-foreground mb-3 uppercase tracking-wider">
              Nomba Credentials
            </p>
            <div className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="nombaParentAccountId">Parent account ID</Label>
                <Input
                  id="nombaParentAccountId"
                  placeholder="NMB-XXXXXXXXXXXX"
                  value={form.nombaParentAccountId}
                  onChange={field("nombaParentAccountId")}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="nombaSubAccountIds">Sub-account ID(s)</Label>
                <Input
                  id="nombaSubAccountIds"
                  placeholder="sub-account id (comma-separated for multiple)"
                  value={form.nombaSubAccountIds}
                  onChange={field("nombaSubAccountIds")}
                  required
                />
                <p className="text-xs text-muted-foreground">
                  Virtual accounts are created under a sub-account. Required to provision accounts.
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="nombaClientId">Client ID</Label>
                <Input
                  id="nombaClientId"
                  placeholder="Nomba OAuth client ID"
                  value={form.nombaClientId}
                  onChange={field("nombaClientId")}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="nombaClientSecret">Client secret</Label>
                <Input
                  id="nombaClientSecret"
                  type="password"
                  placeholder="Nomba OAuth client secret"
                  value={form.nombaClientSecret}
                  onChange={field("nombaClientSecret")}
                  required
                />
              </div>
            </div>
          </div>

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? "Creating account…" : "Create account"}
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link href="/login" className="text-primary hover:underline font-medium">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CreditCard, Users } from "lucide-react";
import { getSession, type MerchantSession } from "@/lib/auth";
import { dashboardApi } from "@/lib/api";

export default function DashboardPage() {
  const [session, setSession] = useState<MerchantSession | null>(null);
  const [stats, setStats] = useState<{ customers: number; virtualAccounts: number } | null>(null);

  useEffect(() => {
    setSession(getSession());
    dashboardApi
      .stats()
      .then((res) => setStats(res.data))
      .catch(() => setStats(null));
  }, []);

  const cards = [
    { title: "Virtual Accounts", value: stats?.virtualAccounts, icon: CreditCard },
    { title: "Customers", value: stats?.customers, icon: Users },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">
          {session ? `Welcome, ${session.businessName}` : "Dashboard"}
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Overview of your virtual account infrastructure.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 max-w-lg">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {card.title}
              </CardTitle>
              <card.icon className="size-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <span className="text-2xl font-bold text-foreground">{card.value ?? "—"}</span>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Getting started</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {[
              { step: "1", label: "Register your business", done: true },
              { step: "2", label: "Verify your email address", done: true },
              { step: "3", label: "Generate an API key", done: false },
              { step: "4", label: "Create your first customer", done: false },
              { step: "5", label: "Provision a virtual account", done: false },
            ].map((item) => (
              <div key={item.step} className="flex items-center gap-3">
                <div
                  className={`size-6 rounded-full flex items-center justify-center text-xs font-medium shrink-0 ${
                    item.done
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground"
                  }`}
                >
                  {item.done ? "✓" : item.step}
                </div>
                <span
                  className={`text-sm ${
                    item.done ? "line-through text-muted-foreground" : "text-foreground"
                  }`}
                >
                  {item.label}
                </span>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">API base URL</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-md bg-muted px-4 py-3 font-mono text-sm text-muted-foreground">
              {process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}
            </div>
            <p className="text-xs text-muted-foreground">
              All requests must include{" "}
              <code className="text-primary">Authorization: Bearer &lt;token&gt;</code>
            </p>
            <div className="rounded-md border border-border px-4 py-3 font-mono text-xs text-muted-foreground space-y-1">
              <div><span className="text-primary">POST</span> /v1/customers</div>
              <div><span className="text-primary">GET</span>  /v1/customers/{"{reference}"}</div>
              <div><span className="text-primary">GET</span>  /v1/merchants/me/subaccounts/balances</div>
              <div><span className="text-primary">POST</span> /v1/merchants/me/go-live</div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

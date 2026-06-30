"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CreditCard, Users, ArrowDownLeft, Activity } from "lucide-react";
import { getSession, type MerchantSession } from "@/lib/auth";

const STAT_CARDS = [
  { title: "Virtual Accounts", value: "—", icon: CreditCard, badge: "Active" },
  { title: "Customers", value: "—", icon: Users, badge: null },
  { title: "Transactions Today", value: "—", icon: ArrowDownLeft, badge: null },
  { title: "Pending Events", value: "—", icon: Activity, badge: null },
];

export default function DashboardPage() {
  const [session, setSession] = useState<MerchantSession | null>(null);

  useEffect(() => {
    setSession(getSession());
  }, []);

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

      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        {STAT_CARDS.map((card) => (
          <Card key={card.title}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {card.title}
              </CardTitle>
              <card.icon className="size-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="flex items-end gap-2">
                <span className="text-2xl font-bold text-foreground">{card.value}</span>
                {card.badge && (
                  <Badge variant="secondary" className="mb-0.5 text-xs">
                    {card.badge}
                  </Badge>
                )}
              </div>
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
              {process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api"}
            </div>
            <p className="text-xs text-muted-foreground">
              All requests must include{" "}
              <code className="text-primary">Authorization: Bearer &lt;token&gt;</code>
            </p>
            <div className="rounded-md border border-border px-4 py-3 font-mono text-xs text-muted-foreground space-y-1">
              <div><span className="text-primary">POST</span> /v1/virtual-accounts</div>
              <div><span className="text-primary">GET</span>  /v1/virtual-accounts</div>
              <div><span className="text-primary">POST</span> /v1/customers</div>
              <div><span className="text-primary">GET</span>  /v1/transactions</div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

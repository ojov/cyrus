import Link from "next/link";
import { ArrowRight, Zap, Shield, Webhook, CreditCard } from "lucide-react";

const NAV = [
  { title: "Introduction", items: ["Overview", "Quick Start", "Authentication"] },
  { title: "API Reference", items: ["Virtual Accounts", "Customers", "Transactions", "Webhooks", "API Keys"] },
  { title: "Guides", items: ["Payment Attribution", "Reconciliation", "Error Handling"] },
];

const FEATURES = [
  { icon: CreditCard, title: "Dedicated Virtual Accounts", description: "Provision a persistent NUBAN-style account per customer. Funds flow directly — Cyrus maps them." },
  { icon: Zap, title: "Real-time Payment Events", description: "Reliable webhook delivery with duplicate detection and failed-event recovery built in." },
  { icon: Shield, title: "Reconciliation Engine", description: "Automatically compares provider records against internal state to detect missed or orphaned transactions." },
  { icon: Webhook, title: "Normalised Webhooks", description: "One clean event schema regardless of what Nomba sends. No provider-specific payload parsing." },
];

const QUICK_START = `# 1. Register your business (returns a dashboard token + test API key)
POST /v1/auth/register
{
  "businessName": "Acme Payments",
  "businessEmail": "dev@acme.ng",
  "password": "••••••••",
  "nombaClientId": "...",
  "nombaClientSecret": "...",
  "nombaParentAccountId": "NMB-XXXX",
  "subAccountIds": ["sub_acct_1"]
}

# 2. Create a customer — a dedicated virtual account is provisioned automatically
POST /v1/customers
Authorization: Bearer <your cyrus_test_ API key>
{ "reference": "user_123", "firstName": "John", "lastName": "Doe" }
→ {
    "virtualAccount": {
      "accountNumber": "0123456789",
      "bankName": "Nomba MFB",
      "status": "ACTIVE"
    }
  }

# 3. Fetch the customer + its account any time
GET /v1/customers/user_123
Authorization: Bearer <your cyrus_test_ API key>`;

export default function LandingPage() {
  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 shrink-0 border-r border-border flex flex-col overflow-y-auto">
        <div className="px-5 py-5 border-b border-border">
          <Link href="/" className="flex items-center gap-2">
            <div className="size-7 rounded bg-primary flex items-center justify-center">
              <span className="text-primary-foreground text-xs font-bold">C</span>
            </div>
            <span className="font-semibold text-foreground">Cyrus</span>
          </Link>
          <p className="text-xs text-muted-foreground mt-1">Developer Docs</p>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-6">
          {NAV.map((section) => (
            <div key={section.title}>
              <p className="px-2 mb-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                {section.title}
              </p>
              <ul className="space-y-0.5">
                {section.items.map((item) => (
                  <li key={item}>
                    <a href="#" className="block px-2 py-1.5 rounded text-sm text-muted-foreground hover:text-foreground hover:bg-accent transition-colors">
                      {item}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <div className="px-4 py-4 border-t border-border">
          <p className="text-xs text-muted-foreground">Nomba Hackathon 2026</p>
        </div>
      </aside>

      {/* Main area */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b border-border flex items-center justify-between px-8 shrink-0">
          <div className="flex items-center gap-6 text-sm text-muted-foreground">
            <span className="text-foreground font-medium">Docs</span>
            <a href="#" className="hover:text-foreground transition-colors">Changelog</a>
            <a href="#" className="hover:text-foreground transition-colors">Support</a>
          </div>
          <Link
            href="/dashboard"
            className="flex items-center gap-2 px-4 py-1.5 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition-opacity"
          >
            Dashboard <ArrowRight className="size-3.5" />
          </Link>
        </header>

        <main className="flex-1 overflow-y-auto">
          <div className="max-w-3xl mx-auto px-8 py-12">
            <div className="mb-12">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-primary/30 bg-primary/10 text-primary text-xs font-medium mb-6">
                <Zap className="size-3" />
                Dedicated Virtual Account Infrastructure
              </div>
              <h1 className="text-4xl font-bold text-foreground mb-4 leading-tight">
                Welcome to Cyrus
              </h1>
              <p className="text-lg text-muted-foreground leading-relaxed">
                Cyrus is the operational layer that makes Nomba virtual accounts
                production-ready. Connect your Nomba account, provision dedicated
                accounts per customer, and receive reliable payment events — without
                rebuilding payment infrastructure from scratch.
              </p>
              <div className="mt-8 flex gap-4">
                <Link
                  href="/register"
                  className="flex items-center gap-2 px-5 py-2.5 rounded-md bg-primary text-primary-foreground font-medium hover:opacity-90 transition-opacity"
                >
                  Get Started <ArrowRight className="size-4" />
                </Link>
                <a
                  href="#quick-start"
                  className="flex items-center gap-2 px-5 py-2.5 rounded-md border border-border text-foreground font-medium hover:bg-accent transition-colors"
                >
                  Quick Start
                </a>
              </div>
            </div>

            <section className="mb-12">
              <h2 className="text-xl font-semibold text-foreground mb-6">What Cyrus provides</h2>
              <div className="grid grid-cols-2 gap-4">
                {FEATURES.map((f) => (
                  <div key={f.title} className="p-4 rounded-lg border border-border bg-card">
                    <div className="size-8 rounded bg-primary/15 flex items-center justify-center mb-3">
                      <f.icon className="size-4 text-primary" />
                    </div>
                    <h3 className="text-sm font-semibold text-foreground mb-1">{f.title}</h3>
                    <p className="text-xs text-muted-foreground leading-relaxed">{f.description}</p>
                  </div>
                ))}
              </div>
            </section>

            <section id="quick-start" className="mb-12">
              <h2 className="text-xl font-semibold text-foreground mb-2">Quick Start</h2>
              <p className="text-sm text-muted-foreground mb-4">
                Get a virtual account provisioned in under 5 minutes.
              </p>
              <div className="rounded-lg border border-border overflow-hidden">
                <div className="flex items-center gap-2 px-4 py-2.5 bg-muted border-b border-border">
                  <div className="size-2.5 rounded-full bg-destructive/60" />
                  <div className="size-2.5 rounded-full bg-yellow-500/60" />
                  <div className="size-2.5 rounded-full bg-green-500/60" />
                  <span className="ml-2 text-xs text-muted-foreground font-mono">bash</span>
                </div>
                <pre className="p-5 text-xs font-mono text-muted-foreground leading-relaxed overflow-x-auto">
                  <code>{QUICK_START}</code>
                </pre>
              </div>
            </section>

            <section className="mb-12 p-5 rounded-lg border border-primary/20 bg-primary/5">
              <h2 className="text-base font-semibold text-foreground mb-2">Authentication</h2>
              <p className="text-sm text-muted-foreground mb-3">
                All API requests require a Bearer token obtained via{" "}
                <code className="text-primary text-xs">POST /v1/auth/login</code>.
              </p>
              <pre className="text-xs font-mono text-muted-foreground">
                <code>Authorization: Bearer {"<your-api-token>"}</code>
              </pre>
            </section>
          </div>
        </main>
      </div>
    </div>
  );
}

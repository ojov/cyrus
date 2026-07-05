import Link from "next/link";
import { Code } from "@/components/ui/code-block";
import { IconArrowRight } from "@/components/icons";

export default function GettingStartedPage() {
  return (
    <div className="doc-prose">
      <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Dedicated virtual account infrastructure</p>
      <h2 style={{ fontSize: "2rem", lineHeight: 1.08, marginTop: "0.6rem" }}>Give every customer their own account number</h2>
      <p className="lede">
        Cyrus sits above Nomba and turns one API call into a persistent, named virtual account — then reconciles every
        inbound transfer back to that customer. You never parse a raw provider payload.
      </p>

      <ol className="steps">
        <li>
          <b>Create your Cyrus account</b>
          <span>
            Sign up with your business email and your Nomba sandbox credentials (client ID and secret, parent account,
            sub-account). You instantly receive your API keys — a <code>cyrus_test_</code> key that authenticates every
            request to Cyrus.
          </span>
        </li>
        <li>
          <b>Create a customer</b>
          <span>One request provisions a dedicated virtual account automatically — no separate account-creation call.</span>
        </li>
        <li>
          <b>Receive payments</b>
          <span>
            Money sent to that account number is attributed to the customer and delivered to your webhook as a normalized{" "}
            <code>payment.received</code> event.
          </span>
        </li>
        <li>
          <b>Go live</b>
          <span>
            When you are ready, add your live Nomba credentials in the dashboard (Settings → Go live) to unlock{" "}
            <code>cyrus_live_</code> keys. Nothing else in your integration changes.
          </span>
        </li>
      </ol>

      <Code>{`POST https://api.trycyrus.app/v1/customers
Authorization: Bearer cyrus_test_9f2a…

{
  "externalCustomerId": "user_123",
  "firstName": "Amara",
  "lastName": "Okafor"
}

→ 201 Created
{
  "customerId": "cus_01HZY8",
  "virtualAccount": {
    "accountNumber": "0123456789",
    "bankName": "Nomba MFB",
    "status": "ACTIVE"
  }
}`}</Code>

      <h3>Authenticate every request</h3>
      <p>Cyrus authenticates with API keys — there is no separate API login. Send your key as a bearer token; the prefix picks the environment.</p>
      <Code>{`Authorization: Bearer cyrus_test_…   # sandbox
Authorization: Bearer cyrus_live_…   # production`}</Code>
      <p className="text-sm text-muted-foreground">
        Your ops team uses a separate dashboard login (email and password) — it only governs the dashboard, never the API.
      </p>

      <div className="callout">
        <b>Sandbox note.</b> The Nomba sandbox allows 2 virtual accounts per account holder — enough to test provisioning and
        reconciliation end-to-end before you go live.
      </div>

      <div className="mt-5 flex flex-wrap gap-3">
        <Link
          href="/register"
          className="inline-flex items-center gap-1.5 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          Create an account <IconArrowRight className="size-4" />
        </Link>
        <Link
          href="/environments"
          className="inline-flex items-center rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-accent"
        >
          Environments
        </Link>
      </div>
    </div>
  );
}

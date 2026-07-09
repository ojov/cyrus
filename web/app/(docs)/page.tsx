import Link from "next/link";
import { Code } from "@/components/ui/code-block";
import { IconArrowRight } from "@/components/icons";

export default function GettingStartedPage() {
  return (
    <div className="doc-prose">
      <p className="text-[11px] font-bold uppercase tracking-widest text-primary">Dedicated virtual account infrastructure</p>
      <h2 style={{ fontSize: "2rem", lineHeight: 1.08, marginTop: "0.6rem" }}>Give every payer a dedicated account number</h2>
      <p className="lede">
        Cyrus turns one API call into a persistent, named virtual account — then reconciles every inbound transfer
        back to the identity you attribute it to. You never touch a raw provider payload.
      </p>

      <div className="callout">
        A &quot;customer&quot; is just whoever you attribute a virtual account to — a person, a beneficiary, an institution.
        Cyrus doesn&apos;t assume the relationship: a savings app attributes to the saver, a school-fees app might
        attribute to the school. Provisioning and reconciliation work identically either way.
      </div>

      <ol className="steps">
        <li>
          <b>Create your Cyrus account</b>
          <span>
            Sign up with your business name, email, and a password — that&apos;s it. Cyrus runs its own payment-provider
            account under the hood, so you never need provider credentials of your own. After signup, open Dashboard →
            API keys, generate your key, and copy it immediately. Full keys are shown only once.
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
            <code>payment.succeeded</code> event, net of the provider&apos;s fee and Cyrus&apos;s platform fee, credited to
            your wallet.
          </span>
        </li>
        <li>
          <b>Pay out</b>
          <span>
            Add a bank beneficiary and withdraw your wallet balance to it whenever you like — see Dashboard → Payouts.
          </span>
        </li>
      </ol>

      <Code>{`POST https://api.trycyrus.app/v1/customers
Authorization: Bearer cyrus_9f2a…

{
  "reference": "user_123",
  "firstName": "Amara",
  "lastName": "Okafor"
}

→ 200 OK
{
  "data": {
    "reference": "user_123",
    "virtualAccount": {
      "accountNumber": "0123456789",
      "bankName": "Nombank MFB",
      "status": "ACTIVE"
    }
  }
}`}</Code>

      <h3>Authenticate every request</h3>
      <p>
        Cyrus authenticates with a single API key — there is no separate API login and no TEST/LIVE split. Generate your
        key in the dashboard and copy the full value when it is shown.
      </p>
      <Code>{`Authorization: Bearer cyrus_9f2a…`}</Code>
      <p className="text-sm text-muted-foreground">
        Your ops team uses a separate dashboard login (email and password) — it only governs the dashboard, never the API.
      </p>

      <div className="mt-5 flex flex-wrap gap-3">
        <Link
          href="/register"
          className="inline-flex items-center gap-1.5 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          Create an account <IconArrowRight className="size-4" />
        </Link>
        <Link
          href="/reference/authentication"
          className="inline-flex items-center rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-accent"
        >
          Authentication
        </Link>
      </div>
    </div>
  );
}

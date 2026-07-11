import { Code } from "@/components/ui/code-block";

export default function PaymentsReferencePage() {
  return (
    <div className="doc-prose">
      <h2>API Reference · Payments</h2>
      <p className="lede">
        You do not call an endpoint to receive money — payments arrive as transfers to a customer account number. Here is
        the path each one takes.
      </p>
      <div className="my-4 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <span className="db">Payer sends transfer</span><span>→</span>
        <span className="db">provider webhook → Cyrus</span><span>→</span>
        <span className="db">verify + dedupe</span><span>→</span>
        <span className="db db-info">attribute to customer</span><span>→</span>
        <span className="db db-warn">reconciled with provider</span><span>→</span>
        <span className="db db-good">payment.succeeded</span>
      </div>
      <p>
        Cyrus verifies the provider&apos;s webhook signature, discards duplicates, and matches the credited account
        number to a customer. The webhook alone is never treated as proof of payment — Cyrus independently requeries
        the provider to confirm the transfer before your wallet is credited and <code>payment.succeeded</code> fires.
        Amounts are always kobo (₦50,000.00 = 5,000,000 kobo) and may carry sub-kobo decimals
        (e.g. a computed fee of <code>1500.015</code>) — parse them as decimals, not integers.
      </p>
      <Code>{`{
  "event": "payment.succeeded",
  "createdAt": "2026-07-10T08:13:29.663Z",
  "data": {
    "transactionId": "9b323304-…",
    "amountKobo": 15000,
    "feeKobo": 1000,
    "currency": "NGN",
    "status": "SUCCESSFUL",
    "matchStatus": "MATCHED",
    "sessionId": "1000042607…",
    "providerTransactionId": "API-VACT_TRA-…",
    "customerReference": "user_123",
    "virtualAccountNumber": "0123456789",
    "paidAt": "2026-07-07T20:39:02Z"
  }
}`}</Code>
      <p>
        <code>amountKobo</code> is the gross amount the payer sent. Your wallet is credited net of two deductions: the
        provider&apos;s own confirmed fee, and Cyrus&apos;s platform fee (a markup on top of it) — see the Wallet page
        in the dashboard for the exact split on any transaction.
      </p>
      <p>
        A transfer to an account number Cyrus does not recognize is never dropped — it is recorded and surfaced as a
        misdirected payment for your ops team to resolve.
      </p>
      <h3>Payment Events &amp; Exceptions</h3>
      <div className="callout">
        Full field-by-field reference for listing, inspecting, replaying, and reattributing payment events is in the{" "}
        <a href={`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/docs`} target="_blank" rel="noreferrer" className="text-primary underline underline-offset-2">
          live API reference
        </a>{" "}
        under <b>Payment Events (Developer)</b>.
      </div>
      <p>
        Every raw inbound payment event is persisted as a <strong>payment event</strong> — even if it can&apos;t be
        attributed to a customer. You can list and inspect these via the Payment Events API. Each event carries a
        status and, when relevant, a <code>failureReason</code> explaining why it wasn&apos;t processed normally.
      </p>
      <table className="doctable">
        <thead>
          <tr>
            <th>Status</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr><td className="font-mono">RECEIVED</td><td>Event ingested but reconciliation hasn&apos;t confirmed it yet — still pending provider requery.</td></tr>
          <tr><td className="font-mono">PROCESSED</td><td>Successfully reconciled. A transaction exists and the wallet was credited.</td></tr>
          <tr><td className="font-mono">IGNORED</td><td>Not a credit to a known customer — see <code>failureReason</code> below.</td></tr>
          <tr><td className="font-mono">REATTRIBUTED</td><td>Was orphaned, then manually attributed to a customer by you.</td></tr>
          <tr><td className="font-mono">FAILED</td><td>Provider could not confirm the session after all retries.</td></tr>
        </tbody>
      </table>
      <table className="doctable">
        <thead>
          <tr>
            <th>failureReason</th>
            <th>What happened</th>
          </tr>
        </thead>
        <tbody>
          <tr><td className="font-mono">UNKNOWN_VIRTUAL_ACCOUNT</td><td>Account number doesn&apos;t match any of your customers — orphan/misdirected payment. Use <strong>reattribute</strong>.</td></tr>
          <tr><td className="font-mono">INACTIVE_CUSTOMER</td><td>Account matched a customer who was suspended or closed at the time. Use <strong>reattribute</strong>.</td></tr>
          <tr><td className="font-mono">NON_CREDIT_EVENT</td><td>Not a VA credit (e.g. POS failure notification). Nothing to do.</td></tr>
          <tr><td className="font-mono">DUPLICATE</td><td>A transaction for this provider ID already exists. Nothing to do.</td></tr>
          <tr><td className="font-mono">PROVIDER_UNCONFIRMED</td><td>Provider never confirmed the session. Use <strong>replay</strong> if the payer confirms they sent money.</td></tr>
        </tbody>
      </table>
      <h3>Replay</h3>
      <p>
        Re-runs the reconciliation pipeline for a previously received event. <strong>Only useful when a payment is
        still unresolved</strong> — status is RECEIVED or FAILED — and has been sitting without confirmation for
        a while. The payer should confirm they actually sent the money before you replay.
      </p>
      <p>
        Do <strong>not</strong> replay events that are already PROCESSED, IGNORED, or REATTRIBUTED — replaying a
        terminal event is a no-op. Do not use replay as a general retry mechanism: if the provider never received the
        transfer in the first place, replaying won&apos;t create one.
      </p>
      <h3>Reattribute</h3>
      <p>
        Manually assigns an orphaned payment to one of your customers. Only works on events with
        <code>failureReason</code> of UNKNOWN_VIRTUAL_ACCOUNT or INACTIVE_CUSTOMER. You must specify the
        <code>customerReference</code> of the customer who should receive the payment — that customer must be
        ACTIVE. Cyrus then creates a transaction and runs the normal reconciliation pipeline.
      </p>
    </div>
  );
}

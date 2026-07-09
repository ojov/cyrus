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
        Amounts are always integer kobo (₦50,000.00 = 5,000,000 kobo).
      </p>
      <Code>{`{
  "event": "payment.succeeded",
  "data": {
    "transactionId": "9b323304-…",
    "amountKobo": 15000,
    "feeKobo": 1000,
    "currency": "NGN",
    "status": "SUCCESSFUL",
    "matchStatus": "MATCHED",
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
    </div>
  );
}

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
        <span className="db">Nomba webhook → Cyrus</span><span>→</span>
        <span className="db">verify + dedupe</span><span>→</span>
        <span className="db db-info">attribute to customer</span><span>→</span>
        <span className="db db-good">payment.received</span>
      </div>
      <p>
        Cyrus verifies the Nomba signature, discards duplicates, matches the credited account number to a customer, and
        emits a single normalized event to your webhook. Amounts are always integer kobo (₦50,000.00 = 5,000,000 kobo).
      </p>
      <Code>{`{
  "event": "payment.received",
  "customerId": "cus_01HZY8",
  "externalCustomerId": "user_123",
  "amount": 5000000,
  "currency": "NGN",
  "status": "SUCCESS"
}`}</Code>
      <p>
        A transfer to an account number Cyrus does not recognize is never dropped — it is recorded and surfaced as a
        misdirected payment for your ops team to resolve.
      </p>
    </div>
  );
}

import { TwoCol } from "@/components/docs/two-col";
import { Example } from "@/components/docs/example";
import { Endpoint } from "@/components/docs/endpoint";
import { Code } from "@/components/ui/code-block";
import { IconArrowRight } from "@/components/icons";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function TransactionsReferencePage() {
  return (
    <TwoCol
      aside={
        <Example
          method="GET"
          path="/v1/transactions?matchStatus=DISCREPANCY"
          response={`{
  "data": {
    "content": [
      { "reference": "ref_a1b2", "type": "CUSTOMER_PAYMENT", "customerReference": "user_123",
        "payer": "John Bello", "status": "SUCCESSFUL", "matchStatus": "DISCREPANCY", "amountKobo": 5000000 }
    ],
    "totalElements": 1
  }
}`}
        />
      }
    >
      <h2>API Reference · Transactions</h2>
      <p className="lede">
        Every inbound transfer or outbound payout is a transaction. Query them either merchant-wide across every
        customer, or scoped to one customer via their statement.
      </p>

      <Endpoint method="GET" path="/v1/transactions" tag={<span className="db db-good dot">merchant-wide</span>} />
      <p>
        Paginated, newest-first, across every customer. Optional filters: <code>customerReference</code>,{" "}
        <code>type</code> (<code>CUSTOMER_PAYMENT</code> / <code>PAYOUT</code> / <code>REVERSAL</code> /{" "}
        <code>ADJUSTMENT</code>), <code>status</code>, <code>matchStatus</code>, and a date range (
        <code>from</code>/<code>to</code>, ISO-8601 instants).
      </p>
      <Code>{`{
  "data": {
    "content": [
      {
        "reference": "ref_a1b2",
        "type": "CUSTOMER_PAYMENT",
        "customerReference": "user_123",
        "date": "2026-07-02T14:20:00Z",
        "payer": "John Bello",
        "providerTransactionId": "nmb_88a1",
        "status": "SUCCESSFUL",
        "matchStatus": "MATCHED",
        "amountKobo": 5000000,
        "feeKobo": 75000
      }
    ],
    "totalElements": 42
  }
}`}</Code>

      <Endpoint method="GET" path="/v1/transactions/{reference}" />
      <p>Fetch a single transaction by your reference. Not found and not-yours both return an identical 404.</p>

      <Endpoint method="GET" path="/v1/customers/{reference}/statement" tag={<span className="db">per-customer</span>} />
      <p>
        Scoped to one customer: identity, a reporting summary (lifetime received volume, transaction/pending counts,
        manual-review/discrepancy counts, last transaction date — always over that customer&apos;s full history),
        and the same paginated transaction list, filterable the same way (minus <code>type</code>/
        <code>customerReference</code>, which don&apos;t apply to a single customer).
      </p>
      <Code>{`{
  "data": {
    "customer": { "reference": "user_123", "firstName": "Amara", "status": "ACTIVE" },
    "summary": {
      "lifetimeKobo": 41850000,
      "transactionCount": 12,
      "pendingCount": 1,
      "pendingKobo": 500000,
      "manualReviewCount": 0,
      "discrepancyCount": 1,
      "lastTransactionAt": "2026-07-02T14:20:00Z"
    },
    "transactions": { "content": [ /* same shape as /v1/transactions */ ], "totalElements": 12 }
  }
}`}</Code>

      <p>
        <code>amountKobo</code> is the gross amount the payer sent (or, for a <code>PAYOUT</code>, the amount sent
        out). <code>matchStatus</code> is the reconciliation verdict:{" "}
        <span className="db db-good">MATCHED</span> <span className="db db-warn">DISCREPANCY</span>{" "}
        <span className="db db-warn">UNMATCHED</span> <span className="db db-crit">MANUAL_REVIEW</span>. The
        <code>summary</code> block on the statement endpoint is always over the customer&apos;s full history,
        independent of whatever <code>from</code>/<code>to</code>/<code>matchStatus</code> filter narrows the list
        alongside it.
      </p>
      <div className="callout">
        <b>Full field-by-field reference.</b> This page covers the shape you&apos;ll use day to day — for every
        field, status enum, and error case, see the{" "}
        <a href={`${API_URL}/docs`} target="_blank" rel="noreferrer" className="text-primary underline underline-offset-2">
          live, generated API reference
        </a>{" "}
        <IconArrowRight className="inline size-3.5" />
      </div>
    </TwoCol>
  );
}

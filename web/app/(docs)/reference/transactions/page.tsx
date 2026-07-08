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
          path="/v1/customers/user_123/statement"
          response={`{
  "data": {
    "customer": { "reference": "user_123", "status": "ACTIVE" },
    "lifetimeKobo": 41850000,
    "transactions": {
      "content": [
        { "payer": "John Bello", "matchStatus": "MATCHED", "amountKobo": 5000000 }
      ]
    }
  }
}`}
        />
      }
    >
      <h2>API Reference · Transactions</h2>
      <p className="lede">
        Every inbound transfer is a transaction on the customer it was attributed to. Query them via the
        customer&apos;s statement — a global cross-customer list isn&apos;t exposed today.
      </p>
      <Endpoint method="GET" path="/v1/customers/{reference}/statement" />
      <Code>{`{
  "data": {
    "customer": { "reference": "user_123", "firstName": "Amara", "status": "ACTIVE" },
    "lifetimeKobo": 41850000,
    "transactions": {
      "content": [
        {
          "date": "2026-07-02T14:20:00Z",
          "payer": "John Bello",
          "ref": "API-VACT_TRA-…",
          "matchStatus": "MATCHED",
          "amountKobo": 5000000
        }
      ],
      "totalElements": 3
    }
  }
}`}</Code>
      <p>
        <code>amountKobo</code> is the gross amount the payer sent. <code>matchStatus</code> is the reconciliation
        verdict: <span className="db db-good">MATCHED</span> <span className="db db-warn">DISCREPANCY</span>{" "}
        <span className="db db-warn">UNMATCHED</span> <span className="db db-crit">MANUAL_REVIEW</span>.
        <code>lifetimeKobo</code> sums only <code>SUCCESSFUL</code> transactions. The list is paginated
        (standard Spring <code>Pageable</code> — <code>page</code>/<code>size</code> query params).
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

import { TwoCol } from "@/components/docs/two-col";
import { TryIt } from "@/components/docs/try-it";
import { Endpoint } from "@/components/docs/endpoint";
import { Code } from "@/components/ui/code-block";

export default function TransactionsReferencePage() {
  return (
    <TwoCol
      aside={
        <TryIt
          method="GET"
          path="/v1/customers/user_123/transactions"
          response={`{
  "transactions": [
    { "amount": 5000000, "payerName": "John Bello", "matchStatus": "MATCHED" }
  ],
  "nextCursor": null
}`}
        />
      }
    >
      <h2>API Reference · Transactions</h2>
      <p className="lede">
        Query the ledger of inbound transfers, globally or per customer. Every amount is integer kobo; convert to naira only
        for display.
      </p>
      <Endpoint method="GET" path="/v1/transactions?status=&cursor=" />
      <Endpoint method="GET" path="/v1/customers/{id}/transactions" tag={<span className="db">statement</span>} />
      <Code>{`{
  "transactions": [
    {
      "id": "txn_01J…",
      "amount": 5000000,
      "payerName": "John Bello",
      "status": "SUCCESSFUL",
      "matchStatus": "MATCHED",
      "receivedAt": "2026-07-02T14:20:00Z"
    }
  ],
  "nextCursor": null
}`}</Code>
      <p>
        <code>matchStatus</code> is the reconciliation verdict: <span className="db db-good">MATCHED</span>{" "}
        <span className="db db-warn">PARTIAL</span> <span className="db db-warn">UNMATCHED</span>{" "}
        <span className="db db-crit">ORPHANED</span>. Lists are cursor-paginated.
      </p>
    </TwoCol>
  );
}

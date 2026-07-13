import Link from "next/link";

export default function ChangelogPage() {
  return (
    <div className="doc-prose">
      <h2>Changelog</h2>
      <p className="lede">Notable changes to the Cyrus API. Breaking changes are announced here first.</p>
      <table className="doctable">
        <thead>
          <tr>
            <th>Date</th>
            <th>Change</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td className="font-mono text-muted-foreground">2026-07-13</td>
            <td>
              Python SDK <code>0.1.2</code> — fixed a packaging bug where <code>pytest</code>/<code>pytest-asyncio</code> were
              installed as unconditional runtime dependencies. Run <code>pip install --upgrade cyrus-payments</code> to pick it up.
            </td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-07-13</td>
            <td>
              Added an official Python SDK — <code>pip install cyrus-payments</code>, covering customers,
              transactions, and payment events. See the{" "}
              <Link href="/sdks" className="text-primary underline underline-offset-2">SDKs</Link> page.
            </td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-07-07</td>
            <td>Added misdirected-payment recovery — orphan events surfaced under <code>GET /v1/payment-events</code> with replay/reattribute endpoints.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-30</td>
            <td>Merchant-wide transactions (<code>GET /v1/transactions</code>), per-customer statements (<code>GET /v1/customers/{"{reference}"}/statement</code>), and reconciliation verdicts (<code>MATCHED</code>, <code>DISCREPANCY</code>, <code>MANUAL_REVIEW</code>).</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

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
            <td className="font-mono text-muted-foreground">2026-07-05</td>
            <td>Added merchant-wide transactions endpoint (<code>GET /v1/transactions</code>) and per-customer statement (<code>GET /v1/customers/{"{reference}"}/statement</code>).</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-28</td>
            <td>Added misdirected-payment recovery — orphan events surfaced under <code>GET /v1/payment-events</code> with replay/reattribute endpoints.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-20</td>
            <td>Normalized inbound payments into <code>payment.succeeded</code>, <code>payment.reversed</code>, and <code>payment.flagged</code> webhook events.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-10</td>
            <td>Reconciliation verdicts: <code>MATCHED</code>, <code>DISCREPANCY</code>, <code>UNMATCHED</code>, <code>ORPHANED</code>, <code>MANUAL_REVIEW</code>.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-01</td>
            <td>Public beta — customer and virtual-account provisioning with wallet and payouts.</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

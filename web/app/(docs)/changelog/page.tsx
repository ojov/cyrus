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
            <td className="font-mono text-muted-foreground">2026-07-01</td>
            <td>Added per-customer statements (<code>GET /v1/customers/{"{id}"}/transactions</code>).</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-20</td>
            <td>Normalized inbound payments into a single <code>payment.received</code> event.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-12</td>
            <td>Reconciliation now flags <code>ORPHANED</code> and <code>PARTIAL</code> transfers.</td>
          </tr>
          <tr>
            <td className="font-mono text-muted-foreground">2026-06-01</td>
            <td>Public beta — customer and virtual-account provisioning.</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

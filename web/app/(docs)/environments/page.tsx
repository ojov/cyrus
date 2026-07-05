export default function EnvironmentsPage() {
  return (
    <div className="doc-prose">
      <h2>Environments</h2>
      <p className="lede">
        Test and live are fully isolated. The important detail: only your Nomba credentials differ between them — your
        parent account and sub-account IDs are shared.
      </p>
      <table className="doctable">
        <thead>
          <tr>
            <th>Environment</th>
            <th>Key prefix</th>
            <th>Nomba credentials</th>
            <th>Money is</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><span className="db db-info">TEST</span></td>
            <td className="font-mono">cyrus_test_</td>
            <td>Sandbox client ID / secret</td>
            <td>Simulated</td>
          </tr>
          <tr>
            <td><span className="db db-good">LIVE</span></td>
            <td className="font-mono">cyrus_live_</td>
            <td>Live client ID / secret</td>
            <td>Real</td>
          </tr>
        </tbody>
      </table>
      <h3>Switching to live</h3>
      <p>
        You do not re-register. In the dashboard, open Settings → Go live and paste your live client ID and secret. Cyrus
        verifies them against Nomba and unlocks <code>cyrus_live_</code> key generation. Then open Dashboard → API keys,
        generate a live key, and copy it immediately. Because the account IDs are reused, your customers and their account
        numbers carry straight over.
      </p>
      <div className="callout">
        <b>Sandbox limit.</b> Nomba sandbox caps you at 2 virtual accounts per account holder. Live has no such cap.
      </div>
    </div>
  );
}

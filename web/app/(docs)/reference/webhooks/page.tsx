import { Code } from "@/components/ui/code-block";

export default function WebhooksReferencePage() {
  return (
    <div className="doc-prose">
      <h2>API Reference · Webhooks</h2>
      <p className="lede">
        Register one endpoint and receive every payment and payout outcome as a clean, signed event. You integrate
        once and never touch the underlying provider&apos;s payload format.
      </p>
      <h3>Events</h3>
      <table className="doctable">
        <thead>
          <tr>
            <th>Event</th>
            <th>Fires when</th>
          </tr>
        </thead>
        <tbody>
          <tr><td className="font-mono">payment.succeeded</td><td>Reconciliation confirms an inbound transfer with the provider.</td></tr>
          <tr><td className="font-mono">payment.reversed</td><td>A previously confirmed payment was clawed back.</td></tr>
          <tr><td className="font-mono">payment.flagged</td><td>Reconciliation couldn&apos;t confirm the payment after retrying — needs manual review.</td></tr>
          <tr><td className="font-mono">payout.completed</td><td>A payout you initiated settled successfully.</td></tr>
          <tr><td className="font-mono">payout.failed</td><td>A payout was rejected by the provider; your wallet was refunded.</td></tr>
        </tbody>
      </table>
      <h3>Configure</h3>
      <p>
        Set your endpoint URL and copy your signing secret (<code>whsec_…</code>, shown once) in Dashboard → Settings.
      </p>
      <h3>Verify the signature</h3>
      <p>
        Every delivery carries <code>X-Cyrus-Signature</code>, <code>X-Cyrus-Timestamp</code>, and{" "}
        <code>X-Cyrus-Event</code>. The signature covers <code>{"timestamp + \".\" + payload"}</code> — not the
        payload alone — so the timestamp is part of what&apos;s actually signed, not just a sibling header.
      </p>
      <Code>{`// pseudo-code
signedContent = timestamp + "." + rawBody
expected = "sha256=" + hex(hmac_sha256(secret, signedContent))
assert expected === headers["X-Cyrus-Signature"]`}</Code>
      <h3>Delivery guarantees</h3>
      <ul>
        <li><b>Idempotent</b> — each delivery carries a stable <code>X-Cyrus-Delivery</code> id; safe to receive more than once.</li>
        <li><b>Retried</b> — non-2xx responses are retried with exponential backoff.</li>
        <li>Respond <code>2xx</code> once you have stored the event; do slow work asynchronously.</li>
      </ul>
      <div className="callout">Test deliveries any time from Webhook Testing — no real transfer needed.</div>
    </div>
  );
}

import { Code } from "@/components/ui/code-block";

export default function WebhooksReferencePage() {
  return (
    <div className="doc-prose">
      <h2>API Reference · Webhooks</h2>
      <p className="lede">
        Register one endpoint and receive every payment as a clean, signed event. You integrate once and never touch the
        Nomba payload format.
      </p>
      <h3>Configure</h3>
      <p>
        Set your endpoint URL and copy your signing secret in Dashboard → Settings. Cyrus signs each delivery with
        HMAC-SHA256 over the raw body.
      </p>
      <h3>Verify the signature</h3>
      <Code>{`// pseudo-code
expected = base64( hmac_sha256(secret, rawBody) )
assert expected === headers["cyrus-signature"]`}</Code>
      <h3>Delivery guarantees</h3>
      <ul>
        <li><b>Idempotent</b> — every event has a stable <code>id</code>; safe to receive more than once.</li>
        <li><b>Retried</b> — non-2xx responses are retried with backoff.</li>
        <li>Respond <code>2xx</code> once you have stored the event; do slow work asynchronously.</li>
      </ul>
      <div className="callout">Test deliveries any time from Webhook Testing — no real transfer needed.</div>
    </div>
  );
}

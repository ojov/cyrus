import { Code } from "@/components/ui/code-block";

export default function WebhookTestingPage() {
  return (
    <div className="doc-prose">
      <h2>Webhook Testing</h2>
      <p className="lede">Test your webhook endpoint before processing real transactions.</p>
      <h3>1 · Tunnel to localhost</h3>
      <p>While developing, expose your local server with a tunnel and set your webhook URL:</p>
      <Code>{`ngrok http 8080
# → https://xxxx.ngrok-free.app  → set as your webhook URL`}</Code>
      <h3>2 · Sign a real transaction</h3>
      <p>
        Trigger a VA credit through the sandbox. When Cyrus processes it and reconciliation confirms
        the transaction, a signed <code>payment.succeeded</code> webhook is delivered to your URL.
      </p>
      <h3>3 · Mock Nomba inbound webhooks</h3>
      <p>
        To simulate Nomba-to-Cyrus webhooks during development, use the helper script:
      </p>
      <Code>{`./scripts/mock-nomba-webhook.sh \\
  --account 1230751405 \\
  --amount 2500 \\
  --secret $NOMBA_WEBHOOK_SECRET`}</Code>
      <p>This tests the full ingestion pipeline without sending real money.</p>
      <h3>What to expect</h3>
      <p>Cyrus sends signed POST requests to your webhook URL.</p>
      <h4>Request headers</h4>
      <ul>
        <li><code>X-Cyrus-Event</code> — event type, one of <code>payment.succeeded</code>, <code>payment.reversed</code>, <code>payment.flagged</code>, <code>payout.completed</code>, <code>payout.failed</code></li>
        <li><code>X-Cyrus-Delivery</code> — unique UUID for this delivery attempt</li>
        <li><code>X-Cyrus-Timestamp</code> — epoch milliseconds of the delivery</li>
        <li><code>X-Cyrus-Signature</code> — <code>sha256=</code> followed by the HMAC-SHA256 hex digest of <code>{'{timestamp}.{payload}'}</code>, where <code>{'{timestamp}'}</code> is the value of <code>X-Cyrus-Timestamp</code> and <code>{'{payload}'}</code> is the raw JSON body</li>
      </ul>
      <h4>Payload</h4>
      <Code>{`{
  "event": "payment.succeeded",
  "createdAt": "2026-07-09T12:00:00Z",
  "data": {
    "transactionId": "uuid",
    "amountKobo": 250000,
    "feeKobo": 3750,
    "currency": "NGN",
    "status": "SUCCESSFUL",
    "matchStatus": "MATCHED",
    "sessionId": "1000042602061021531516xxxx",
    "providerTransactionId": "API-VACT_TRA-613BB-...",
    "customerReference": "cust_abc123",
    "virtualAccountNumber": "1230751405",
    "paidAt": "2026-07-09T11:55:00Z"
  }
}`}</Code>
      <h4>Response &amp; retries</h4>
      <ul>
        <li>Return <code>2xx</code> to acknowledge — Cyrus stops retrying.</li>
        <li><code>5xx</code> and <code>429</code> trigger retries with exponential backoff (up to the configured max attempts).</li>
        <li>Other <code>4xx</code> (400, 401, 403, 404, 410) are treated as permanent failures — the webhook is marked failed immediately.</li>
        <li>Connection timeouts, DNS failures, and network errors are also retried.</li>
      </ul>
      <h4>Deduplication</h4>
      <p>
        Cyrus sends each event type exactly once per transaction. If a delivery fails and is retried,
        it carries the same <code>transactionId</code> and <code>event</code> type. Dedupe on those
        two fields rather than the delivery ID.
      </p>
      <h4>Verifying signatures</h4>
      <p>Construct the signing string as <code>{'{X-Cyrus-Timestamp}.{raw body}'}</code>, compute HMAC-SHA256 with your webhook secret, and compare as hex:</p>
      <Code>{`const crypto = require("crypto");
const expected = "sha256=" + crypto
  .createHmac("sha256", secret)
  .update(timestamp + "." + body)
  .digest("hex");
if (expected !== signature) throw new Error("Invalid signature");`}</Code>
      <p>To avoid timing attacks, use a constant-time comparison. The timestamp also lets you enforce a replay window (e.g. reject signatures older than 5 minutes).</p>
    </div>
  );
}

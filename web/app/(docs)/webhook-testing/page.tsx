import { Code } from "@/components/ui/code-block";

export default function WebhookTestingPage() {
  return (
    <div className="doc-prose">
      <h2>Webhook Testing</h2>
      <p className="lede">Two ways to exercise your webhook before real money moves.</p>
      <h3>1 · Send a test event from the dashboard</h3>
      <p>
        Open Settings → Webhooks and hit Send test event. Cyrus posts a signed <code>payment.received</code> to your URL so
        you can confirm your signature check and 2xx response.
      </p>
      <h3>2 · Tunnel to localhost</h3>
      <p>While developing, expose your local server with a tunnel and point your webhook URL at it:</p>
      <Code>{`ngrok http 8080
# → https://xxxx.ngrok-free.app  → set as your webhook URL`}</Code>
      <h3>What to expect</h3>
      <ul>
        <li>Header <code>cyrus-signature</code> = base64 HMAC-SHA256 of the raw body.</li>
        <li>Return <code>2xx</code> to acknowledge; anything else is retried with backoff.</li>
        <li>Re-deliveries share the same event <code>id</code> — dedupe on it.</li>
      </ul>
    </div>
  );
}

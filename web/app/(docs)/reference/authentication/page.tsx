import { Code } from "@/components/ui/code-block";

export default function AuthenticationReferencePage() {
  return (
    <div className="doc-prose">
      <h2>API Reference · Authentication</h2>
      <p className="lede">
        Every request to the Cyrus API is authenticated with a single API key. Generate it in Dashboard → API keys and
        copy the full value when it appears — there is no separate API login and no TEST/LIVE split.
      </p>
      <h3>Authorize a request</h3>
      <p>Send your key as a bearer token.</p>
      <Code>{`Authorization: Bearer cyrus_9f2a…`}</Code>
      <h3>Keep keys safe</h3>
      <ul>
        <li>Use keys only from your backend — never in browser or mobile code.</li>
        <li>Create, copy immediately, and revoke keys in the dashboard. Full keys are revealed once; a revoked key is rejected with <code>401</code>.</li>
        <li>The ops dashboard login (email and password) is separate and governs only the dashboard.</li>
      </ul>
    </div>
  );
}

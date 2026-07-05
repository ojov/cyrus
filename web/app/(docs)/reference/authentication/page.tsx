import { TwoCol } from "@/components/docs/two-col";
import { TryIt } from "@/components/docs/try-it";
import { Code } from "@/components/ui/code-block";

export default function AuthenticationReferencePage() {
  return (
    <TwoCol
      aside={
        <TryIt
          method="GET"
          path="/v1/customers/user_123"
          response={`{
  "customerId": "cus_01HZY8",
  "externalCustomerId": "user_123",
  "status": "ACTIVE"
}`}
        />
      }
    >
      <h2>API Reference · Authentication</h2>
      <p className="lede">
        Every request to the Cyrus API is authenticated with an API key. Your keys appear in Dashboard → API keys as soon as
        your account exists — there is no separate API login.
      </p>
      <h3>Authorize a request</h3>
      <p>
        Send your key as a bearer token. The prefix — <code>cyrus_test_</code> or <code>cyrus_live_</code> — selects the
        environment.
      </p>
      <Code>{`Authorization: Bearer cyrus_test_…`}</Code>
      <h3>Keep keys safe</h3>
      <ul>
        <li>Use keys only from your backend — never in browser or mobile code.</li>
        <li>Create, reveal-once, and revoke keys in the dashboard. A revoked key is rejected with <code>401</code>.</li>
        <li>The ops dashboard login (email and password) is separate and governs only the dashboard.</li>
      </ul>
    </TwoCol>
  );
}

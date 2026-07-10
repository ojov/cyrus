import { Code } from "@/components/ui/code-block";

export default function ErrorsReferencePage() {
  return (
    <div className="doc-prose">
      <h2>API Reference · Errors</h2>
      <p className="lede">Every response — success or failure — uses the same envelope, so your client parses one shape.</p>
      <Code>{`{
  "status": false,
  "code": "DUPLICATE_MERCHANT",
  "message": "A merchant with this email already exists.",
  "data": null,
  "timestamp": "2026-07-02T14:20:00Z"
}`}</Code>
      <table className="doctable">
        <thead>
          <tr>
            <th>HTTP</th>
            <th>code</th>
            <th>data carries</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>400</td><td className="font-mono">INVALID_INPUT</td><td><code>fieldErrors</code></td></tr>
          <tr><td>400</td><td className="font-mono">INVALID_REQUEST</td><td>—</td></tr>
          <tr><td>400</td><td className="font-mono">INVALID_TOKEN</td><td>—</td></tr>
          <tr><td>401</td><td className="font-mono">UNAUTHORIZED</td><td>—</td></tr>
          <tr><td>401</td><td className="font-mono">INVALID_TOKEN</td><td>—</td></tr>
          <tr><td>403</td><td className="font-mono">ACCOUNT_NOT_VERIFIED</td><td>—</td></tr>
          <tr><td>403</td><td className="font-mono">UNAUTHORIZED</td><td>—</td></tr>
          <tr><td>404</td><td className="font-mono">RESOURCE_NOT_FOUND</td><td>—</td></tr>
          <tr><td>409</td><td className="font-mono">DUPLICATE_MERCHANT</td><td>—</td></tr>
          <tr><td>409</td><td className="font-mono">INVALID_REQUEST</td><td>—</td></tr>
          <tr><td>422</td><td className="font-mono">INVALID_REQUEST</td><td>—</td></tr>
          <tr><td>502</td><td className="font-mono">NOMBA_INTEGRATION_ERROR</td><td><code>traceId</code></td></tr>
          <tr><td>502</td><td className="font-mono">EMAIL_DELIVERY_ERROR</td><td><code>traceId</code></td></tr>
          <tr><td>500</td><td className="font-mono">INTERNAL_ERROR</td><td><code>traceId</code></td></tr>
        </tbody>
      </table>
      <p>
        Validation errors return per-field detail; server and upstream errors return a <code>traceId</code> you can quote to
        support. Stack traces and raw provider payloads are never leaked.
      </p>
    </div>
  );
}

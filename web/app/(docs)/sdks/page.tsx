import { Code } from "@/components/ui/code-block";

export default function SdksPage() {
  return (
    <div className="doc-prose">
      <h2>SDKs</h2>
      <p className="lede">
        An official Python SDK is available now. Other languages are on the roadmap — the REST API is stable and
        simple to call directly in the meantime.
      </p>

      <h3>Python</h3>
      <p>
        <code>cyrus-payments</code> on PyPI covers the developer (API-key) surface — customers, transactions, and
        payment events.
      </p>
      <Code>{`pip install cyrus-payments`}</Code>
      <Code>{`from cyrus import CyrusClient

client = CyrusClient(api_key="cyrus_live_xxx")
customer = client.customers.create(reference="cust_123", first_name="Ada")`}</Code>
      <div className="callout">
        <a
          href="https://pypi.org/project/cyrus-payments/"
          target="_blank"
          rel="noreferrer"
          className="text-primary underline underline-offset-2"
        >
          View on PyPI
        </a>{" "}
        for the full README — installation, pagination, error handling, and the enum reference.
      </div>

      <table className="doctable">
        <thead>
          <tr>
            <th>Language</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>Python</td><td><span className="db db-good dot">Available</span></td></tr>
          <tr><td>Java / Spring</td><td><span className="db db-warn dot">Planned</span></td></tr>
          <tr><td>Node / TypeScript</td><td><span className="db db-warn dot">Planned</span></td></tr>
          <tr><td>PHP · Go</td><td><span className="db dot">Exploring</span></td></tr>
        </tbody>
      </table>
      <div className="callout">
        Want a specific SDK next? Reach us at <b>hello@trycyrus.app</b> and we will prioritize.
      </div>
    </div>
  );
}

import { TwoCol } from "@/components/docs/two-col";
import { TryIt } from "@/components/docs/try-it";
import { Endpoint } from "@/components/docs/endpoint";

export default function VirtualAccountsReferencePage() {
  return (
    <TwoCol
      aside={
        <TryIt
          method="POST"
          path="/v1/customers"
          body={`{
  "reference": "user_123",
  "firstName": "Amara",
  "lastName": "Okafor"
}`}
          response={`{
  "reference": "user_123",
  "virtualAccount": {
    "accountNumber": "0123456789",
    "bankName": "Nombank MFB",
    "status": "ACTIVE"
  }
}`}
        />
      }
    >
      <h2>API Reference · Virtual Accounts</h2>
      <p className="lede">
        A virtual account is a payment instrument attached to a customer identity. It is provisioned automatically — you
        never create one directly.
      </p>
      <Endpoint method="POST" path="/v1/customers" tag={<span className="db db-good dot">auto-provisions a VA</span>} />
      <Endpoint method="GET" path="/v1/customers/{reference}" />
      <p>Returns the customer and its virtual account, keyed by <code>reference</code> — your own identifier for the customer.</p>
      <table className="doctable">
        <thead>
          <tr>
            <th>Field</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr><td className="font-mono">accountNumber</td><td>Permanent NUBAN — the key every payment is attributed by.</td></tr>
          <tr><td className="font-mono">accountName</td><td>Display name; updates if you rename the customer.</td></tr>
          <tr><td className="font-mono">bankName</td><td>e.g. Nombank MFB.</td></tr>
          <tr>
            <td className="font-mono">status</td>
            <td><span className="db db-good">ACTIVE</span> <span className="db db-warn">SUSPENDED</span> <span className="db">CLOSED</span></td>
          </tr>
        </tbody>
      </table>
      <Endpoint method="PATCH" path="/v1/customers/{reference}/status" />
      <p>
        Set <code>status</code> to <code>SUSPENDED</code> to stop accepting credits or <code>CLOSED</code> to retire the
        account for good. Identity and transaction history are always retained; <code>CLOSED</code> is terminal.
      </p>
    </TwoCol>
  );
}

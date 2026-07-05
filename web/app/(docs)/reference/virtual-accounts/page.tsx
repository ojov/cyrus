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
          respCode="201"
          respText="Created"
          body={`{
  "externalCustomerId": "user_123",
  "firstName": "Amara",
  "lastName": "Okafor"
}`}
          response={`{
  "customerId": "cus_01HZY8",
  "virtualAccount": {
    "accountNumber": "0123456789",
    "bankName": "Nomba MFB",
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
      <Endpoint method="GET" path="/v1/customers/{externalCustomerId}" />
      <p>Returns the customer and its virtual account.</p>
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
          <tr><td className="font-mono">bankName</td><td>e.g. Nomba MFB.</td></tr>
          <tr>
            <td className="font-mono">status</td>
            <td><span className="db db-good">ACTIVE</span> <span className="db db-warn">SUSPENDED</span> <span className="db">CLOSED</span></td>
          </tr>
        </tbody>
      </table>
      <Endpoint method="POST" path="/v1/customers/{id}/suspend · /close" tag={<span className="db">proposed</span>} />
      <p>Suspend to stop accepting credits; close to retire the account. Identity and history are always retained.</p>
    </TwoCol>
  );
}

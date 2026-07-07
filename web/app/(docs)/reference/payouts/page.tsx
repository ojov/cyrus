import { TwoCol } from "@/components/docs/two-col";
import { TryIt } from "@/components/docs/try-it";
import { Code } from "@/components/ui/code-block";

export default function PayoutsReferencePage() {
  return (
    <TwoCol
      aside={
        <TryIt
          method="POST"
          path="/v1/merchants/me/payouts"
          body={`{
  "beneficiaryId": "3f9c…",
  "amount": 500000,
  "narration": "Weekly settlement"
}`}
          response={`{
  "reference": "pyt_a1b2c3…",
  "status": "PENDING",
  "amount": 500000
}`}
        />
      }
    >
      <h2>API Reference · Payouts</h2>
      <p className="lede">
        Withdraw your Cyrus wallet balance to a bank account. Payouts settle via Nomba and are tracked the same way
        inbound payments are — with a stable reference and a status you can poll or receive as a webhook.
      </p>
      <h3>Register a beneficiary first</h3>
      <p>
        A beneficiary is the destination bank account. Cyrus verifies the account name against Nomba when you add one, so
        you always see the real account holder before sending money.
      </p>
      <Code>{`POST /v1/merchants/me/beneficiaries
{
  "nickname": "Main GTBank",
  "accountNumber": "0123456789",
  "bankCode": "058",
  "bankName": "GTBank"
}`}</Code>
      <h3>Initiate a payout</h3>
      <p>
        Amounts are integer kobo. Cyrus debits your wallet up front — a payout can never overdraw your balance — and
        refunds it automatically if Nomba rejects the transfer.
      </p>
      <h3>Statuses</h3>
      <ul>
        <li><b>PENDING</b> — reserved and submitted to Nomba.</li>
        <li><b>PROCESSING</b> — accepted by Nomba, settling.</li>
        <li><b>SUCCESS</b> — settled to the beneficiary.</li>
        <li><b>FAILED</b> — rejected by Nomba; your wallet was refunded.</li>
      </ul>
      <div className="callout">
        A failed payout is refunded to your wallet automatically — you never need to manually reconcile a rejected transfer.
      </div>
    </TwoCol>
  );
}

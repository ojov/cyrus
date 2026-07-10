import { TwoCol } from "@/components/docs/two-col";

export default function PayoutsReferencePage() {
  return (
    <TwoCol
      aside={
        <div className="self-start overflow-hidden rounded-xl border border-border bg-card lg:sticky lg:top-6">
          <div className="flex items-center gap-2 border-b border-border bg-muted px-3.5 py-2.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
            Dashboard only
          </div>
          <div className="p-3.5 text-sm text-muted-foreground">
            Payouts are initiated from the <strong>Dashboard</strong> — there is no API-key-authenticated
            payout endpoint. Beneficiaries and payouts are managed under <code className="font-mono text-xs">/ops/payouts</code>.
          </div>
        </div>
      }
    >
      <h2>Payouts</h2>
      <p className="lede">
        Withdraw your Cyrus wallet balance to a bank account. Payouts are managed exclusively through the
        dashboard — your operations team sends money, not your application code. They settle via our connected
        banking provider and are tracked the same way inbound payments are: with a stable reference and a
        status you can monitor or receive as a webhook.
      </p>
      <div className="callout">
        Payouts are a <strong>dashboard-only</strong> operation. Your server-to-server API key cannot
        initiate payouts. This is intentional — the destination account number and bank code are exactly
        what a real bank transfer is keyed by, and we want that controlled through the ops dashboard,
        not a raw API call.
      </div>
      <h3>Register a beneficiary first</h3>
      <p>
        Before you can send a payout, add a beneficiary from the <strong>Beneficiaries</strong> page in
        the dashboard. You pick a bank from a dropdown (the exact list our provider recognizes — no risk of
        a mistyped bank code), enter the account number, and Cyrus verifies the account name with the
        provider. The verified name is displayed so you always know whose account you are sending to.
      </p>
      <h3>Initiate a payout</h3>
      <p>
        From the <strong>Payouts</strong> page, pick a beneficiary, enter the amount in naira, and
        optionally add a narration. Cyrus debits your wallet up front — a payout can never overdraw
        your balance — and refunds it automatically if the provider rejects the transfer.
      </p>
      <h3>Statuses</h3>
      <ul>
        <li><b>PENDING</b> — reserved and submitted to the provider.</li>
        <li><b>PROCESSING</b> — accepted by the provider, settling.</li>
        <li><b>SUCCESS</b> — settled to the beneficiary.</li>
        <li><b>FAILED</b> — rejected by the provider; your wallet was refunded.</li>
      </ul>
      <div className="callout">
        A failed payout is refunded to your wallet automatically — you never need to manually reconcile a rejected transfer.
      </div>
    </TwoCol>
  );
}

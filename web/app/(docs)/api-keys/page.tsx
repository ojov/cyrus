import Link from "next/link";
import { IconArrowRight } from "@/components/icons";

export default function ApiKeysDocsPage() {
  return (
    <div className="doc-prose">
      <h2>API Keys</h2>
      <p className="lede">
        Keys authenticate your backend to the Cyrus API. Generate and manage them from the dashboard; this page explains
        how they work.
      </p>
      <ul>
        <li>
          <b>One key.</b> There is no TEST/LIVE split — Cyrus runs on its own Nomba account, so a single key authenticates
          every request.
        </li>
        <li>
          <b>Generate it yourself.</b> Signup does not give you a reusable visible key. Open Dashboard → API keys,
          generate one, and copy it immediately.
        </li>
        <li>
          <b>Shown once.</b> The full key is revealed a single time at creation. Existing keys only show their prefix, so
          store the full value in your secret manager immediately.
        </li>
        <li><b>Revocable.</b> Revoke a leaked key instantly; it cannot be un-revoked, so issue a fresh one.</li>
      </ul>
      <div className="mt-4">
        <Link
          href="/login"
          className="inline-flex items-center gap-1.5 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105"
        >
          Manage keys in the dashboard <IconArrowRight className="size-4" />
        </Link>
      </div>
    </div>
  );
}

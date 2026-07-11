/** Join truthy class names. Keeps class lists conditional without a runtime dep. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}

/**
 * Validates a post-login redirect target before it's ever passed to `router.push`. Without this,
 * a `?callbackUrl=` query param is an open-redirect vector: an attacker-crafted link could send a
 * user through a real login and then off to an external phishing page. Requiring the `/ops` prefix
 * (the only auth-gated area) is a strict allowlist — it also rules out protocol-relative URLs
 * (`//evil.com`) and absolute URLs (`https://evil.com`), since neither starts with that string.
 */
export function safeOpsCallback(callbackUrl: string | null): string | null {
  return callbackUrl && callbackUrl.startsWith("/ops") ? callbackUrl : null;
}

/** Format integer kobo (minor units) as a naira string, e.g. 5000000 → "₦50,000.00". */
export function naira(kobo: number): string {
  return "₦" + (kobo / 100).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Map a domain status to a semantic badge modifier class (db-good / db-warn / db-crit / db-info). */
export function statusClass(status: string): string {
  switch (status) {
    case "MATCHED":
    case "ACTIVE":
    case "SUCCESSFUL":
    case "SUCCESS":
    case "PROCESSED":
    case "REATTRIBUTED":
    case "DELIVERED":
      return "db-good";
    case "PARTIAL":
    case "UNMATCHED":
    case "MISSING":
    case "SUSPENDED":
    case "PENDING":
    case "PROCESSING":
    case "RECEIVED":
    case "RETRYING":
    case "INACTIVE_CUSTOMER":
    case "PROVIDER_UNCONFIRMED":
    case "PROVIDER_UNAVAILABLE":
      return "db-warn";
    case "ORPHANED":
    case "REVOKED":
    case "FAILED":
    case "DISCREPANCY":
    case "MANUAL_REVIEW":
    case "IGNORED":
    case "PROCESSED_DUPLICATE":
    case "UNKNOWN_VIRTUAL_ACCOUNT":
    case "NON_CREDIT_EVENT":
    case "DUPLICATE":
    case "SIGNATURE_MISMATCH":
    case "AMOUNT_MISMATCH":
    case "LEDGER_READ_FAILED":
      return "db-crit";
    case "TIER_2":
    case "TIER_3":
      return "db-info";
    default:
      return "";
  }
}

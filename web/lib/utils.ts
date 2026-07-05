/** Join truthy class names. Keeps class lists conditional without a runtime dep. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
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
    case "LIVE":
      return "db-good";
    case "PARTIAL":
    case "UNMATCHED":
    case "MISSING":
    case "SUSPENDED":
      return "db-warn";
    case "ORPHANED":
    case "REVOKED":
    case "FAILED":
      return "db-crit";
    case "LEVEL_2":
    case "TEST":
      return "db-info";
    default:
      return "";
  }
}
